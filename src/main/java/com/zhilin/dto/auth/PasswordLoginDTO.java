package com.zhilin.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 用户名密码登录参数；不接受客户端声明的角色或用户编号。 */
@Getter
@Setter
public class PasswordLoginDTO {
    /** 不区分大小写的登录名。 */
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "[a-zA-Z0-9_]{4,32}", message = "用户名需为 4 至 32 位字母、数字或下划线")
    @Schema(description = "登录名，4 至 32 位字母、数字或下划线")
    private String username;
    /** 原始密码仅用于本次校验，不能写入日志。 */
    @NotBlank(message = "密码不能为空")
    @Size(max = 64, message = "密码不能超过 64 个字符")
    @Schema(description = "密码", format = "password")
    private String password;
}
