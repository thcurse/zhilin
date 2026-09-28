package com.zhilin.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 整体替换本人的文字资料；不允许指定用户编号、账号角色或头像存储位置。 */
@Getter
@Setter
@Schema(description = "修改本人文字资料；可选字段省略或为 null 时清空，头像通过上传接口修改")
public class UserProfileUpdateDTO {
    /** 展示昵称必填，前后空白会在保存时移除。 */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 32, message = "昵称不能超过 32 个字符")
    private String nickname;
    /** 纯文本简介，省略或 null 表示清空。 */
    @Size(max = 500, message = "简介不能超过 500 个字符")
    private String bio;
    /** 公司名称，省略或 null 表示清空。 */
    @Size(max = 100, message = "公司名称不能超过 100 个字符")
    private String company;
    /** 职位名称，省略或 null 表示清空。 */
    @Size(max = 100, message = "职位名称不能超过 100 个字符")
    private String position;
    /** 联系邮箱，允许留空；保存不代表通过邮箱验证。 */
    @Email(message = "邮箱格式不正确")
    @Size(max = 254, message = "邮箱不能超过 254 个字符")
    private String email;
}
