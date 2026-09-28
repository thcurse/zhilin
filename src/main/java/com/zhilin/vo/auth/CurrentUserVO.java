package com.zhilin.vo.auth;

/** 当前账号展示信息，不含密码摘要及任何令牌。 */
public record CurrentUserVO(
        /** 字符串形式的账号编号，避免前端大整数精度丢失。 */
        String id,
        /** 登录名。 */
        String username,
        /** 服务端角色，USER 或 ADMIN。 */
        String role) {
}
