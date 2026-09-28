package com.zhilin.security;

import com.zhilin.common.enums.AuthClientEnum;
import com.zhilin.common.enums.ErrorCodeEnum;
import com.zhilin.common.exception.BusinessException;
import com.zhilin.dto.auth.AuthSessionDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 验证认证上下文拒绝伪造身份，并在请求切换后不残留上一用户的会话。 */
class AuthContextTest {

    /** 测试前后清除当前线程的请求绑定，避免用例之间互相影响。 */
    @BeforeEach
    @AfterEach
    void resetRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    /** 没有 HTTP 请求时，两种身份读取方式均返回未登录错误。 */
    @Test
    void shouldRejectAccessWithoutRequest() {
        BusinessException sessionException = assertThrows(BusinessException.class, AuthContext::getCurrentSession);
        BusinessException userIdException = assertThrows(BusinessException.class, AuthContext::getCurrentUserId);
        assertEquals(ErrorCodeEnum.UNAUTHORIZED, sessionException.getErrorCodeEnum());
        assertEquals(ErrorCodeEnum.UNAUTHORIZED, userIdException.getErrorCodeEnum());
    }

    /** 请求参数和请求头中的身份声明不能替代服务端认证属性。 */
    @Test
    void shouldRejectClientSuppliedIdentity() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter(AuthInterceptor.SESSION_ATTRIBUTE, "1");
        request.addParameter("userId", "1");
        request.addHeader(AuthInterceptor.SESSION_ATTRIBUTE, "1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        BusinessException businessException = assertThrows(BusinessException.class, AuthContext::getCurrentSession);
        assertEquals(ErrorCodeEnum.UNAUTHORIZED, businessException.getErrorCodeEnum());
    }

    /** 错误类型的服务端属性不能作为认证成功的会话返回。 */
    @Test
    void shouldRejectInvalidSessionAttribute() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AuthInterceptor.SESSION_ATTRIBUTE, "invalid-session");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        BusinessException businessException = assertThrows(BusinessException.class, AuthContext::getCurrentSession);
        assertEquals(ErrorCodeEnum.UNAUTHORIZED, businessException.getErrorCodeEnum());
    }

    /** 保留完整会话并准确返回其中的用户 ID，不进行额外身份转换。 */
    @Test
    void shouldReadAuthenticatedSessionAndUserId() {
        AuthSessionDTO authSessionDTO = new AuthSessionDTO(42, "session-id", "refresh-id", AuthClientEnum.USER,
                Instant.now().plusSeconds(60));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AuthInterceptor.SESSION_ATTRIBUTE, authSessionDTO);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertSame(authSessionDTO, AuthContext.getCurrentSession());
        assertEquals(42L, AuthContext.getCurrentUserId());
    }

    /** 同一线程切换到新请求后，不能读取上一请求中的已登录身份。 */
    @Test
    void shouldNotReuseIdentityFromPreviousRequest() {
        AuthSessionDTO previousAuthSessionDTO = new AuthSessionDTO(42, "previous-session", "previous-refresh",
                AuthClientEnum.USER, Instant.now().plusSeconds(60));
        MockHttpServletRequest previousRequest = new MockHttpServletRequest();
        previousRequest.setAttribute(AuthInterceptor.SESSION_ATTRIBUTE, previousAuthSessionDTO);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(previousRequest));
        assertEquals(42L, AuthContext.getCurrentUserId());

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        BusinessException businessException = assertThrows(BusinessException.class, AuthContext::getCurrentSession);
        assertEquals(ErrorCodeEnum.UNAUTHORIZED, businessException.getErrorCodeEnum());
    }
}
