# 变更日志

本文件记录 GameExchange 已经实施的工程化变更。尚未纳入版本的变化记录在 `Unreleased` 下，发布候选版本单独记录。

记录原则：

- 只记录已经实施并完成相关验证的变化。
- 不补写无法确认的早期开发历史。
- 不在变更记录中保存凭据、服务器地址或其他敏感信息。
- 形成发布候选或正式版本后，在带日期的版本章节中记录对应内容。

## [1.0.0-rc1] - 2026-08-01

### 变更

- 完成交易事务可靠性增强。
- 增加 TradeService 事务回滚、失败注入和并发购买验证。
- 增加 Battle 幂等、防重复提交、并发和持久化验证。
- 增加玩家在线状态 Heartbeat。
- 增加数据库 Migration 管理及 Baseline、Upgrade、HEX Guard 验证。
- 增加 Docker Runtime 验证。
- 增加 Release Gate 自动化。
- 增加 Artifact Identity 追踪及 WAR、Image、Migration 制品验证。

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
- **Phase 3A**：新增 `database/migrations/phase-3a-add-player-last-seen-at.sql`，用于为已有数据库增加玩家在线租约时间字段和查询索引。
- **Phase 3B**：新增 `PresenceService` 与 `POST /presence/heartbeat`，由已登录 Session 中的玩家身份刷新在线租约。
- **Phase 3C**：新增共享前端 Heartbeat 调度器，按 20 秒周期刷新租约，并在页面恢复可见时立即补发。
- **Phase 5A**：新增定向字符集数据修复 Migration，只处理已经确认的历史乱码记录，并提供执行前后校验。
- **Phase 5C**：新增 `battle_record` 持久化表、唯一请求约束和对应 DAO，为战斗结算幂等提供数据库依据。
- **测试基线**：建立 JUnit 5、Mockito 和 Testcontainers 测试结构；当前单元测试快照为 `42/42` 通过，集成测试继续由 Maven Failsafe 在 `verify` 阶段执行。
- **Docker 基线**：新增多阶段 `Dockerfile`、开发 Compose、真实 Healthcheck、持久化数据库 Volume 和运行验证记录。
- **Phase 6.1-A**：新增单 ECS 生产 Compose、Nginx 入口配置和生产部署手册，覆盖文件型 Secret、ESSD 数据目录、备份、验证和回滚边界。
- **Observability P1.1**：新增 Micrometer Prometheus Registry、JVM/进程指标初始化和 `/metrics` 采集端点。
- **Observability P1.2**：新增在线玩家 Gauge 和成功交易 Counter；在线人数采样复用 60 秒 Lease 查询，数据库故障时返回 `NaN`。
- **Observability P1.3**：新增 Prometheus、Node Exporter 和 Grafana Compose，自动加载 Prometheus 数据源及包含 11 个面板的业务 Dashboard。
- **CI 质量门禁**：新增 GitHub Actions 工作流，在 Push、Pull Request 或手动触发时使用 JDK 17 和 `actions/setup-java@v5` 执行 Maven `verify`，失败时保存 Surefire 和 Failsafe 报告。

### 变更

