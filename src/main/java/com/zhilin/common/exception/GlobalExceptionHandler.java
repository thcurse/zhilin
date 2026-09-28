package com.zhilin.common.exception;

import com.zhilin.common.enums.ErrorCodeEnum;
import com.zhilin.common.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 统一处理进入 Spring MVC 的异常，保留框架的 HTTP 状态及 Allow 等协议响应头。
 * 认证过滤器等 MVC 之外的异常需要在对应入口处理，不由本类兜底。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * 记录基础设施故障并返回服务不可用，避免误报为账号密码错误。
     *
     * @param exception 数据库或 Redis 访问产生的异常
     * @return HTTP 503 与安全的统一错误响应
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Result<Void>> handleDataAccessException(DataAccessException exception) {
        log.error("账号或基础设施访问失败", exception);
        ErrorCodeEnum errorCodeEnum = ErrorCodeEnum.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(errorCodeEnum.getHttpStatus())
                .body(Result.failure(errorCodeEnum.name(), errorCodeEnum.getMessage()));
    }

    /**
     * 将已知业务异常转换为约定错误码及 HTTP 状态，不重复记录预期失败的堆栈。
     *
     * @param exception 包含已定义错误枚举的业务异常
     * @return 与业务错误含义一致的 HTTP 状态和统一响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException exception) {
        ErrorCodeEnum errorCodeEnum = exception.getErrorCodeEnum();
        return ResponseEntity.status(errorCodeEnum.getHttpStatus())
                .body(Result.failure(errorCodeEnum.name(), errorCodeEnum.getMessage()));
    }

    /**
     * 记录未知异常完整堆栈，对客户端仅返回通用服务错误。
     *
     * @param exception 未被其他处理方法识别的异常
     * @return HTTP 500 与不包含内部细节的错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpectedException(Exception exception) {
        log.error("处理请求时发生未预期的异常", exception);
        ErrorCodeEnum errorCodeEnum = ErrorCodeEnum.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(errorCodeEnum.getHttpStatus())
                .body(Result.failure(errorCodeEnum.name(), errorCodeEnum.getMessage()));
    }

    /**
     * 统一 Spring MVC 框架异常的响应体，保留原 HTTP 状态和协议响应头。
     *
     * @param exception Spring MVC 捕获的框架异常
     * @param body 框架提供的原始响应体，此处替换为安全的统一响应
     * @param headers 需要保留的协议响应头，例如 Allow
     * @param status 框架确定的 HTTP 状态
     * @param request 框架请求上下文，用于检查响应提交状态及维护错误属性
     * @return 统一错误响应；响应已提交时遵循父类约定返回 null
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Result<Void> result = createFrameworkFailure(exception, status);
        if (status.is5xxServerError()) {
            log.error("Spring MVC 处理请求失败，HTTP 状态：{}", status.value(), exception);
        }
        // 交给父类检查响应是否已经提交，并保留框架需要的错误上下文。
        return super.handleExceptionInternal(exception, result, headers, status, request);
    }

    /**
     * 按框架状态生成安全提示，参数错误只返回约束消息，不暴露请求原文。
     *
     * @param exception 产生当前状态的框架异常
     * @param status 框架已确定的 HTTP 状态
     * @return 不包含内部排查细节的统一失败响应
     */
    private Result<Void> createFrameworkFailure(Exception exception, HttpStatusCode status) {
        if (status.value() == 400) {
            String message = ErrorCodeEnum.BAD_REQUEST.getMessage();
            if (exception instanceof MethodArgumentNotValidException validationException) {
                // 仅取约束消息，不返回 BindingResult 中的被拒绝值或完整 DTO。
                message = validationException.getBindingResult().getAllErrors().stream()
                        .map(MessageSourceResolvable::getDefaultMessage)
                        .filter(Objects::nonNull)
                        .distinct()
                        .sorted()
                        .collect(Collectors.joining("；"));
            } else if (exception instanceof HandlerMethodValidationException validationException) {
                message = validationException.getAllErrors().stream()
                        .map(MessageSourceResolvable::getDefaultMessage)
                        .filter(Objects::nonNull)
                        .distinct()
                        .sorted()
                        .collect(Collectors.joining("；"));
            } else if (exception instanceof HttpMessageNotReadableException) {
                message = "请求体缺失或 JSON 格式错误";
            }
            if (message.isBlank()) {
                message = ErrorCodeEnum.BAD_REQUEST.getMessage();
            }
            return Result.failure(ErrorCodeEnum.BAD_REQUEST.name(), message);
        }

        for (ErrorCodeEnum errorCodeEnum : ErrorCodeEnum.values()) {
            if (errorCodeEnum.getHttpStatus().value() == status.value()) {
                return Result.failure(errorCodeEnum.name(), errorCodeEnum.getMessage());
            }
        }
        // 框架可能产生未在公共枚举中列出的状态，保留状态并使用稳定的通用错误码。
        return Result.failure("HTTP_" + status.value(), "请求未能完成");
    }
}
