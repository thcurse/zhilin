package com.zhilin.security;

import com.zhilin.common.enums.ErrorCodeEnum;
import com.zhilin.common.exception.BusinessException;
import com.zhilin.dto.auth.AuthSessionDTO;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 从当前 HTTP 请求读取拦截器已认证的登录身份，不另行缓存会话或维护 ThreadLocal。
 * Controller 获取身份后显式传入 Service；异步任务应显式传递身份，不依赖本上下文。
 */
public final class AuthContext {

    private AuthContext() {
    }

    /**
     * 读取当前请求中的认证会话，仅接受拦截器写入的服务端属性。
     *
     * @return 已认证的完整会话，包含用户、登录端及会话编号
     * @throws BusinessException 当前没有 HTTP 请求或请求中没有有效认证会话
     */
    public static AuthSessionDTO getCurrentSession() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            throw new BusinessException(ErrorCodeEnum.UNAUTHORIZED);
        }

        // 只读取服务端请求属性，不从请求参数或请求头接受客户端声明的身份。
        Object sessionAttribute = servletRequestAttributes.getRequest()
                .getAttribute(AuthInterceptor.SESSION_ATTRIBUTE);
        if (!(sessionAttribute instanceof AuthSessionDTO authSessionDTO)) {
            throw new BusinessException(ErrorCodeEnum.UNAUTHORIZED);
        }
        return authSessionDTO;
    }

    /**
     * 获取当前登录用户的账号主键，供只需要用户身份的业务入口使用。
     *
     * @return 当前认证会话对应的用户 ID
     * @throws BusinessException 当前没有 HTTP 请求或请求中没有有效认证会话
     */
    public static long getCurrentUserId() {
        return getCurrentSession().userId();
    }
}
