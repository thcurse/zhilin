package com.zhilin.common.exception;

import com.zhilin.common.enums.ErrorCodeEnum;
import lombok.Getter;

/**
 * 表示已识别的业务失败，由全局处理器转换为错误码和对应的 HTTP 状态。
 * 未知系统异常应保留原始异常，不能随意包装成业务失败。
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 已定义的业务错误，包含可公开的提示和 HTTP 状态。 */
    private final ErrorCodeEnum errorCodeEnum;

    /**
     * 按服务端已定义的错误创建业务异常，不接受客户端指定错误信息。
     *
     * @param errorCodeEnum 包含公开提示与 HTTP 状态的业务错误枚举
     */
    public BusinessException(ErrorCodeEnum errorCodeEnum) {
        super(errorCodeEnum.getMessage());
        this.errorCodeEnum = errorCodeEnum;
    }
}
