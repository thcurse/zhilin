# Zhilin

知邻社区后端，采用前后端分离的 Spring Boot 单 Maven 工程。

项目已提供用户名注册、密码登录、JWT 双令牌续期、会话注销、管理员身份校验、本人资料编辑及头像上传，使用 MySQL、MyBatis-Plus、Redis 和 S3 兼容对象存储。内容与互动业务正在逐步接入。

## 环境要求

- JDK 17
- Maven 3.9.16（本机安装）
- Spring Boot 4.1.1
- MySQL（已验证 8.4.9）
- Redis（已验证 8.6.2）
- S3 兼容对象存储（已验证 SeaweedFS 4.40；提供 Docker Compose 配置）

以下命令均在项目根目录执行。

## 数据服务准备

准备可访问的 MySQL 和 Redis。使用具有建库权限的 MySQL 账号，在目标实例执行 `src/main/resources/db/00-create-databases.sql`，创建 `zhilin` 开发库和 `zhilin_test` 测试库。应用不会自动建库或改表。

分别选中开发库和测试库，执行 `src/main/resources/db/01-create-user-account.sql` 和 `03-create-user-profile.sql` 创建账号及资料表。`02-migrate-account-deleted.sql` 仅供仍有 enabled 字段的已有账号表执行一次，新建库不要执行。不要在旧业务库中执行。

通过环境变量配置连接，或创建不提交 Git 的 `.local/application-dev.yml`：

```yaml
DB_USERNAME: your_user
DB_PASSWORD: "your_password"
# 使用安全随机生成的至少 32 个字符的密钥；不要提交真实值。
AUTH_JWT_SECRET: "replace-with-your-own-random-signing-secret"
```

文件中的键作为下表环境变量的本地默认值，同名环境变量可覆盖。默认连接本机 MySQL 3308 端口及 Redis 16380 端口，其他地址通过下表参数配置。

## 构建与运行

使用 Maven 启动：

```shell
mvn spring-boot:run
```

或构建可执行 JAR 后运行：

```shell
mvn clean verify
java -jar target/zhilin-0.0.1-SNAPSHOT.jar
```

默认使用 `dev` 环境，监听 `8081` 端口。终端运行时按 `Ctrl+C` 停止。

IDEA 用户可导入 `pom.xml`，选择 JDK 17 和本机 Maven，运行 `ZhilinApplication.main`。运行配置的工作目录设为项目根目录，停止时点击运行窗口的停止按钮。

单独执行测试：

```shell
mvn test
```

默认测试不连接外部数据服务，跳过基础设施集成测试。运行真实 MySQL、Redis、S3 验证前，配置 `TEST_DB_USERNAME`、`TEST_DB_PASSWORD` 及对象存储凭据，或在 `.local/application-test.yml` 中提供本地配置，再执行：

```shell
mvn -DinfraTests=true verify
```

集成测试使用 `zhilin_test` 的连接级临时表及 `zhilin:test:` 下带过期时间的随机 Redis 键，并清理本次数据。测试使用独立的 `TEST_DB_URL`、`TEST_REDIS_HOST`、`TEST_REDIS_PORT`、`TEST_REDIS_USERNAME`、`TEST_REDIS_PASSWORD`、`TEST_REDIS_DATABASE` 连接参数，默认地址与开发配置相同，但数据库固定为 `zhilin_test`；误连其他数据库时测试在写入前失败。

账号、资料测试只清理本次随机创建的数据；头像测试固定使用 `zhilin-media-test` 桶，只删除本次上传的精确对象键，不清空桶。

## 配置

公共配置位于 `src/main/resources/application.yml`，开发配置位于 `application-dev.yml`。

