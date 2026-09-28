package com.zhilin.common.enums;

/** 区分用户端与管理端会话，防止一端的凭证被另一端误用。 */
public enum AuthClientEnum {
    /** 社区用户端。 */
    USER,
    /** 管理端，还必须校验数据库中的 ADMIN 角色。 */
    ADMIN
}
