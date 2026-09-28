# 知邻 Zhilin

面向内容分享与用户交流的社区后端，采用前后端分离的 Spring Boot 单体架构。

目前已实现账号注册登录、JWT 双令牌认证、管理员身份校验、个人资料编辑和头像上传。文章与互动功能开发中。

## 技术栈

Java 17 · Spring Boot 4.1.1 · MyBatis-Plus · MySQL · Redis · SeaweedFS · Knife4j

使用本机 Maven 3.9.16。RabbitMQ 已提供容器配置，业务尚未接入。

## 本地运行

1. 准备 MySQL、Redis 和 S3 兼容对象存储。根目录 `docker-compose.yml` 提供容器配置，使用前需准备其中的外部数据卷、挂载目录及环境变量。
2. 执行 `src/main/resources/db/00-create-databases.sql`，在 `zhilin` 库执行 `01-create-user-account.sql` 和 `03-create-user-profile.sql`。新建库不执行 `02-migrate-account-deleted.sql`。
3. 创建本地配置 `.local/application-dev.yml`，填写数据库和对象存储凭据，以及随机生成的至少 32 字符 JWT 签名密钥：

```yaml
DB_USERNAME: root
DB_PASSWORD: "your-database-password"
AUTH_JWT_SECRET: "replace-with-your-own-random-signing-secret"
OBJECT_STORAGE_ACCESS_KEY: "your-access-key"
OBJECT_STORAGE_SECRET_KEY: "your-secret-key"
```

默认连接本机 MySQL `3308`、Redis `16380`、S3 `18333` 端口；其他地址和参数见 `src/main/resources/application.yml`。配置文件已被 Git 忽略。

在项目根目录启动：

```shell
mvn spring-boot:run
```

默认端口 `8081`，开发环境接口文档：[Knife4j](http://127.0.0.1:8081/doc.html)。本仓库仅包含后端，根路径不提供页面。

## 测试与打包

```shell
mvn test
mvn clean package
java -jar target/zhilin-0.0.1-SNAPSHOT.jar
```

默认测试跳过外部服务集成测试。配置 `.local/application-test.yml` 中的测试连接和凭据，并准备 `zhilin_test` 库及 `zhilin-media-test` 桶后，可运行 `mvn -DinfraTests=true verify`。
