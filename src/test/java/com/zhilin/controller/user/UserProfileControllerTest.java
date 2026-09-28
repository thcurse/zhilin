package com.zhilin.controller.user;

import com.jayway.jsonpath.JsonPath;
import com.zhilin.common.enums.AuthClientEnum;
import com.zhilin.common.util.RedisKeyUtil;
import com.zhilin.config.ObjectStorageProperties;
import com.zhilin.config.RedisKeyProperties;
import com.zhilin.dto.auth.AuthSessionDTO;
import com.zhilin.dto.auth.UserRegisterDTO;
import com.zhilin.security.JwtTokenUtil;
import com.zhilin.service.auth.AuthService;
import com.zhilin.vo.auth.CurrentUserVO;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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

/** 用独立数据库、Redis 前缀及 S3 测试桶验收资料持久化、身份隔离和真实头像存取。 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@EnabledIfSystemProperty(named = "infraTests", matches = "true")
class UserProfileControllerTest {
    /** 仅供随机测试账号使用的密码，不对应开发账号。 */
    private static final String TEST_PASSWORD = "Profile-test-2026";
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AuthService authService;
    @Autowired private JwtTokenUtil jwtTokenUtil;
    @Autowired private StringRedisTemplate stringRedisTemplate;
    @Autowired private RedisKeyUtil redisKeyUtil;
    @Autowired private RedisKeyProperties redisKeyProperties;
    @Autowired private ObjectStorageProperties objectStorageProperties;
    @Autowired private S3Client s3Client;
    /** 仅清理由当前用例创建的账号。 */
    private final List<Long> userIdList = new ArrayList<>();
    /** 仅清理由当前用例登录获得的精确会话键。 */
    private final List<String> sessionKeyList = new ArrayList<>();
    /** 仅清理当前用例在测试桶上传的精确对象键。 */
    private final List<String> objectKeyList = new ArrayList<>();

    /** 写入前确认三种基础设施均使用隔离命名空间。 */
    @BeforeEach
    void requireIsolatedInfrastructure() {
        assertEquals("zhilin_test", jdbcTemplate.queryForObject("SELECT DATABASE()", String.class));
        assertEquals("zhilin:test:", redisKeyProperties.getKeyPrefix());
        assertEquals("zhilin-media-test", objectStorageProperties.getBucket());
    }

    /** 只清理本用例登记的对象、会话和账号，不删除桶或清空业务表。 */
    @AfterEach
    void removeOwnFixtures() {
        for (String objectKey : objectKeyList) {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(objectStorageProperties.getBucket()).key(objectKey).build());
        }
        for (String sessionKey : sessionKeyList) {
            stringRedisTemplate.delete(sessionKey);
        }
        for (Long userId : userIdList) {
            jdbcTemplate.update("DELETE FROM user_profile WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM user_account WHERE id = ?", userId);
        }
    }

    /** GET 不补写资料，保存后重新读取仍有数据，且昵称不会修改登录名。 */
    @Test
    void shouldPersistOwnProfileWithoutWritingOnRead() throws Exception {
        String accessToken = createLoggedInUser();
        MvcResult defaultProfileResult = mockMvc.perform(get("/api/users/me/profile").header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.email").value(""))
                .andExpect(jsonPath("$.data.avatarUrl").isEmpty()).andReturn();
        String username = JsonPath.read(defaultProfileResult.getResponse().getContentAsString(), "$.data.username");
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_profile WHERE user_id = ?",
                Integer.class, userIdList.get(0)));
        saveProfile(accessToken, "{\"nickname\":\"  知邻读者  \",\"bio\":\"喜欢分享\",\"company\":\"测试公司\","
                + "\"position\":\"开发者\",\"email\":\"reader@example.com\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.nickname").value("知邻读者"));
        mockMvc.perform(get("/api/users/me/profile").header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.bio").value("喜欢分享"))
                .andExpect(jsonPath("$.data.email").value("reader@example.com"));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT deleted FROM user_profile WHERE user_id = ?",
                Integer.class, userIdList.get(0)));
    }

    /** 伪造 userId、role、deleted、avatarKey 都不能改变保存对象或权限；邮箱不会串给他人。 */
    @Test
    void shouldIgnoreForgedOwnershipAndProtectOtherProfile() throws Exception {
        String ownerAccessToken = createLoggedInUser();
        String otherAccessToken = createLoggedInUser();
        saveProfile(ownerAccessToken, "{\"nickname\":\"本人昵称\",\"email\":\"private@example.com\"}")
                .andExpect(status().isOk());
        saveProfile(otherAccessToken, "{\"nickname\":\"另一用户\",\"userId\":\"" + userIdList.get(0)
                + "\",\"role\":\"ADMIN\",\"deleted\":1,\"avatarKey\":\"other.png\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value(userIdList.get(1).toString()))
                .andExpect(jsonPath("$.data.email").value(""));
        mockMvc.perform(get("/api/users/me/profile").header("Authorization", bearer(ownerAccessToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.nickname").value("本人昵称"))
                .andExpect(jsonPath("$.data.email").value("private@example.com"));
        assertEquals("USER", jdbcTemplate.queryForObject("SELECT role FROM user_account WHERE id = ?",
                String.class, userIdList.get(1)));
    }

    /** 校验失败不写资料，可选字段省略时按 PUT 契约清空。 */
    @Test
    void shouldValidateAndClearOptionalFields() throws Exception {
        String accessToken = createLoggedInUser();
        for (String invalidBody : List.of("{\"nickname\":\" \"}",
                "{\"nickname\":\"昵称\",\"email\":\"invalid-mail\"}",
                "{\"nickname\":\"昵称\",\"bio\":\"" + "文".repeat(501) + "\"}")) {
            saveProfile(accessToken, invalidBody).andExpect(status().isBadRequest());
        }
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_profile WHERE user_id = ?",
                Integer.class, userIdList.get(0)));
        saveProfile(accessToken, "{\"nickname\":\"昵称\",\"email\":\"reader@example.com\",\"bio\":\"旧简介\"}")
                .andExpect(status().isOk());
        saveProfile(accessToken, "{\"nickname\":\"昵称\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(""))
                .andExpect(jsonPath("$.data.bio").value(""));
    }

    /** 首次并发保存只保留一份资料，不因先查后插而发生主键冲突。 */
    @Test
    void shouldAllowConcurrentFirstSave() throws Exception {
        String accessToken = createLoggedInUser();
        Callable<Integer> saveProfileTask = () -> saveProfile(accessToken, "{\"nickname\":\"并发保存\"}")
                .andReturn().getResponse().getStatus();
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> saveFutureList = executorService.invokeAll(List.of(saveProfileTask, saveProfileTask));
            assertEquals(200, saveFutureList.get(0).get());
            assertEquals(200, saveFutureList.get(1).get());
            assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_profile WHERE user_id = ?",
                    Integer.class, userIdList.get(0)));
        } finally {
            executorService.shutdownNow();
        }
    }

    /** 真实头像上传后可匿名读取 PNG，后续保存文字不会覆盖头像，S3 桶本身仍禁止匿名读取。 */
    @Test
    void shouldUploadReadAndPreserveAvatarInPrivateBucket() throws Exception {
        String accessToken = createLoggedInUser();
        saveProfile(accessToken, "{\"nickname\":\"保留昵称\",\"bio\":\"原简介\"}").andExpect(status().isOk());
        MockMultipartFile avatarFile = new MockMultipartFile("file", "avatar.png", "image/png", pngContent(640, 320));
        MvcResult uploadResult = mockMvc.perform(multipart("/api/users/me/avatar").file(avatarFile)
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.nickname").value("保留昵称"))
                .andExpect(jsonPath("$.data.bio").value("原简介")).andReturn();
        String avatarUrl = JsonPath.read(uploadResult.getResponse().getContentAsString(), "$.data.avatarUrl");
        String objectKey = avatarUrl.substring("/api/".length());
        objectKeyList.add(objectKey);
        byte[] avatarContent = mockMvc.perform(get(avatarUrl)).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage storedImage = ImageIO.read(new ByteArrayInputStream(avatarContent));
        assertEquals(512, storedImage.getWidth());
        assertEquals(256, storedImage.getHeight());
        saveProfile(accessToken, "{\"nickname\":\"修改文字\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value(avatarUrl));
        URI directObjectUri = URI.create(objectStorageProperties.getEndpoint() + "/"
                + objectStorageProperties.getBucket() + "/" + objectKey);
        HttpResponse<Void> unsignedResponse = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
                .send(HttpRequest.newBuilder(directObjectUri).timeout(Duration.ofSeconds(5)).GET().build(),
                        HttpResponse.BodyHandlers.discarding());
        assertEquals(403, unsignedResponse.statusCode());
    }

    /** 未登录、伪装图片、空文件、超限图片和不存在对象均返回对应失败，不生成头像链接。 */
    @Test
    void shouldRejectInvalidAvatarsAndUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/users/me/profile")).andExpect(status().isUnauthorized());
        MockMultipartFile forgedFile = new MockMultipartFile("file", "fake.png", "image/png", "<script>test</script>".getBytes());
        mockMvc.perform(multipart("/api/users/me/avatar").file(forgedFile)).andExpect(status().isUnauthorized());
        String accessToken = createLoggedInUser();
        for (MockMultipartFile invalidFile : List.of(forgedFile,
                new MockMultipartFile("file", "empty.png", "image/png", new byte[0]),
                new MockMultipartFile("file", "large-pixels.png", "image/png", pngContent(2049, 1)))) {
            mockMvc.perform(multipart("/api/users/me/avatar").file(invalidFile)
                            .header("Authorization", bearer(accessToken)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_AVATAR"));
        }
        MockMultipartFile oversizedFile = new MockMultipartFile("file", "large.png", "image/png", new byte[2 * 1024 * 1024 + 1]);
        mockMvc.perform(multipart("/api/users/me/avatar").file(oversizedFile)
                        .header("Authorization", bearer(accessToken))).andExpect(status().isPayloadTooLarge());
        mockMvc.perform(get("/api/avatars/not-an-avatar.png")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/avatars/" + UUID.randomUUID().toString().replace("-", "") + ".png"))
                .andExpect(status().isNotFound());
    }

    /** 资料删除后不能通过保存操作恢复，账号删除后也无法读取或上传。 */
    @Test
    void shouldRejectDeletedProfileAndAccount() throws Exception {
        String accessToken = createLoggedInUser();
        saveProfile(accessToken, "{\"nickname\":\"待删除资料\"}").andExpect(status().isOk());
        jdbcTemplate.update("UPDATE user_profile SET deleted = 1 WHERE user_id = ?", userIdList.get(0));
        saveProfile(accessToken, "{\"nickname\":\"不能恢复\"}").andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users/me/profile").header("Authorization", bearer(accessToken)))
                .andExpect(status().isForbidden());
        jdbcTemplate.update("UPDATE user_account SET deleted = 1 WHERE id = ?", userIdList.get(0));
        MockMultipartFile avatarFile = new MockMultipartFile("file", "avatar.png", "image/png", pngContent(1, 1));
        mockMvc.perform(multipart("/api/users/me/avatar").file(avatarFile)
                        .header("Authorization", bearer(accessToken))).andExpect(status().isForbidden());
    }

    /**
     * 创建随机普通用户并通过真实登录接口获取凭证，登记清理对象。
     *
     * @return 仅供当前用例使用的访问令牌
     */
    private String createLoggedInUser() throws Exception {
        UserRegisterDTO userRegisterDTO = new UserRegisterDTO();
        userRegisterDTO.setUsername("profile_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        userRegisterDTO.setPassword(TEST_PASSWORD);
        CurrentUserVO currentUserVO = authService.register(userRegisterDTO);
        userIdList.add(Long.valueOf(currentUserVO.id()));
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login").header("X-Auth-Request", "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + currentUserVO.username()
                                + "\",\"password\":\"" + TEST_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        String accessToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.data.accessToken");
        AuthSessionDTO authSessionDTO = jwtTokenUtil.verify(accessToken, "access", AuthClientEnum.USER);
        sessionKeyList.add(redisKeyUtil.build("auth:session:" + authSessionDTO.sessionId()));
        return accessToken;
    }

    /**
     * 提交本人资料，统一附加测试会话的认证头。
     *
     * @param accessToken 当前用例的访问令牌
     * @param jsonBody 用例构造的资料 JSON
     * @return 可继续断言的 MVC 执行结果
     */
    private ResultActions saveProfile(String accessToken, String jsonBody)
            throws Exception {
        return mockMvc.perform(put("/api/users/me/profile").header("Authorization", bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON).content(jsonBody));
    }

    /**
     * 生成请求头值，不把令牌写入日志。
     *
     * @param accessToken 本用例访问令牌
     * @return 标准 Bearer 请求头值
     */
    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    /**
     * 在内存构造合法 PNG 夹具，支持验证像素限制及实际缩放结果。
     *
     * @param width 夹具图片宽度，单位像素
     * @param height 夹具图片高度，单位像素
     * @return 生成的 PNG 内容
     */
    private byte[] pngContent(int width, int height) throws Exception {
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "PNG", outputStream);
        return outputStream.toByteArray();
    }
}