| 环境变量 | 默认值 | 用途 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | 未设置时使用 `dev` | 选择运行环境，当前提供 dev 环境配置 |
| `SERVER_PORT` | `8081` | HTTP 端口 |
| `LOG_DIR` | `.local/logs` | 日志目录，支持绝对路径 |
| `DB_URL` | 本机 3308 端口的 zhilin 库 | MySQL JDBC URL，完整默认值见 application.yml |
| `DB_USERNAME` / `DB_PASSWORD` | root / 空 | MySQL 账号及密码，运行前按实例配置 |
| `REDIS_HOST` / `REDIS_PORT` | 127.0.0.1 / 16380 | Redis 地址及端口 |
| `REDIS_USERNAME` / `REDIS_PASSWORD` | 空 | Redis 认证信息，无认证时不设置 |
| `REDIS_DATABASE` | 0 | Redis 逻辑库编号 |
| `REDIS_KEY_PREFIX` | `zhilin:` | Redis 业务键命名空间 |
| `AUTH_JWT_SECRET` | 无，必填 | 高熵 JWT 签名密钥，至少 32 个字符 |
| `OBJECT_STORAGE_ENDPOINT` | `http://127.0.0.1:18333` | S3 API 地址 |
| `OBJECT_STORAGE_REGION` | `us-east-1` | S3 签名区域 |
| `OBJECT_STORAGE_BUCKET` | `zhilin-media` | 开发环境私有桶 |
| `OBJECT_STORAGE_ACCESS_KEY` / `OBJECT_STORAGE_SECRET_KEY` | 无 | 对象存储访问凭据，缺失时存储接口返回 503 |
| `ZHILIN_AUTH_ALLOWED_ORIGINS` | dev 为本机 3301、3302、8081 | 允许发起认证请求的完整前端 Origin，多个值用逗号分隔 |

也可以通过命令行参数覆盖端口：

```shell
java -jar target/zhilin-0.0.1-SNAPSHOT.jar --server.port=8082
```

相对日志路径以进程工作目录为基准。敏感配置应通过环境变量或不提交的本地配置提供。

开发环境只加载 `.local/application-dev.yml`；集成测试只加载 `.local/application-test.yml`。这些文件不会打包进 JAR。Redis 键前缀用于命名隔离，不替代服务端权限控制；业务代码通过 `RedisKeyUtil` 构造键，再使用 `StringRedisTemplate` 读写字符串。

## 接口文档

默认 dev 环境启动后访问：

