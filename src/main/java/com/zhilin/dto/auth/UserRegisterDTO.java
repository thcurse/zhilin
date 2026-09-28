package com.zhilin.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 普通用户注册参数；账号创建后需单独登录。 */
@Getter
@Setter
public class UserRegisterDTO {
    /** 登录名不区分大小写，数据库保存为小写。 */
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "[a-zA-Z0-9_]{4,32}", message = "用户名需为 4 至 32 位字母、数字或下划线")
    private String username;
    /** 8 至 64 位可打印 ASCII，含字母与数字；限制字节长度以避免 BCrypt 截断。 */
    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "(?=.*[a-zA-Z])(?=.*[0-9])[\\x21-\\x7E]{8,64}",
            message = "密码需为 8 至 64 位非空格英文字符，且包含字母和数字")
    private String password;
}
