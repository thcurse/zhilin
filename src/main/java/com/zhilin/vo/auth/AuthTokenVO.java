package com.zhilin.vo.auth;

/** 登录及刷新返回的短期凭证；刷新令牌仅通过 HttpOnly Cookie 传递。 */
public record AuthTokenVO(
        /** 调用受保护接口时使用的 Bearer JWT。 */
        String accessToken,
        /** 访问令牌剩余有效时间，单位秒。 */
        long expiresInSeconds,
        /** 当前账号身份；user 是已公开的 JSON 字段名，保持接口兼容。 */
        CurrentUserVO user) {
}
