package com.zhilin.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.zhilin.common.enums.AuthClientEnum;
import com.zhilin.common.enums.ErrorCodeEnum;
import com.zhilin.common.exception.BusinessException;
import com.zhilin.config.AuthProperties;
import com.zhilin.dto.auth.AuthSessionDTO;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** 只负责 JWT 签名与严格校验；是否已经注销由会话服务检查 Redis。 */
@Component
public class JwtTokenUtil {
    private final AuthProperties authProperties;
    private final Algorithm algorithm;

    /**
     * 根据认证配置初始化 JWT 签名算法。
     *
     * @param authProperties 提供签名密钥与签发者的认证配置
     */
    public JwtTokenUtil(AuthProperties authProperties) {
        this.authProperties = authProperties;
        this.algorithm = Algorithm.HMAC256(authProperties.getSecret());
    }

    /**
     * 签发指定用途的 JWT，访问令牌与刷新令牌具有不同的用途声明。
     *
     * @param authSessionDTO 写入令牌的账号、登录端、会话编号和刷新编号
     * @param tokenType 令牌用途：access 表示访问，refresh 表示刷新
     * @param expiresAt 当前令牌的到期时间，不得晚于会话的绝对截止时间
     * @return 使用配置密钥签名的 JWT 字符串
     */
    public String create(AuthSessionDTO authSessionDTO, String tokenType, Instant expiresAt) {
        return JWT.create().withIssuer(authProperties.getIssuer())
                .withAudience(authSessionDTO.authClientEnum().name())
                .withSubject(Long.toString(authSessionDTO.userId()))
                .withClaim("sid", authSessionDTO.sessionId()).withJWTId(authSessionDTO.refreshId())
                .withClaim("type", tokenType)
                .withClaim("sessionExp", authSessionDTO.sessionExpiresAt().getEpochSecond())
                .withIssuedAt(Instant.now()).withExpiresAt(expiresAt).sign(algorithm);
    }

    /**
     * 校验 JWT 签名、签发者、受众、用途及必需声明，不查询 Redis。
     *
     * @param token 待校验的原始 JWT，不能为 null 或空白
     * @param tokenType 预期用途，access 或 refresh
     * @param authClientEnum 预期登录端，用于校验令牌受众
     * @return 已通过 JWT 校验的会话声明；调用方仍需检查 Redis 和账号权限
     * @throws BusinessException 令牌缺失、校验失败、声明无法解析或会话已到期
     */
    public AuthSessionDTO verify(String token, String tokenType, AuthClientEnum authClientEnum) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.UNAUTHORIZED);
        }
        try {
            DecodedJWT decodedJWT = JWT.require(algorithm).withIssuer(authProperties.getIssuer())
                    .withAudience(authClientEnum.name()).withClaim("type", tokenType)
                    .withClaimPresence("exp").withClaimPresence("sub").withClaimPresence("sid")
                    .withClaimPresence("jti").withClaimPresence("sessionExp").build().verify(token);
            Instant sessionExpiresAt = Instant.ofEpochSecond(decodedJWT.getClaim("sessionExp").asLong());
            if (!sessionExpiresAt.isAfter(Instant.now())) {
                throw new BusinessException(ErrorCodeEnum.UNAUTHORIZED);
            }
            return new AuthSessionDTO(Long.parseLong(decodedJWT.getSubject()), decodedJWT.getClaim("sid").asString(),
                    decodedJWT.getId(), authClientEnum, sessionExpiresAt);
        } catch (JWTVerificationException | IllegalArgumentException | NullPointerException exception) {
            // 不记录原始令牌或解析消息，避免凭证进入日志。
            throw new BusinessException(ErrorCodeEnum.UNAUTHORIZED);
        }
    }
}