- **Milestone 1.3**：数据库配置支持 JVM 系统属性、环境变量和资源文件三级来源，优先级为 JVM 系统属性、环境变量、`db.properties`。
- **Milestone 1.3**：数据库连接初始化增加必要配置校验，缺少用户名、密码等必要值时快速失败。
- **Milestone 1.4**：11 个 Java 类统一使用 `java.util.logging`，按 `INFO`、`WARNING`、`SEVERE` 和 `FINE` 区分日志级别。
- **Milestone 1.4**：Java 源码中的 `System.out`、`System.err` 和 `printStackTrace()` 已清理完成。
- **Milestone 1.6**：根目录 README 增加文档索引和变更日志入口。
- **Phase 1.5 / Milestone 2**：五个前端页面移除固定 `/GameExchange_war` 的 `<base>` 和重复 `GE_API_BASE`，支持任意 WAR Context Path 及 ROOT 部署。
- **Phase 1.5 / Milestone 3**：项目构建入口改为 Maven Wrapper，并为 Linux/macOS 的 `mvnw` 固定 LF 换行符。
- **Phase 1.5 / Milestone 4**：游戏模拟器改为默认关闭，只有开关明确为 `true` 时监听器才会创建模拟器并访问数据库；无效配置按关闭处理。
- **Phase 3A**：`player` 新环境基线增加可空 `last_seen_at` 字段与 `idx_player_last_seen_at` 索引；不回填历史数据，不修改现有 `online_status` 业务逻辑。
- **Phase 3B**：登录、登出和 Session 销毁统一通过 `PresenceService` 更新租约，并继续维护 Legacy `online_status`；`/stats` 仍使用 Legacy 查询。
- **Phase 3C**：登录成功后立即启动 Heartbeat；接口返回 401 时停止调度。前端不切换 `/stats` 读路径，也不改变 `online_status` 写入。
- **Phase 3D**：`/stats` 同时计算 Legacy 与 60 秒 Lease 在线人数并记录差异；对外 `onlineCount` 仍返回 Legacy 结果，Lease 查询失败不影响原有响应。
- **Phase 3E**：`/stats.onlineCount` 切换为 60 秒 Lease 查询；Legacy 查询仅用于 Shadow 对比，其异常不影响正式 Lease 响应，并继续保留 `online_status` 双写。
- **Phase 3F**：应用停止写入 `online_status`；登录与 Heartbeat 只刷新 `last_seen_at`，登出与 Session 销毁只清理租约。Legacy 字段和 Shadow Verification 继续保留。
- **Phase 4B.2**：Java Entity、DAO 和相关测试移除对 `online_status` 的运行时依赖；数据库字段暂保留。
- **Database Constraint Repair**：新增独立 Migration，修复现有 MySQL Volume 中 `item.chk_item_rarity` 的字符集定义，并仅修复已确认的三条历史异常数据。
- **Phase 4B.3**：更新 `CODE_REVIEW.md` 和本变更日志，明确当前 Presence 使用 `last_seen_at` + 60 秒 Lease；`online_status` 仅作为待 Phase 4C 清理的 Legacy 字段保留。
- **容器运行安全**：应用以固定非 root UID/GID 运行；生产 Compose 启用 `no-new-privileges`、移除 Linux Capabilities，并要求应用镜像使用 Registry Digest。
- **监控访问边界**：Prometheus 和 Grafana 只绑定宿主机回环地址，生产 Nginx 对精确 `/metrics` 路径返回 404，Prometheus 改从 Docker 内部网络采集应用。
- **CI Action Runtime**：根据 GitHub Runner 弃用提示，将 `actions/setup-java` 从 v4 升级到使用 Node.js 24 的 v5。

### 移除

- **Milestone 1.3**：移除源码中的旧 `druid.properties`，避免继续把数据库凭据作为资源文件打包。
- **Phase 4B.1**：移除 `/stats` 的 Legacy 在线人数查询与 Shadow Verification 日志；`onlineCount` 继续使用 60 秒 Lease 查询，API 返回结构不变。
- **Phase 4C**：通过独立 Migration 删除 `player.online_status` Legacy 字段及其约束；在线状态唯一事实来源保持为 `last_seen_at` Lease。

### 安全

- **Milestone 1.3**：数据库用户名和密码改为通过运行环境注入，不再保存在当前源码配置中。
- **Milestone 1.4**：日志不记录数据库密码、Token、连接地址或完整业务请求参数。
- 用户注册密码使用 BCrypt 保存；Legacy 明文密码在成功登录后以条件更新方式迁移为 BCrypt Hash。
- 生产数据库密码通过文件型 Secret 注入，不写入 Compose、镜像或仓库配置。
- 历史构建产物可能仍包含旧配置；不得直接部署，应轮换历史凭据并从当前源码重新构建。

### 文档

- **P2.1A**：重整根目录 README，补充项目亮点、Mermaid 架构图、测试与监控入口，并同步当前能力边界。
- 新增 Prometheus、Grafana 启动与排错手册，更新文档索引中的数据库迁移、生产部署和监控入口。

### 已知限制

- **P1.4 已跳过**：当前仍由各 Servlet 分别执行 Session 检查，尚未实施统一 Authentication Filter 和 CSRF Token 校验。
- 当前可观测性范围不包含告警规则、Alertmanager、集中日志或多节点监控。
- CI 的本地等价命令已完成 `42/42` 单元测试和 `21/21` 集成测试验证；GitHub Hosted Runner 的 CI #1 已成功完成，当前不包含 CD 自动部署。
