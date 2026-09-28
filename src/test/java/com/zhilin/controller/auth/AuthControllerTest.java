package com.zhilin.controller.auth;

import com.jayway.jsonpath.JsonPath;
import com.zhilin.common.enums.AuthClientEnum;
import com.zhilin.common.enums.ErrorCodeEnum;
import com.zhilin.common.exception.BusinessException;
import com.zhilin.common.util.RedisKeyUtil;
import com.zhilin.config.RedisKeyProperties;
import com.zhilin.dto.auth.AuthSessionDTO;
import com.zhilin.dto.auth.UserRegisterDTO;
import com.zhilin.entity.auth.UserAccountEntity;
import com.zhilin.mapper.auth.UserAccountMapper;
import com.zhilin.security.JwtTokenUtil;
import com.zhilin.service.auth.AuthService;
import com.zhilin.vo.auth.CurrentUserVO;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实 MySQL/Redis 认证闭环；仅清理本测试创建的账号和精确会话键。 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(printOnlyOnFailure = false, print = MockMvcPrint.NONE)
@EnabledIfSystemProperty(named = "infraTests", matches = "true")
class AuthControllerTest {
    /** 仅测试使用的密码，不对应开发或生产账号。 */
    private static final String TEST_PASSWORD = "Test-only-Password-2026";
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserAccountMapper userAccountMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private StringRedisTemplate stringRedisTemplate;
    @Autowired private RedisKeyUtil redisKeyUtil;
    @Autowired private RedisKeyProperties redisKeyProperties;
    @Autowired private JwtTokenUtil jwtTokenUtil;
    @Autowired private AuthService authService;
    /** 当前用例独占的账号，不使用全表清理。 */
    private final List<String> usernameList = new ArrayList<>();
    /** 当前用例获得的会话键，不使用模糊删除。 */
    private final List<String> sessionKeyList = new ArrayList<>();

    /**
     * 在写入前确认连接测试库和专属 Redis 前缀，避免触及开发数据。
     */
    @BeforeEach
    void requireIsolatedInfrastructure() {
        assertEquals("zhilin_test", jdbcTemplate.queryForObject("SELECT DATABASE()", String.class));
        assertEquals("zhilin:test:", redisKeyProperties.getKeyPrefix());
    }

    /**
     * 仅删除当前用例登记的账号和精确会话键，不执行全表或全库清理。
     */
    @AfterEach
    void removeOnlyOwnFixtures() {
        for (String sessionKey : sessionKeyList) {
            stringRedisTemplate.delete(sessionKey);
        }
        for (String username : usernameList) {
            jdbcTemplate.update("DELETE FROM user_account WHERE username = ?", username);
        }
    }

