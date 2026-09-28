-- 手动连接目标 MySQL 后执行；仅新建缺失的数据库，不修改旧 zhima 库。
-- 使用前确认目标实例，应用启动不会自动执行此文件。
CREATE DATABASE IF NOT EXISTS zhilin
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS zhilin_test
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
