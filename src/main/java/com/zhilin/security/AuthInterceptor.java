package com.zhilin.security;

import com.zhilin.common.enums.AuthClientEnum;
import com.zhilin.common.enums.ErrorCodeEnum;
import com.zhilin.common.exception.BusinessException;
import com.zhilin.config.AuthProperties;
import com.zhilin.dto.auth.AuthSessionDTO;
import com.zhilin.service.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Set;

/** API 默认要求 Bearer 凭证；只有明确列出的注册、登录和刷新入口公开。 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {
    /** 认证成功后的请求属性名，不接受客户端参数覆盖。 */
    public static final String SESSION_ATTRIBUTE = "zhilin.auth.session";
    /** 仅这些 POST 入口不要求访问令牌，刷新入口另行验证刷新 Cookie。 */
    private static final Set<String> PUBLIC_POST_PATHS = Set.of(
            "/api/auth/register", "/api/auth/login", "/api/auth/refresh",
            "/api/admin/auth/login", "/api/admin/auth/refresh");
    private final AuthService authService;
    private final AuthProperties authProperties;

    /**
     * 在 Controller 执行前检查认证请求来源，并为受保护接口建立当前会话上下文。
     *
     * @param request 当前 HTTP 请求，提供来源、路径和 Bearer 凭证
     * @param response 当前 HTTP 响应，认证接口会写入禁止缓存的响应头
     * @param handler Spring MVC 选定的处理器；非方法处理器交由框架继续处理
     * @return 校验通过或不属于 API 方法处理器时返回 true
     * @throws BusinessException 请求头或来源不可信、凭证缺失、会话无效或账号无权限
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        // 使用 MVC 已解析的映射，避免编码路径或路径参数干扰管理端识别。
        String path = String.valueOf(request.getAttribute(
                HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
        // 头像是公开展示内容；仅放行这个明确的只读映射，不放行上传和资料接口。
        if ("/api/avatars/{avatarKey}".equals(path)
                && ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))) {
            return true;
        }
        boolean isAuthPath = path.startsWith("/api/auth/") || path.startsWith("/api/admin/auth/");
        if (isAuthPath || path.startsWith("/api/users/me/")) {
            // 本人资料包含邮箱等私有信息，和认证响应一样禁止缓存。
            response.setHeader("Cache-Control", "no-store");
        }
        // 自定义头迫使跨站脚本先预检；本项目不开放 CORS，浏览器只能从同源代理调用。
        // SameSite=Strict 是附加保护，不能替代此检查。修改 CORS 时必须同步复核 CSRF 规则。
        if (isAuthPath && !"GET".equals(request.getMethod())
                && !"1".equals(request.getHeader("X-Auth-Request"))) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN);
        }
        // 同时检查来源，防止开发代理或未来错误配置的 CORS 破坏自定义头保护。
        String origin = request.getHeader("Origin");
        if (isAuthPath && !"GET".equals(request.getMethod()) && origin != null
                && !authProperties.getAllowedOrigins().contains(origin)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN);
        }
        if ("POST".equals(request.getMethod()) && PUBLIC_POST_PATHS.contains(path)) {
            return true;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCodeEnum.UNAUTHORIZED);
        }
        AuthClientEnum authClientEnum = path.startsWith("/api/admin/") ? AuthClientEnum.ADMIN : AuthClientEnum.USER;
        AuthSessionDTO authSessionDTO = authService.authenticate(authorization.substring(7), authClientEnum);
        request.setAttribute(SESSION_ATTRIBUTE, authSessionDTO);
        return true;
    }
}