    /** 注册始终生成普通用户，哈希不能等于原始密码；大小写同名也只能创建一次。 */
    @Test
    void shouldRegisterNormalAccountAndRejectDuplicate() throws Exception {
        String username = uniqueUsername();
        mockMvc.perform(post("/api/auth/register").header("X-Auth-Request", "1")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(username).replace("}", ",\"role\":\"ADMIN\"}")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.id").isString()).andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        String hash = jdbcTemplate.queryForObject("SELECT password_hash FROM user_account WHERE username = ?",
                String.class, username);
        assertEquals(0, jdbcTemplate.queryForObject("SELECT deleted FROM user_account WHERE username = ?",
                Integer.class, username));
        assertNotEquals(TEST_PASSWORD, hash);
        assertTrue(passwordEncoder.matches(TEST_PASSWORD, hash));
        mockMvc.perform(post("/api/auth/register").header("X-Auth-Request", "1")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(username.toUpperCase())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USERNAME_EXISTS"));
    }

    /** 初始化只创建新管理员，重复执行不能覆盖已有账号及密码。 */
    @Test
    void shouldInitializeAdminWithoutOverwritingExistingAccount() {
        UserRegisterDTO userRegisterDTO = new UserRegisterDTO();
        userRegisterDTO.setUsername(uniqueUsername());
        userRegisterDTO.setPassword(TEST_PASSWORD);
        CurrentUserVO initializedAdminVO = authService.initializeAdmin(userRegisterDTO);
        assertEquals("ADMIN", initializedAdminVO.role());
        BusinessException failure = assertThrows(BusinessException.class,
                () -> authService.initializeAdmin(userRegisterDTO));
        assertEquals(ErrorCodeEnum.USERNAME_EXISTS, failure.getErrorCodeEnum());
    }

    /** 真正并发注册同名账号，一个成功、一个冲突，不产生重复记录。 */
    @Test
    void shouldEnforceUsernameUniquenessDuringConcurrentRegistration() throws Exception {
        String username = uniqueUsername();
        Callable<Integer> register = () -> mockMvc.perform(post("/api/auth/register").header("X-Auth-Request", "1")
                .contentType(MediaType.APPLICATION_JSON).content(credentials(username))).andReturn().getResponse().getStatus();
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> registrationFutureList = executorService.invokeAll(List.of(register, register));
            List<Integer> statusList = List.of(registrationFutureList.get(0).get(), registrationFutureList.get(1).get());
            assertTrue(statusList.contains(201));
            assertTrue(statusList.contains(409));
            assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_account WHERE username = ?",
                    Integer.class, username));
        } finally {
            executorService.shutdownNow();
        }
    }

    /** 刷新轮换后旧刷新凭证失效；退出会撤销当前会话下所有访问凭证。 */
    @Test
    void shouldRotateRefreshTokenAndRevokeWholeSessionOnLogout() throws Exception {
        String username = createAccount("USER");
        MvcResult login = login(username, "/api/auth/login");
        String oldAccess = accessToken(login);
        Cookie oldRefresh = login.getResponse().getCookie("zhilin_user_refresh");
        assertNotNull(oldRefresh);
        assertTrue(oldRefresh.isHttpOnly());
        assertEquals("/api/auth", oldRefresh.getPath());
        assertTrue(login.getResponse().getHeader("Set-Cookie").contains("SameSite=Strict"));
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + oldAccess))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value(username));

        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh").header("X-Auth-Request", "1").cookie(oldRefresh))
                .andExpect(status().isOk()).andReturn();
        mockMvc.perform(post("/api/auth/refresh").header("X-Auth-Request", "1").cookie(oldRefresh))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/logout").header("X-Auth-Request", "1")
                        .header("Authorization", "Bearer " + accessToken(refreshed)))
                .andExpect(status().isOk()).andExpect(cookie().maxAge("zhilin_user_refresh", 0));
        for (String revokedAccess : List.of(oldAccess, accessToken(refreshed))) {
            mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + revokedAccess))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/auth/refresh").header("X-Auth-Request", "1")
                        .cookie(refreshed.getResponse().getCookie("zhilin_user_refresh")))
                .andExpect(status().isUnauthorized());
    }

    /** 相同刷新凭证并发提交最多成功一次，成功响应对应的新凭证仍可使用。 */
    @Test
    void shouldAllowOnlyOneConcurrentRefresh() throws Exception {
        MvcResult login = login(createAccount("USER"), "/api/auth/login");
        Cookie refreshCookie = login.getResponse().getCookie("zhilin_user_refresh");
        Callable<MvcResult> refresh = () -> mockMvc.perform(post("/api/auth/refresh")
                .header("X-Auth-Request", "1").cookie(refreshCookie)).andReturn();
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            List<Future<MvcResult>> refreshFutureList = executorService.invokeAll(List.of(refresh, refresh));
            List<MvcResult> refreshResultList = List.of(refreshFutureList.get(0).get(), refreshFutureList.get(1).get());
            assertEquals(1, refreshResultList.stream().filter(result -> result.getResponse().getStatus() == 200).count());
            assertEquals(1, refreshResultList.stream().filter(result -> result.getResponse().getStatus() == 401).count());
            MvcResult success = refreshResultList.stream().filter(result -> result.getResponse().getStatus() == 200).findFirst().orElseThrow();
            mockMvc.perform(post("/api/auth/refresh").header("X-Auth-Request", "1")
                    .cookie(success.getResponse().getCookie("zhilin_user_refresh"))).andExpect(status().isOk());
        } finally {
            executorService.shutdownNow();
        }
    }

    /** 管理员校验由服务端执行，普通用户不能通过管理登录；降权立即影响现有会话。 */
    @Test
    void shouldEnforceAdminRoleAndClientIsolation() throws Exception {
        String userUsername = createAccount("USER");
        mockMvc.perform(post("/api/admin/auth/login").header("X-Auth-Request", "1")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(userUsername)))
                .andExpect(status().isForbidden());
        MvcResult userLogin = login(userUsername, "/api/auth/login");
        mockMvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + accessToken(userLogin)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(URI.create("/api/%61dmin/auth/me"))
                        .header("Authorization", "Bearer " + accessToken(userLogin)))
                .andExpect(status().isUnauthorized());
        String adminUsername = createAccount("ADMIN");
        MvcResult adminLogin = login(adminUsername, "/api/admin/auth/login");
        mockMvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + accessToken(adminLogin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("ADMIN"));
        jdbcTemplate.update("UPDATE user_account SET role = 'USER' WHERE username = ?", adminUsername);
        mockMvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + accessToken(adminLogin)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/auth/refresh").header("X-Auth-Request", "1")
                        .cookie(adminLogin.getResponse().getCookie("zhilin_admin_refresh")))
                .andExpect(status().isForbidden());
    }

    /** 自定义头是 Cookie 入口的 CSRF 防线；刷新凭证不能充当 Bearer 访问凭证。 */
    @Test
    void shouldRejectMissingHeadersWrongPasswordsAndTokenType() throws Exception {
        String username = createAccount("USER");
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials(username)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login").header("X-Auth-Request", "1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"incorrect\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        MvcResult login = login(username, "/api/auth/login");
        Cookie refreshCookie = login.getResponse().getCookie("zhilin_user_refresh");
        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie).header("X-Auth-Request", "1")
                .header("Origin", "https://untrusted.example")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + refreshCookie.getValue()))
                .andExpect(status().isUnauthorized());
    }

    /** 逻辑删除后，正确密码、已有访问令牌和刷新 Cookie 都不能继续登录或访问。 */
    @Test
    void shouldRejectDeletedAccountAcrossAuthenticationPaths() throws Exception {
        String username = createAccount("USER");
        MvcResult login = login(username, "/api/auth/login");
        jdbcTemplate.update("UPDATE user_account SET deleted = 1 WHERE username = ?", username);
        mockMvc.perform(post("/api/auth/login").header("X-Auth-Request", "1")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(username)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/refresh").header("X-Auth-Request", "1")
                        .cookie(login.getResponse().getCookie("zhilin_user_refresh")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken(login)))
                .andExpect(status().isForbidden());
    }

    /**
     * 生成并登记随机测试名，避免与既有账号及其他用例冲突。
     *
     * @return 本用例专属且满足账号约束的用户名
     */
    private String uniqueUsername() {
        String username = "test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        usernameList.add(username);
        return username;
    }

    /**
     * 在隔离测试库创建指定角色的随机账号，并登记待清理用户名。
     *
     * @param role 测试夹具的角色，USER 或 ADMIN
     * @return 本次创建的随机用户名
     */
    private String createAccount(String role) {
        UserAccountEntity userAccountEntity = new UserAccountEntity();
        userAccountEntity.setUsername(uniqueUsername());
        userAccountEntity.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        userAccountEntity.setRole(role);
        userAccountEntity.setDeleted(0);
        userAccountMapper.insert(userAccountEntity);
        return userAccountEntity.getUsername();
    }

    /**
     * 通过指定入口登录，并登记精确 Redis 会话键以便测试结束后清理。
     *
     * @param username 本用例创建的测试账号登录名
     * @param path 普通用户或管理员登录接口路径
     * @return 包含登录响应及刷新 Cookie 的 MVC 执行结果
     */
    private MvcResult login(String username, String path) throws Exception {
        MvcResult result = mockMvc.perform(post(path).header("X-Auth-Request", "1")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(username)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andReturn();
        AuthClientEnum authClientEnum = path.startsWith("/api/admin/") ? AuthClientEnum.ADMIN : AuthClientEnum.USER;
        AuthSessionDTO authSessionDTO = jwtTokenUtil.verify(accessToken(result), "access", authClientEnum);
        sessionKeyList.add(redisKeyUtil.build("auth:session:" + authSessionDTO.sessionId()));
        return result;
    }

    /**
     * 从测试登录或刷新响应中提取访问令牌，不输出到日志。
     *
     * @param result 成功登录或刷新请求的 MVC 执行结果
     * @return 响应 JSON 中的访问 JWT
     */
    private String accessToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    /**
     * 为内部生成的 ASCII 测试账号构造登录 JSON。
     *
     * @param username 本用例创建的 ASCII 登录名
     * @return 包含测试用户名及固定测试密码的 JSON 字符串
     */
    private String credentials(String username) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + TEST_PASSWORD + "\"}";
    }
}