- [Knife4j](http://127.0.0.1:8081/doc.html)
- [OpenAPI JSON](http://127.0.0.1:8081/v3/api-docs)

文档仅在 dev 环境默认开启，提供用户认证、管理员认证、个人资料及头像接口。服务不提供前端页面，访问根路径返回 HTTP 404。

普通 JSON 接口采用 `code`、`message`、`data` 响应结构，HTTP 状态码表示请求结果类别，`code` 表示具体业务结果。文件、流式响应及 OpenAPI 文档遵循各自协议。

## 账号与认证

- 用户接口位于 `/api/auth`，管理接口位于 `/api/admin/auth`。两端均提供 `POST /login`、`POST /refresh`、`GET /me`、`POST /logout`；用户端额外提供 `POST /register`。
- 注册成功返回 201，不自动登录。用户名为 4 至 32 位字母、数字或下划线，不区分大小写；密码为 8 至 64 位非空格 ASCII 字符，包含字母和数字。重复用户名返回 409。
- 所有认证 POST 请求必须携带 `X-Auth-Request: 1`。浏览器 Origin 必须在允许列表中；前端通过同源反向代理调用 API，后端不开放 CORS。
- 访问令牌有效期默认 15 分钟，通过 `Authorization: Bearer <accessToken>` 传递。刷新令牌通过 HttpOnly、SameSite=Strict Cookie 传递，用户端与管理端分别保存。
- 会话最长 7 天，刷新时轮换刷新令牌但不延长绝对期限。同一刷新令牌只能成功使用一次；客户端需合并并发刷新，失败后不无限重试。
- 退出会删除当前 Redis 会话，使该会话的访问和刷新令牌一同失效；其他设备和另一端的会话不受影响。账号逻辑删除、管理员降权在后续请求中生效。
- 凭证错误或过期返回 401，角色不足或账号逻辑删除返回 403，数据服务故障返回 503。密码使用带随机盐的 BCrypt，旧 MD5 密码不能直接复制使用。
- HTTPS 部署保留 `zhilin.auth.cookie-secure=true`，并设置实际前端 Origin；仅本机 dev 配置为 HTTP 关闭 Secure。不要把 dev 配置用于公开部署。

首次管理员初始化：设置 `ADMIN_USERNAME`、`ADMIN_PASSWORD` 后，额外传入 `--zhilin.bootstrap.admin-enabled=true` 启动一次。初始化参数遵循注册约束；已有同名账号会报冲突，不覆盖密码、不提升已有普通用户权限。成功后关闭初始化开关，移除初始化环境变量，再按常规方式启动。项目不提供默认管理员密码或公开管理员注册接口。

## 个人资料与头像

- `GET /api/users/me/profile` 获取本人资料；`PUT /api/users/me/profile` 整体保存昵称、简介、公司、职位、邮箱，均需 Bearer 凭证。昵称必填，可选字段省略或为 null 时清空，头像保持不变。
- 资料所有者由会话确定；登录名和角色不通过资料接口修改。邮箱仅本人可见，尚未验证且不作为登录凭据。
- `POST /api/users/me/avatar` 上传并设置本人头像，需 Bearer 凭证，使用 multipart 文件字段 `file`。只接受不超过 2MB、宽高均不超过 2048 像素的 PNG/JPEG，存储时最长边缩至 512 像素并重新编码为 PNG。
- `GET /api/avatars/{avatarKey}` 返回公开头像的 PNG 内容；只开放头像读取，S3 桶本身保持私有。
- 替换头像会产生新对象，不立即删除旧对象；对象上传成功但资料保存失败时会记录对象键供核对。目前没有自动清理未引用对象的任务。

## 本机容器

根目录 `docker-compose.yml` 将 MySQL、Redis、RabbitMQ、SeaweedFS 统一放在 `zhilin` 项目中。SeaweedFS 的服务名为 `seaweedfs`，容器名为 `seaweedFS`。创建 Git 忽略的 `.local/compose.env`，填写以下键，值使用自行生成的凭据，不提交到仓库：

```dotenv
MYSQL_ROOT_PASSWORD=your-mysql-password
MYSQL_DATABASE=zhilin
RABBITMQ_DEFAULT_USER=zhilin
RABBITMQ_DEFAULT_PASS=your-rabbitmq-password
RABBITMQ_HOSTNAME=zhilin-rabbitmq
OBJECT_STORAGE_ACCESS_KEY=your-access-key
OBJECT_STORAGE_SECRET_KEY=your-random-secret-key
OBJECT_STORAGE_ADMIN_PASSWORD=your-random-admin-password
```

```shell
docker compose --env-file .local/compose.env up -d --no-recreate mysql redis seaweedfs
docker compose --env-file .local/compose.env ps -a
```

RabbitMQ 按需启动，停止命令可指定一个或多个服务：

```shell
docker compose --env-file .local/compose.env up -d --no-recreate rabbitmq
docker compose --env-file .local/compose.env stop seaweedfs
```

配置显式引用外部数据卷 `zhima_mysql-data`、`zhima_redis-data`、`zhilin_rabbitmq-data`、`zhilin-storage_object-data`，保留名称以复用已有数据。首次在新机器使用时，先分别通过 `docker volume create <卷名>` 创建这些卷，或修改 Compose 指向准备好的外部卷；MySQL 配置和 Redis 日志的绑定目录也需按机器情况调整。复用 RabbitMQ 数据时，`RABBITMQ_HOSTNAME` 必须保持原节点的主机名。已有数据库和消息队列不会因修改环境文件而自动更改账号密码。

S3 API 为 `http://127.0.0.1:18333`，管理页面为 `http://127.0.0.1:12346`，管理用户名为 `admin`。SeaweedFS 端口只绑定本机；文件保存在上述存储卷中。启动时创建 `zhilin-media` 和 `zhilin-media-test` 两个私有桶。

Compose 环境文件不会自动传给 Java。后端需配置相同的 `OBJECT_STORAGE_ACCESS_KEY`、`OBJECT_STORAGE_SECRET_KEY`，或在本机开发和测试 YAML 中设置 `zhilin.storage.access-key`、`secret-key`；测试桶固定为 `zhilin-media-test`。部署时自行配置实际地址、访问控制、持久卷和备份。

## 日志

- 控制台与文件同时输出，使用 UTF-8 编码。
- 默认文件：`.local/logs/zhilin.log`。
- 按日期和大小滚动，单文件阈值为 `10MB`。
- 归档保留 7 天，总量上限为 `100MB`，不包含当前日志文件。
- 公共日志级别为 `INFO`，dev 环境项目包日志级别为 `DEBUG`。

## 目录

```text
zhilin/
├── pom.xml
├── docker-compose.yml        MySQL、Redis、RabbitMQ、SeaweedFS
├── src/
│   ├── main/
│   │   ├── java/com/zhilin/     Java 源码
│   │   └── resources/          应用配置及资源
│   └── test/                  测试源码及资源
└── target/                    Maven 构建产物
```
