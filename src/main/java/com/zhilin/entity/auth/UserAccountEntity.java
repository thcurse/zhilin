package com.zhilin.entity.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 登录账号及服务端权限，不承担个人资料展示职责。 */
@Getter
@Setter
@TableName("user_account")
public class UserAccountEntity {
    /** 数据库生成的账号主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 登录名，只接受英文字母、数字和下划线。 */
    private String username;
    /** 带算法前缀的密码摘要，禁止作为接口响应。 */
    private String passwordHash;
    /** USER 或 ADMIN，只能由服务端设置。 */
    private String role;
    /** 逻辑删除标记：0 正常，1 已删除；删除后禁止登录、刷新及使用现有访问令牌。 */
    private Integer deleted;
}
