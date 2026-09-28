package com.zhilin.security;

import com.zhilin.common.enums.AuthClientEnum;
import com.zhilin.config.AuthProperties;
import com.zhilin.dto.auth.AuthTokenPairDTO;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** 刷新凭证的 Cookie 读写，用户端与管理端使用独立名称及路径。 */
@Component
@RequiredArgsConstructor
public class AuthCookieUtil {
    private final AuthProperties authProperties;

    /**
     * 读取当前登录端的刷新 Cookie，不从查询参数读取令牌。
     *
     * @param request 浏览器发来的 HTTP 请求
     * @param authClientEnum 决定刷新 Cookie 名称的登录端
     * @return 刷新令牌；没有对应 Cookie 时返回 null
     */
    public String read(HttpServletRequest request, AuthClientEnum authClientEnum) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (name(authClientEnum).equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * 将刷新令牌写入 HttpOnly Cookie，禁止前端 JavaScript 读取。
     *
     * @param response 待添加 Set-Cookie 响应头的 HTTP 响应
     * @param authClientEnum 决定 Cookie 名称及路径的登录端
     * @param authTokenPairDTO 签发结果，提供刷新令牌及剩余有效秒数
     */
    public void write(HttpServletResponse response, AuthClientEnum authClientEnum, AuthTokenPairDTO authTokenPairDTO) {
        setCookie(response, authClientEnum, authTokenPairDTO.refreshToken(),
                authTokenPairDTO.refreshExpiresInSeconds());
    }

    /**
     * 将当前端刷新 Cookie 的有效期设为零；应在服务端会话撤销成功后调用。
     *
     * @param response 待添加清除 Cookie 指令的 HTTP 响应
     * @param authClientEnum 需要退出的登录端
     */
    public void clear(HttpServletResponse response, AuthClientEnum authClientEnum) {
        setCookie(response, authClientEnum, "", 0);
    }

    /**
     * 设置当前主机范围的刷新 Cookie，按配置启用 Secure，固定 HttpOnly 和 SameSite=Strict。
     *
     * @param response 待写入 Cookie 的 HTTP 响应
     * @param authClientEnum 决定 Cookie 名称及路径的登录端
     * @param value 刷新令牌；清除 Cookie 时为空字符串
     * @param maxAgeSeconds Cookie 剩余有效秒数，0 表示立即清除
     */
    private void setCookie(HttpServletResponse response, AuthClientEnum authClientEnum,
                           String value, long maxAgeSeconds) {
        String path = authClientEnum == AuthClientEnum.ADMIN ? "/api/admin/auth" : "/api/auth";
        ResponseCookie cookie = ResponseCookie.from(name(authClientEnum), value).path(path)
                .httpOnly(true).secure(authProperties.isCookieSecure()).sameSite("Strict")
                .maxAge(maxAgeSeconds).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * 区分同主机上用户端与管理端的刷新 Cookie，避免跨端覆盖。
     *
     * @param authClientEnum Cookie 所属登录端
     * @return 当前端的固定刷新 Cookie 名称
     */
    private String name(AuthClientEnum authClientEnum) {
        return authClientEnum == AuthClientEnum.ADMIN ? "zhilin_admin_refresh" : "zhilin_user_refresh";
    }
}
