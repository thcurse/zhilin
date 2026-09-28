package com.zhilin.service.auth.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhilin.common.enums.AuthClientEnum;
import com.zhilin.common.enums.ErrorCodeEnum;
import com.zhilin.common.exception.BusinessException;
import com.zhilin.common.util.RedisKeyUtil;
import com.zhilin.config.AuthProperties;
import com.zhilin.dto.auth.AuthSessionDTO;
import com.zhilin.dto.auth.AuthTokenPairDTO;
import com.zhilin.dto.auth.PasswordLoginDTO;
import com.zhilin.dto.auth.UserRegisterDTO;
import com.zhilin.entity.auth.UserAccountEntity;
import com.zhilin.mapper.auth.UserAccountMapper;
import com.zhilin.security.JwtTokenUtil;
import com.zhilin.service.auth.AuthService;
import com.zhilin.vo.auth.AuthTokenVO;
import com.zhilin.vo.auth.CurrentUserVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** 编排账号校验与双令牌会话；数据库失败或 Redis 故障不会伪装成密码错误。 */
@Service
public class AuthServiceImpl implements AuthService {
    /** 比较旧刷新编号并原子替换，保留原 TTL；注销后绝不重建会话。 */
    private static final DefaultRedisScript<Long> ROTATE_REFRESH = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            redis.call('SET', KEYS[1], ARGV[2], 'KEEPTTL')
            return 1
            """, Long.class);

    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisKeyUtil redisKeyUtil;
    private final JwtTokenUtil jwtTokenUtil;
    private final AuthProperties authProperties;
    /** 账号不存在时仍执行一次 BCrypt，减少账号存在性时间差。 */
    private final String dummyPasswordHash;

    /**
     * 装配认证依赖，并预生成账号不存在时用于降低时间差的密码校验的摘要。
     *
     * @param userAccountMapper 登录账号的数据访问组件
     * @param passwordEncoder 密码摘要生成和匹配组件
     * @param stringRedisTemplate 保存和原子轮换会话的 Redis 组件
     * @param redisKeyUtil 为会话键添加当前环境前缀
     * @param jwtTokenUtil JWT 签发及校验组件
     * @param authProperties 令牌期限和签名等认证配置
     */
    public AuthServiceImpl(UserAccountMapper userAccountMapper, PasswordEncoder passwordEncoder,
                           StringRedisTemplate stringRedisTemplate, RedisKeyUtil redisKeyUtil,
                           JwtTokenUtil jwtTokenUtil, AuthProperties authProperties) {
        this.userAccountMapper = userAccountMapper;
        this.passwordEncoder = passwordEncoder;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisKeyUtil = redisKeyUtil;
        this.jwtTokenUtil = jwtTokenUtil;
        this.authProperties = authProperties;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * 创建普通账号，不自动登录；由数据库唯一索引保证并发注册不产生同名账号。
     *
     * @param userRegisterDTO 已通过入口校验的注册参数，包含登录名和原始密码
     * @return 新建普通账号的公开信息，不包含密码摘要
     * @throws BusinessException 用户名已被使用
     */
    @Override
    public CurrentUserVO register(UserRegisterDTO userRegisterDTO) {
        return createAccount(userRegisterDTO, "USER");
    }

    /**
     * 从显式启用的初始化入口创建管理员，不覆盖已有账号或提升其权限。
     *
     * @param userRegisterDTO 已通过入口校验的注册参数，包含登录名和原始密码
     * @return 新建管理员的公开信息
     * @throws BusinessException 用户名已被使用，初始化被拒绝
     */
    @Override
    public CurrentUserVO initializeAdmin(UserRegisterDTO userRegisterDTO) {
        return createAccount(userRegisterDTO, "ADMIN");
    }

    /**
     * 复用普通注册与管理员初始化的账号创建规则，保存密码摘要和未删除标记。
     *
     * @param userRegisterDTO 已通过入口校验的注册参数，包含登录名和原始密码
     * @param role 由服务端入口指定的 USER 或 ADMIN，不接受客户端指定
     * @return 创建账号的公开信息
     * @throws BusinessException 登录名触发唯一索引冲突
     */
    private CurrentUserVO createAccount(UserRegisterDTO userRegisterDTO, String role) {
        UserAccountEntity userAccountEntity = new UserAccountEntity();
        userAccountEntity.setUsername(userRegisterDTO.getUsername().toLowerCase(Locale.ROOT));
        userAccountEntity.setPasswordHash(passwordEncoder.encode(userRegisterDTO.getPassword()));
        userAccountEntity.setRole(role);
        userAccountEntity.setDeleted(0);
        try {
            userAccountMapper.insert(userAccountEntity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCodeEnum.USERNAME_EXISTS);
        }
        return toCurrentUser(userAccountEntity);
    }

    /**
     * 校验密码与账号权限，创建独立 Redis 会话并签发双令牌。
     *
     * @param passwordLoginDTO 已通过入口校验的用户名和原始密码
     * @param authClientEnum 请求所属端，管理端额外要求 ADMIN 角色
     * @return 访问凭证、用户信息及只供 Cookie 写入的刷新凭证
     * @throws BusinessException 用户名或密码错误、账号已删除或没有管理端权限
     */
    @Override
    public AuthTokenPairDTO login(PasswordLoginDTO passwordLoginDTO, AuthClientEnum authClientEnum) {
        UserAccountEntity userAccountEntity = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccountEntity>()
                .eq(UserAccountEntity::getUsername, passwordLoginDTO.getUsername().toLowerCase(Locale.ROOT)));
        String passwordHash = userAccountEntity == null ? dummyPasswordHash : userAccountEntity.getPasswordHash();
        boolean isPasswordMatched = passwordLoginDTO.getPassword().getBytes(StandardCharsets.UTF_8).length <= 72
                && passwordEncoder.matches(passwordLoginDTO.getPassword(), passwordHash);
        if (userAccountEntity == null || !isPasswordMatched) {
            throw new BusinessException(ErrorCodeEnum.INVALID_CREDENTIALS);
        }
        checkAccountPermission(userAccountEntity, authClientEnum);

        // 两个令牌共享会话编号；Redis 的 TTL 与刷新令牌的绝对截止时间一致。
        Instant sessionExpiresAt = Instant.now().plusSeconds(authProperties.getRefreshSeconds());
        AuthSessionDTO authSessionDTO = new AuthSessionDTO(userAccountEntity.getId(), UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), authClientEnum, sessionExpiresAt);
        AuthTokenPairDTO authTokenPairDTO = issueTokens(authSessionDTO, userAccountEntity);
        stringRedisTemplate.opsForValue().set(sessionKey(authSessionDTO), sessionValue(authSessionDTO),
                Duration.between(Instant.now(), sessionExpiresAt));
        return authTokenPairDTO;
    }

    /**
     * 轮换刷新令牌并签发新的访问令牌，保留原会话的绝对到期时间。
     * Redis 原子校验旧刷新编号，同一个刷新令牌只能成功使用一次。
     *
     * @param refreshToken 客户端提交的刷新 JWT，不能为空且必须属于请求端
     * @param authClientEnum 请求所属端，用于校验令牌受众和账号权限
     * @return 新的双令牌及各自剩余有效秒数、用户信息
     * @throws BusinessException 令牌无效、会话失效、重复刷新或账号无权限
     */
    @Override
    public AuthTokenPairDTO refresh(String refreshToken, AuthClientEnum authClientEnum) {
        // 先校验旧令牌及实时账号权限，再生成本次轮换所需的新刷新编号。
        AuthSessionDTO previousAuthSessionDTO = jwtTokenUtil.verify(refreshToken, "refresh", authClientEnum);
        UserAccountEntity userAccountEntity = requireAccount(previousAuthSessionDTO);
        AuthSessionDTO renewedAuthSessionDTO = new AuthSessionDTO(
                previousAuthSessionDTO.userId(), previousAuthSessionDTO.sessionId(),
                UUID.randomUUID().toString(), authClientEnum, previousAuthSessionDTO.sessionExpiresAt());
        AuthTokenPairDTO authTokenPairDTO = issueTokens(renewedAuthSessionDTO, userAccountEntity);
        // Lua 返回 1 才表示轮换成功；会话被撤销或旧编号已使用时不能返回新令牌。
        Long refreshRotationResult = stringRedisTemplate.execute(
                ROTATE_REFRESH, List.of(sessionKey(previousAuthSessionDTO)),
                sessionValue(previousAuthSessionDTO), sessionValue(renewedAuthSessionDTO));
        if (!Long.valueOf(1).equals(refreshRotationResult)) {
            throw new BusinessException(ErrorCodeEnum.UNAUTHORIZED);
        }
        return authTokenPairDTO;
    }

    /**
     * 校验访问 JWT、Redis 会话及实时账号权限。
     *
     * @param accessToken Bearer 请求头中提取的访问 JWT，不包含 Bearer 前缀
     * @param authClientEnum 接口所属端，用于阻止用户端与管理端凭证混用
     * @return 验证通过的服务端会话声明
     * @throws BusinessException 访问令牌无效、会话已撤销或账号无权限
     */
    @Override
    public AuthSessionDTO authenticate(String accessToken, AuthClientEnum authClientEnum) {
        AuthSessionDTO authSessionDTO = jwtTokenUtil.verify(accessToken, "access", authClientEnum);
        String storedSession = stringRedisTemplate.opsForValue().get(sessionKey(authSessionDTO));
        // 刷新后旧访问令牌可用至自身到期，但 Redis 会话删除后所有访问令牌立即失效。
        String identityPrefix = authSessionDTO.userId() + ":" + authSessionDTO.authClientEnum().name() + ":";
        if (storedSession == null || !storedSession.startsWith(identityPrefix)) {
            throw new BusinessException(ErrorCodeEnum.UNAUTHORIZED);
        }
        requireAccount(authSessionDTO);
        return authSessionDTO;
    }

    /**
     * 根据会话重新检查账号权限，返回可公开的当前身份。
     *
     * @param authSessionDTO 服务端已认证的会话，包含账号、登录端和会话编号
     * @return 当前账号编号、登录名和角色，不包含密码摘要
     * @throws BusinessException 账号不存在、已逻辑删除或不具备当前端的访问权限
     */
    @Override
    public CurrentUserVO currentUser(AuthSessionDTO authSessionDTO) {
        return toCurrentUser(requireAccount(authSessionDTO));
    }

    /**
     * 删除当前 Redis 会话，使其访问令牌与刷新令牌失效；其他设备会话不受影响。
     *
     * @param authSessionDTO 服务端已认证的会话，包含账号、登录端和会话编号
     */
    @Override
    public void logout(AuthSessionDTO authSessionDTO) {
        stringRedisTemplate.delete(sessionKey(authSessionDTO));
    }

    /**
     * 从数据库读取账号并校验当前权限，使逻辑删除及降权在下一次请求中生效。
     *
     * @param authSessionDTO 服务端已认证的会话，包含账号、登录端和会话编号
     * @return 存在且允许当前端访问的账号实体
     * @throws BusinessException 账号不存在、已逻辑删除或不具备当前端的访问权限
     */
    private UserAccountEntity requireAccount(AuthSessionDTO authSessionDTO) {
        UserAccountEntity userAccountEntity = userAccountMapper.selectById(authSessionDTO.userId());
        if (userAccountEntity == null) {
            throw new BusinessException(ErrorCodeEnum.UNAUTHORIZED);
        }
        checkAccountPermission(userAccountEntity, authSessionDTO.authClientEnum());
        return userAccountEntity;
    }

    /**
     * 检查账号未删除，并在管理端校验 ADMIN 角色。
     *
     * @param userAccountEntity 已从数据库读取的非空账号实体
     * @param authClientEnum 本次访问所属端
     * @throws BusinessException 删除标记不为 0，或管理端访问者不是管理员
     */
    private void checkAccountPermission(UserAccountEntity userAccountEntity, AuthClientEnum authClientEnum) {
        if (!Integer.valueOf(0).equals(userAccountEntity.getDeleted())
                || (authClientEnum == AuthClientEnum.ADMIN && !"ADMIN".equals(userAccountEntity.getRole()))) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN);
        }
    }

    /**
     * 签发双令牌，访问令牌的期限不得超过会话剩余期限。
     *
     * @param authSessionDTO 本次签发使用的会话编号、刷新编号和绝对截止时间
     * @param userAccountEntity 已通过权限校验的账号，用于生成公开身份
     * @return 双令牌及剩余有效秒数，刷新令牌仅供服务端写入 Cookie
     * @throws BusinessException 会话剩余有效时间不足一秒
     */
    private AuthTokenPairDTO issueTokens(AuthSessionDTO authSessionDTO, UserAccountEntity userAccountEntity) {
        Instant now = Instant.now();
        long refreshSeconds = Duration.between(now, authSessionDTO.sessionExpiresAt()).getSeconds();
        if (refreshSeconds < 1) {
            throw new BusinessException(ErrorCodeEnum.UNAUTHORIZED);
        }
        // 访问令牌不能比所属会话活得更久，取配置期限与会话剩余期限中的较小值。
        long accessSeconds = Math.min(authProperties.getAccessSeconds(), refreshSeconds);
        String accessToken = jwtTokenUtil.create(authSessionDTO, "access", now.plusSeconds(accessSeconds));
        String refreshToken = jwtTokenUtil.create(authSessionDTO, "refresh", authSessionDTO.sessionExpiresAt());
        return new AuthTokenPairDTO(new AuthTokenVO(accessToken, accessSeconds, toCurrentUser(userAccountEntity)),
                refreshToken, refreshSeconds);
    }

    /**
     * 提取允许公开的账号字段，将主键转换为字符串。
     *
     * @param userAccountEntity 非空账号实体，主键已由数据库生成
     * @return 不包含密码摘要的账号身份
     */
    private CurrentUserVO toCurrentUser(UserAccountEntity userAccountEntity) {
        return new CurrentUserVO(userAccountEntity.getId().toString(),
                userAccountEntity.getUsername(), userAccountEntity.getRole());
    }

    /**
     * 生成带当前环境前缀的 Redis 会话键，不包含原始令牌。
     *
     * @param authSessionDTO 服务端已认证的会话，包含账号、登录端和会话编号
     * @return 当前会话对应的完整 Redis 键
     */
    private String sessionKey(AuthSessionDTO authSessionDTO) {
        return redisKeyUtil.build("auth:session:" + authSessionDTO.sessionId());
    }

    /**
     * 编码 Redis 会话身份及刷新编号，不保存可直接使用的完整 JWT。
     *
     * @param authSessionDTO 服务端已认证的会话，包含账号、登录端和会话编号
     * @return 账号编号、登录端和刷新编号组成的冒号分隔字符串
     */
    private String sessionValue(AuthSessionDTO authSessionDTO) {
        return authSessionDTO.userId() + ":" + authSessionDTO.authClientEnum().name()
                + ":" + authSessionDTO.refreshId();
    }
}
