# 变更日志

本文件记录 GameExchange 已经实施的工程化变更。项目当前没有正式版本发布记录，因此现阶段统一记录在 `Unreleased` 下。

记录原则：

- 只记录已经实施并完成相关验证的变化。
- 不补写无法确认的早期开发历史。
- 不在变更记录中保存凭据、服务器地址或其他敏感信息。
- 正式发布版本后，再将对应内容从 `Unreleased` 移入带日期的版本章节。

## [Unreleased]

### 新增

- **Milestone 1.1**：新增 `CODE_REVIEW.md`，记录项目模块关系、数据库关系、接口清单、技术债和 Docker 就绪度。
- **Milestone 1.2**：新增 `.gitignore` 和 `.gitattributes`，明确构建产物、本地配置、文本编码和换行符边界。
- **Milestone 1.3**：新增 `src/main/resources/db.properties`，保存非敏感数据库与 Druid 连接池默认值。
- **Milestone 1.5**：新增根目录 `README.md`，记录项目用途、运行前提、配置、构建、Tomcat 部署和当前限制。
- **Milestone 1.6**：新增 `docs/README.md` 文档索引及本变更日志。
- **Phase 1.5 / Milestone 1**：新增 `database/schema.sql` 与 `database/seed.sql`，提供 MySQL 8.4 数据库结构基线和最小可重复种子数据。
- **Phase 1.5 / Milestone 2**：新增 `vue/assets/js/app-config.js`，集中计算前端 `BASE_PATH` 并生成 API 地址。
- **Phase 1.5 / Milestone 3**：新增 `mvnw`、`mvnw.cmd` 和 `.mvn/wrapper/maven-wrapper.properties`，通过 `only-script` 模式固定使用 Maven 3.9.16 并校验分发包 SHA-256。
- **Phase 1.5 / Milestone 4**：新增 `SIMULATOR_ENABLED` 环境变量和 `simulator.enabled` JVM 系统属性，用于控制游戏模拟器启动。

### 变更

- **Milestone 1.3**：数据库配置支持 JVM 系统属性、环境变量和资源文件三级来源，优先级为 JVM 系统属性、环境变量、`db.properties`。
- **Milestone 1.3**：数据库连接初始化增加必要配置校验，缺少用户名、密码等必要值时快速失败。
- **Milestone 1.4**：11 个 Java 类统一使用 `java.util.logging`，按 `INFO`、`WARNING`、`SEVERE` 和 `FINE` 区分日志级别。
- **Milestone 1.4**：Java 源码中的 `System.out`、`System.err` 和 `printStackTrace()` 已清理完成。
- **Milestone 1.6**：根目录 README 增加文档索引和变更日志入口。
- **Phase 1.5 / Milestone 2**：五个前端页面移除固定 `/GameExchange_war` 的 `<base>` 和重复 `GE_API_BASE`，支持任意 WAR Context Path 及 ROOT 部署。
- **Phase 1.5 / Milestone 3**：项目构建入口改为 Maven Wrapper，并为 Linux/macOS 的 `mvnw` 固定 LF 换行符。
- **Phase 1.5 / Milestone 4**：游戏模拟器改为默认关闭，只有开关明确为 `true` 时监听器才会创建模拟器并访问数据库；无效配置按关闭处理。

### 移除

- **Milestone 1.3**：移除源码中的旧 `druid.properties`，避免继续把数据库凭据作为资源文件打包。

### 安全

- **Milestone 1.3**：数据库用户名和密码改为通过运行环境注入，不再保存在当前源码配置中。
- **Milestone 1.4**：日志不记录数据库密码、Token、连接地址或完整业务请求参数。
- 历史构建产物可能仍包含旧配置；不得直接部署，应轮换历史凭据并从当前源码重新构建。
