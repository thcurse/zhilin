package com.zhilin.security;

import com.zhilin.common.enums.AuthClientEnum;
import com.zhilin.common.exception.BusinessException;
import com.zhilin.config.AuthProperties;
import com.zhilin.dto.auth.AuthSessionDTO;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

/** 无外部依赖地验证签名、到期与令牌用途隔离。 */
class JwtTokenUtilTest {
    /**
     * 创建使用测试密钥的 JWT 工具，不读取本机运行凭据。
     *
     * @param secret 本用例专用的签名密钥
     * @return 使用指定测试密钥的 JWT 工具
     */
    private JwtTokenUtil createUtil(String secret) {
        AuthProperties authProperties = new AuthProperties();
        authProperties.setSecret(secret);
        return new JwtTokenUtil(authProperties);
    }

    /**
     * 验证正确令牌可解析，并拒绝过期、错误密钥、错误用途、跨端及格式错误的令牌。
     */
    @Test
    void shouldRejectExpiredTamperedAndWrongPurposeTokens() {
        JwtTokenUtil jwtTokenUtil = createUtil("unit-test-secret-with-at-least-32-characters");
        AuthSessionDTO authSessionDTO = new AuthSessionDTO(1, "session-id", "refresh-id", AuthClientEnum.USER,
                Instant.now().plusSeconds(60));
        String accessToken = jwtTokenUtil.create(authSessionDTO, "access", Instant.now().plusSeconds(30));
        assertEquals(1, jwtTokenUtil.verify(accessToken, "access", AuthClientEnum.USER).userId());
        assertThrows(BusinessException.class, () -> jwtTokenUtil.verify(accessToken, "refresh", AuthClientEnum.USER));
        assertThrows(BusinessException.class, () -> jwtTokenUtil.verify(accessToken, "access", AuthClientEnum.ADMIN));
        JwtTokenUtil otherSignerJwtTokenUtil = createUtil("another-unit-test-secret-with-at-least-32-characters");
        assertThrows(BusinessException.class, () -> otherSignerJwtTokenUtil.verify(accessToken, "access", AuthClientEnum.USER));
        String expiredAccessToken = jwtTokenUtil.create(authSessionDTO, "access", Instant.now().minusSeconds(1));
        assertThrows(BusinessException.class, () -> jwtTokenUtil.verify(expiredAccessToken, "access", AuthClientEnum.USER));
        assertThrows(BusinessException.class, () -> jwtTokenUtil.verify("not.a.token", "access", AuthClientEnum.USER));
    }
}
