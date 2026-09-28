-- 在连接参数中明确选择 zhilin 或 zhilin_test；应用不会自动执行此脚本。
-- 资料在用户首次保存时创建，读取接口不补写记录，不修改既有账号和密码。
CREATE TABLE IF NOT EXISTS user_profile (
    user_id BIGINT NOT NULL COMMENT '所属账号主键，一名用户仅有一份资料',
    nickname VARCHAR(32) NOT NULL COMMENT '社区展示昵称，与登录名分开',
    bio VARCHAR(500) NOT NULL DEFAULT '' COMMENT '个人简介，仅按纯文本展示',
    company VARCHAR(100) NOT NULL DEFAULT '' COMMENT '公司名称，可留空',
    position VARCHAR(100) NOT NULL DEFAULT '' COMMENT '职位名称，可留空',
    email VARCHAR(254) NOT NULL DEFAULT '' COMMENT '本人资料邮箱，尚未验证且不用于登录',
    avatar_key VARCHAR(64) DEFAULT NULL COMMENT '服务端生成的头像存储标识，不接受客户端指定路径',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 正常，1 已删除',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次保存时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近修改时间',
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户个人资料，与登录账号分离';
