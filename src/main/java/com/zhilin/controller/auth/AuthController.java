package com.zhilin.controller.auth;

import com.zhilin.common.enums.AuthClientEnum;
import com.zhilin.common.exception.BusinessException;
import com.zhilin.common.response.Result;
import com.zhilin.dto.auth.AuthSessionDTO;
import com.zhilin.dto.auth.AuthTokenPairDTO;
import com.zhilin.dto.auth.PasswordLoginDTO;
import com.zhilin.dto.auth.UserRegisterDTO;
import com.zhilin.security.AuthCookieUtil;
import com.zhilin.security.AuthContext;
import com.zhilin.service.auth.AuthService;
import com.zhilin.vo.auth.AuthTokenVO;
import com.zhilin.vo.auth.CurrentUserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 用户注册与登录入口；Cookie 只在 Controller 写入，不让服务层依赖 HTTP。 */
@Tag(name = "用户注册与登录")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final AuthCookieUtil authCookieUtil;

    /**
     * 注册普通用户，成功后需另外调用登录接口建立会话。
     *
     * @param userRegisterDTO 已通过入口校验的注册参数，包含登录名和原始密码
     * @return 包含新账号公开信息的成功响应，HTTP 状态为 201
     * @throws BusinessException 用户名已被使用
     */
    @Operation(summary = "注册普通用户", description = "用户名不区分大小写；重名 409，参数错误 400。")
    @Parameter(name = "X-Auth-Request", in = ParameterIn.HEADER, required = true, example = "1")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<CurrentUserVO> register(@Valid @RequestBody UserRegisterDTO userRegisterDTO) {
        return Result.success(authService.register(userRegisterDTO));
    }

    /**
     * 校验登录凭据并建立会话，将刷新令牌写入当前端的 HttpOnly Cookie。
     *
     * @param passwordLoginDTO 用户名和原始密码，由请求体校验约束
     * @param response 用于写入刷新 Cookie 的 HTTP 响应
     * @return 访问令牌、有效秒数和当前身份，不在 JSON 中暴露刷新令牌
     * @throws BusinessException 凭据错误、账号已删除或不具备当前端权限
     */
    @Operation(summary = "用户注册与登录：用户名密码登录")
    @Parameter(name = "X-Auth-Request", in = ParameterIn.HEADER, required = true, example = "1")
    @PostMapping("/login")
    public Result<AuthTokenVO> login(@Valid @RequestBody PasswordLoginDTO passwordLoginDTO,
                                     HttpServletResponse response) {
        AuthTokenPairDTO authTokenPairDTO = authService.login(passwordLoginDTO, AuthClientEnum.USER);
        authCookieUtil.write(response, AuthClientEnum.USER, authTokenPairDTO);
        return Result.success(authTokenPairDTO.authTokenVO());
    }

    /**
     * 获取拦截器确认的当前身份，并再次检查数据库中的账号状态。
     *
     * @return 当前账号的公开信息
     * @throws BusinessException 未登录、账号不存在、已逻辑删除或不具备当前端的访问权限
     */
    @Operation(summary = "获取当前用户", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/me")
    public Result<CurrentUserVO> currentUser() {
        AuthSessionDTO authSessionDTO = AuthContext.getCurrentSession();
        return Result.success(authService.currentUser(authSessionDTO));
    }

    /**
     * 读取当前端的刷新 Cookie，原子轮换令牌并更新 Cookie。
     *
     * @param request 携带当前端刷新 Cookie 的 HTTP 请求
     * @param response 用于写入轮换后刷新 Cookie 的 HTTP 响应
     * @return 新的访问令牌、有效秒数和当前身份
     * @throws BusinessException 刷新凭证缺失、无效、重复使用，或账号无权限
     */
    @Operation(summary = "刷新双令牌", description = "自动读取 HttpOnly Cookie，同一刷新令牌只能使用一次。")
    @Parameter(name = "X-Auth-Request", in = ParameterIn.HEADER, required = true, example = "1")
    @PostMapping("/refresh")
    public Result<AuthTokenVO> refresh(HttpServletRequest request, HttpServletResponse response) {
        AuthTokenPairDTO authTokenPairDTO = authService.refresh(
                authCookieUtil.read(request, AuthClientEnum.USER), AuthClientEnum.USER);
        authCookieUtil.write(response, AuthClientEnum.USER, authTokenPairDTO);
        return Result.success(authTokenPairDTO.authTokenVO());
    }

    /**
     * 撤销当前会话后清除当前端的刷新 Cookie，不影响其他设备的会话。
     *
     * @param response 用于清除当前端刷新 Cookie 的 HTTP 响应
     * @return data 为 null 的成功响应
     * @throws BusinessException 当前请求没有有效认证会话
     */
    @Operation(summary = "退出当前会话", security = @SecurityRequirement(name = "bearerAuth"))
    @Parameter(name = "X-Auth-Request", in = ParameterIn.HEADER, required = true, example = "1")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletResponse response) {
        AuthSessionDTO authSessionDTO = AuthContext.getCurrentSession();
        authService.logout(authSessionDTO);
        authCookieUtil.clear(response, AuthClientEnum.USER);
        return Result.success(null);
    }
}
