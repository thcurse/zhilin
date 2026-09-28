package com.zhilin.dto.auth;

import com.zhilin.common.enums.AuthClientEnum;
import java.time.Instant;

/** 已通过签名校验的令牌声明，仅在服务端内部传递。 */
public record AuthSessionDTO(
        /** 令牌对应的账号主键。 */
        long userId,
        /** 同一对令牌共享的会话编号，注销时按此撤销。 */
        String sessionId,
        /** 本次刷新令牌编号，用于防止重复刷新。 */
        String refreshId,
        /** 会话所属端。 */
        AuthClientEnum authClientEnum,
        /** 会话绝对截止时间，刷新不会无限延长登录。 */
        Instant sessionExpiresAt) {
}
