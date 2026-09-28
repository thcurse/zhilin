# 项目开发约束

## 规范入口

- 涉及 Java / Spring Boot 后端的新建、开发、修改、重构或审查时，使用 `java-backend-standard` 技能，按任务读取工程约定和代码编写规范，并检查本次改动。
- 只处理明确授权的范围；讨论或询问设计用途不等于授权修改。不要改动同级的 `zhilin-old`。用户已授权将本项目依赖统一到根目录 Compose 的 zhilin 分组，并将对象存储容器命名为 seaweedFS；复用已有数据卷，其他项目容器不操作，不据此重建 MySQL、Redis、RabbitMQ。
- 项目已实现工程基础、统一响应、开发文档、MySQL/MyBatis-Plus/Redis 接入、账号认证、本人资料编辑及头像上传。账号表为 user_account，资料表为 user_profile；内容与互动业务尚未迁移，不能将预留目录描述成已实现能力。

## 工程结构

- 单 Maven 后端工程，根包 `com.zhilin`，技术分层、层内业务分类。
- Controller → Service → Mapper；Service 接口在 `service/<业务>/`，实现在 `service/<业务>/impl/`。
- exception、enums、util、constant、response 归 common。当前没有 Agent 功能，不预建相关目录；明确开发 Agent 时按需创建 `agent/`、`agent/tool/` 和 `resources/prompts/`。
- 类名体现业务及类型，使用 Controller、Service、ServiceImpl、Mapper、Entity、DTO、VO 等规范后缀。
- 公共配置使用 `application.yml`；Mapper XML 位于 `resources/mapper/<业务>/`；建表、初始化和数据库变更 SQL 脚本统一放在 `resources/db/`，子目录及执行方式按实际需要确定，不默认引入迁移框架。
- 前后端分离，不创建 backend、module、static、templates；只用本机 Maven，不添加 mvnw、mvnw.cmd 或 `.mvn/`。
- 初始化时根据用户要求保留基础空目录。后续业务包与环境配置按需添加，不创建占位类或无用依赖。
- 开发库使用 zhilin，测试库使用 zhilin_test；旧 zhima 库不执行写入或迁移。Redis 开发键使用 zhilin:，测试键使用 zhilin:test:；通过 RedisKeyUtil 显式构造键，字符串读写使用 StringRedisTemplate，复杂对象序列化待实际业务确定。
- 本机连接信息分别保存在 Git 忽略的 `.local/application-dev.yml`、`.local/application-test.yml`，或通过环境变量传入；不将密码写入正式配置。应用不自动执行建库或变更 SQL。
- JWT 签名密钥由 AUTH_JWT_SECRET 提供；访问令牌通过 Bearer 头传递，刷新令牌通过两端独立的 HttpOnly Cookie 传递。Redis 会话键前缀为 auth:session:，刷新原子轮换，退出删除整次会话。
- API 默认要求认证，新增公开入口必须在 AuthInterceptor 中明确声明。管理权限在登录、刷新、访问三个阶段检查；不能信任客户端角色。认证 POST 要求 X-Auth-Request: 1 并校验 Origin，变更跨域或代理配置时同时复核此规则。
- Controller 通过 security/AuthContext 获取当前会话或用户 ID，再显式传给 Service，不重复声明会话请求属性参数。AuthContext 只读取拦截器写入的当前请求属性，不额外维护 ThreadLocal；Service 与异步任务不依赖 HTTP 上下文。

- 数据库逻辑删除统一使用 `deleted INT NOT NULL DEFAULT 0`，Java 字段使用 `Integer`；0 正常、1 已删除。账号删除后禁止登录、刷新和访问；删除标记不承载其他业务状态。
- 根目录 Compose 项目名为 zhilin，统一管理 mysql、redis、rabbitmq、seaweedfs；SeaweedFS 容器名为 seaweedFS。显式复用已有外部卷，保留历史卷名，不删除卷。凭据保存在忽略的 `.local/compose.env` 和本机配置，开发桶 zhilin-media、测试桶 zhilin-media-test。RabbitMQ 按需启动，复用数据时保持原主机名。
- 本人资料从 AuthSessionDTO 获取所有者；文字保存与头像保存分列更新，首次保存才创建资料，读取不产生数据库写入。邮箱仅本人可见，头像公开读取仅放行明确映射 /api/avatars/{avatarKey}。

## 接口与代码

- 普通接口前缀 `/api`，管理接口前缀 `/api/admin`；URL 最多一个路径参数，且只能放在结尾。
- 普通 JSON 响应使用 `Result<T>`，字段为 code、message、data；正确使用 HTTP 状态码。
- Controller 显式返回 `Result<VO>`，不使用全局自动包装；文件、流式响应与 OpenAPI 文档不套 Result。已知业务失败抛 BusinessException，未知异常交给全局处理器记录并返回安全提示。
- DTO 使用 Jakarta Validation 约束和中文提示，Controller 请求体使用 `@Valid`；直接参数约束使用 Spring MVC 自带方法校验，不为此在 Controller 上添加类级 `@Validated`。
- 接口文档使用 Knife4j（/doc.html），由 springdoc 提供 OpenAPI 及分组发现；页面资源和 OpenAPI 仅在 dev 环境默认开启。验收用 Controller 放测试源码并使用 `@TestComponent` 隔离，通过测试配置显式导入，不添加正式演示接口。
- Entity 不直接作为接口请求或响应；业务请求使用 DTO，业务响应使用 VO。
- 使用有信息量的中文注释说明类型、业务方法、字段与关键业务变量；短参数列表保持一行。
- 超过 100 行有效代码的业务方法需审查职责，不为压低行数机械拆分。
- Java 局部变量使用明确类型，不使用 `var`，让读者直接看出业务类型。
- 参数、局部变量和成员变量按通用 Skill 保留业务对象角色后缀。业务方法含实现方法写完整中文 Javadoc，每个参数写 @param，非 void 写 @return，明确业务异常写 @throws。
- 不吞异常，不把失败包装为成功，不在查询和转换过程中隐藏业务写入。

## 本地产物与验证

- 日志、临时文件、报告分别放 `.local/logs/`、`.local/tmp/`、`.local/reports/`；Maven 产物保留 `target/`。
- 不在根目录生成任务计划、总结、脚本或日志等临时材料；敏感配置不提交。
- README 面向开源项目使用者，只保留项目介绍、环境要求、构建运行、配置和公开接口文档等使用说明。阶段计划、实现讲解、代码阅读顺序、个人学习与验收步骤统一放 `.local/reports/`，不写入 README。
- 使用本机 `mvn test` 测试，`mvn spring-boot:run` 启动，`mvn clean package` 打包。
- 按实际改动运行必要检查。目录或文档调整不机械增加测试；未执行的检查不得宣称通过。
- `mvn test` 默认跳过外部基础设施测试；显式执行 `mvn -DinfraTests=true verify` 才验收真实 MySQL/Redis。测试写入前检查目标库和前缀，只操作自身临时表或随机测试键，不使用 FLUSHDB/FLUSHALL、全库清理或清空业务表。
