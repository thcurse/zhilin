-- 仅用于仍有 enabled 字段的旧版 user_account，执行前停止后端。
-- 连接参数必须明确选择 zhilin 或 zhilin_test；新建库只执行 01，不执行此脚本。
-- 一次性变更，不可重复执行；先转换状态，再移除旧字段，账号与密码保持不变。
ALTER TABLE user_account
    ADD COLUMN deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 正常，1 已删除' AFTER role;

UPDATE user_account SET deleted = CASE WHEN enabled = 1 THEN 0 ELSE 1 END;

ALTER TABLE user_account DROP COLUMN enabled;
