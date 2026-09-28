package com.zhilin.dto.auth;

import com.zhilin.vo.auth.AuthTokenVO;

/** 服务层到 Controller 的签发结果，包含敏感刷新令牌，禁止直接返回 JSON。 */
public record AuthTokenPairDTO(
        /** 可返回给前端的访问凭证与身份。 */
        AuthTokenVO authTokenVO,
        /** 只写入 HttpOnly Cookie 的刷新凭证。 */
        String refreshToken,
        /** 刷新 Cookie 剩余存活秒数。 */
        long refreshExpiresInSeconds) {
}
