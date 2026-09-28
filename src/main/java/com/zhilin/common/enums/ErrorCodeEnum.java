package com.zhilin.common.enums;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 当前公共错误及对应 HTTP 状态；后续业务错误按实际需求新增，枚举名称即对外错误码。
 */
@Getter
public enum ErrorCodeEnum {

    /** 参数缺失、格式错误或约束校验失败。 */
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "请求参数不合法"),
    /** 接口地址或业务资源不存在。 */
    NOT_FOUND(HttpStatus.NOT_FOUND, "请求的资源不存在"),
    /** 地址存在，但不支持当前 HTTP 方法。 */
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "请求方法不支持"),
    /** 服务无法返回 Accept 请求头要求的媒体类型。 */
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "不支持请求指定的响应类型"),
    /** 当前业务状态不允许执行操作。 */
    CONFLICT(HttpStatus.CONFLICT, "当前状态不允许此操作"),
    /** 上传内容超过服务允许的大小。 */
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "请求内容超过大小限制"),
    /** 请求体的 Content-Type 不受接口支持。 */
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "请求内容类型不支持"),
    /** 未预期的内部错误；具体原因仅记录到服务端日志。 */
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务内部异常，请稍后重试"),
    /** 服务暂时无法完成请求，例如异步请求超时。 */
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "服务暂时不可用，请稍后重试"),
    /** 凭证缺失、过期、被撤销或校验失败。 */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录"),
    /** 已识别身份，但无权执行当前操作。 */
    FORBIDDEN(HttpStatus.FORBIDDEN, "没有执行此操作的权限"),
    /** 用户名或密码不匹配，不向外区分账号是否存在。 */
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "用户名或密码错误"),
    /** 唯一索引保证并发注册时也不能产生同名账号。 */
    USERNAME_EXISTS(HttpStatus.CONFLICT, "用户名已被使用"),
    /** 头像不是可解码的 PNG/JPEG，或尺寸超过 2048×2048。 */
    INVALID_AVATAR(HttpStatus.BAD_REQUEST, "请选择尺寸不超过 2048×2048 的有效 PNG 或 JPEG 图片");

    /** 与错误含义对应的 HTTP 状态，不能把业务失败统一返回为 200。 */
    private final HttpStatus httpStatus;

    /** 默认中文提示，不包含敏感排查信息。 */
    private final String message;

    /**
     * 定义一个稳定错误码对应的 HTTP 状态与公开提示。
     *
     * @param httpStatus 与错误语义对应的 HTTP 状态
     * @param message 可向调用方公开的默认中文提示
     */
    ErrorCodeEnum(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
