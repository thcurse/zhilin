package com.zhilin.common.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 普通 JSON 接口的统一响应；文件下载、流式响应和 OpenAPI 文档不使用此包装。
 *
 * @param <T> 业务返回类型，通常为 VO；无返回数据时使用 Void
 */
@Getter
@Schema(description = "统一 JSON 响应")
public class Result<T> {

    /** 稳定的结果码，前端依据它判断结果，不依赖提示文案。 */
    @Schema(description = "结果码", example = "SUCCESS")
    private final String code;

    /** 给调用方的安全提示，不包含堆栈或其他内部排查信息。 */
    @Schema(description = "结果说明", example = "操作成功")
    private final String message;

    /** 业务结果；无结果时为 null，空集合仍返回空数组。 */
    @Schema(description = "业务结果，无结果时为 null")
    private final T data;

    /**
     * 构造统一响应字段，由成功或失败工厂方法调用。
     *
     * @param code 稳定的响应码
     * @param message 可公开的响应提示
     * @param <T> 业务返回类型，通常为 VO
     * @param data 业务结果，允许为 null
     */
    private Result(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 包装成功结果。创建资源时，Controller 还需要将 HTTP 状态设置为 201。
     *
     * @param data 业务结果，允许为 null
     * @return 成功响应体
     */
    public static <T> Result<T> success(T data) {
        return new Result<>("SUCCESS", "操作成功", data);
    }

    /**
     * 构造失败响应体，由异常处理器同时设置对应的 HTTP 失败状态。
     *
     * @param code 稳定的大写下划线错误码
     * @param message 可向调用方公开的提示，不能直接传入未知异常的原始消息
     * @return 不包含业务数据的失败响应体
     */
    public static Result<Void> failure(String code, String message) {
        return new Result<>(code, message, null);
    }
}
