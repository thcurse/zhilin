-- 在连接参数中明确选择 zhilin 或 zhilin_test；应用不会自动执行此脚本。
CREATE TABLE IF NOT EXISTS user_account (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '账号主键',
    username VARCHAR(32) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL COMMENT '登录名，不区分大小写',
    password_hash VARCHAR(100) NOT NULL COMMENT '带算法标识的密码摘要，不保存明文',
    role VARCHAR(16) NOT NULL DEFAULT 'USER' COMMENT 'USER 普通用户，ADMIN 管理员',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 正常，1 已删除',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，数据库会话时区',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_account_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录账号，个人资料后续独立维护';
