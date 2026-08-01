# GameExchange

## 项目简介

GameExchange 是一个基于 Java Servlet 的游戏虚拟经济交易项目，包含玩家注册与登录、玩家信息、物品查询、市场挂售与购买、战斗结果保存及统计数据展示等功能。

当前项目正在进行工程化整理，支持 Maven/Tomcat 手动运行和 Docker Compose 一键开发环境。开发编排不代表项目已经具备生产环境高可用能力。

## 技术栈

| 领域 | 当前技术 | 说明 |
| --- | --- | --- |
| Java | JDK 17 | `pom.xml` 使用 Java 17 编译 |
| 构建 | Maven WAR | 产物部署到外部 Servlet 容器 |
| Web | Servlet API 4.0.1 | 源码使用 `javax.servlet.*` |
| 容器 | Tomcat 9.x | Tomcat 10+ 使用 `jakarta.*`，不能直接替换 |
| 数据库 | MySQL Connector/J 8.4.0 | Compose 开发环境使用 MySQL 8.4 |
| 连接池 | Druid 1.2.23 | 数据库配置支持运行环境覆盖 |
| JSON | Fastjson 1.2.83、Gson 2.10.1 | 当前代码同时使用两套 JSON 工具 |
| 前端 | HTML、Vue 2、Axios、Three.js、GSAP | 部分依赖通过 CDN 加载 |
| 日志 | `java.util.logging` | 由 Tomcat 运行环境接管输出和级别配置 |

## 运行前提

开始前需要准备：

- JDK 17。
- Tomcat 9.x。
- 已创建业务表的 MySQL 数据库。
- 能够访问前端 CDN 的网络环境。

使用 Compose 开发环境时，只需要 Docker Desktop 或兼容的 Docker Engine，并确保 `docker compose version` 可用；Java、Maven、Tomcat 和 MySQL 均由容器环境提供。

检查本机环境：

```powershell
java -version
.\mvnw.cmd --version
```

Linux 或 macOS 使用 `./mvnw --version`。Wrapper 固定使用 Maven 3.9.16，因此开发机不需要预装 Maven；首次运行需要联网下载 Maven 分发包。

## 数据库前置条件

默认数据库名称为 `game_exchange`。源码会访问以下业务表：

- `player`
- `item`
- `market`
- `trade_record`
- `game_event`

仓库通过以下脚本提供 MySQL 8.4 数据库基线：

- `database/schema.sql`：创建数据库、业务表、索引和数据约束。
- `database/seed.sql`：写入可重复执行的最小市场与排行榜数据。

新环境只执行 Baseline，不执行历史 Migration：

在项目根目录依次执行：

```powershell
mysql --default-character-set=utf8mb4 -u <username> -p --execute="SOURCE database/schema.sql"
mysql --default-character-set=utf8mb4 -u <username> -p --execute="SOURCE database/seed.sql"
```

脚本不会删除已有数据库或业务数据。`schema.sql` 是新环境基线，不会自动升级已经存在但结构不同的旧表。

已有数据库必须根据已保存的执行记录，按以下顺序执行尚未应用的 Migration：

```text
phase-3a-add-player-last-seen-at.sql
        |
phase-db-repair-item-rarity-check.sql
        |
phase-4c-drop-player-online-status.sql
        |
phase-5a-data-repair-charset-mojibake.sql
```

Phase 5A Charset Repair 已在当前已验证数据库完成。该数据修复脚本只匹配已确认的 5 条历史乱码记录；新环境的 Baseline 已是正确 UTF-8 数据，不得重复执行该 Migration。

Migration 的前置条件、结构/数据影响、风险和完整执行规则见 [DATABASE_MIGRATION.md](DATABASE_MIGRATION.md)。

## 数据库配置

基础默认值位于 `src/main/resources/db.properties`。数据库用户名和密码不写入仓库，必须通过环境变量或 JVM 系统属性注入。

配置优先级：

```text
JVM 系统属性 > 环境变量 > db.properties
```

| 用途 | 环境变量 | JVM 系统属性 | 是否必需 |
| --- | --- | --- | --- |
| 数据库地址 | `DB_URL` | `db.url` | Docker 或远程数据库环境必设 |
| 数据库用户名 | `DB_USERNAME` | `db.username` | 是 |
| 数据库密码 | `DB_PASSWORD` | `db.password` | 是 |
| JDBC 驱动 | `DB_DRIVER_CLASS_NAME` | `db.driverClassName` | 否，已有默认值 |
| 初始连接数 | `DB_POOL_INITIAL_SIZE` | `db.pool.initialSize` | 否 |
| 最大连接数 | `DB_POOL_MAX_ACTIVE` | `db.pool.maxActive` | 否 |
| 最小空闲连接数 | `DB_POOL_MIN_IDLE` | `db.pool.minIdle` | 否 |
| 获取连接最大等待时间 | `DB_POOL_MAX_WAIT` | `db.pool.maxWait` | 否 |

PowerShell 示例：

```powershell
$env:DB_USERNAME = "<your-username>"
$env:DB_PASSWORD = "<your-password>"
$env:DB_URL = "jdbc:mysql://<mysql-host>:3306/game_exchange?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8&connectionCollation=utf8mb4_0900_ai_ci"
```

这些环境变量必须能够被启动 Tomcat 的进程读取。如果 Tomcat 作为系统服务运行，应在服务运行环境中配置，而不是只在临时终端中配置。

> Docker 中的 `127.0.0.1` 指向应用容器自身。未来使用独立 MySQL 容器时，必须通过 `DB_URL` 指向 MySQL 服务名。

## 模拟器配置

游戏模拟器默认关闭，普通登录、交易和排行榜功能不依赖模拟器。配置优先级为：

```text
JVM 系统属性 simulator.enabled > 环境变量 SIMULATOR_ENABLED > 默认 false
```

仅接受 `true` 或 `false`，缺失或无效值都会安全关闭模拟器。启用后，Tomcat 启动时才会创建 `GameSimulator` 并加载模拟玩家；配置变化需要重启 Tomcat 才能生效。

PowerShell 启用示例：

```powershell
$env:SIMULATOR_ENABLED = "true"
```

也可以通过 Tomcat JVM 参数设置 `-Dsimulator.enabled=true`。JVM 系统属性优先于环境变量。

当前 `GameSimulator` 中的三个定时调度调用仍处于注释状态；启用开关不会自动恢复这些任务。未来 Docker 环境应显式设置 `SIMULATOR_ENABLED=false`，避免多副本重复执行模拟任务。

## 构建项目

在项目根目录执行：

```powershell
.\mvnw.cmd package
```

Linux 或 macOS 执行：

```bash
./mvnw package
```

默认生成：

```text
target/GameExchange_war-1.0-SNAPSHOT.war
```

前端通过 `vue/assets/js/app-config.js` 根据当前页面地址计算 `BASE_PATH`，静态资源使用相对路径，API 统一通过 `window.GE_API()` 生成。因此 WAR 可以使用其他名称，也可以部署为 `ROOT.war`，不需要修改 HTML。

## Docker Compose 开发环境

Compose 使用现有 `Dockerfile` 构建应用，并管理 MySQL 8.4、Bridge Network 和持久化 Volume。`schema.sql` 与开发用 `seed.sql` 只会在空数据卷首次启动时执行。

首次使用时创建本地配置：

```powershell
Copy-Item .env.example .env
```

编辑 `.env`，为 `MYSQL_PASSWORD` 和 `MYSQL_ROOT_PASSWORD` 设置不同的非空随机密码。`.env` 已被 Git 和 Docker Build Context 忽略，不要提交该文件。

启动开发环境：

```powershell
docker compose config --quiet
docker compose up --detach --build
docker compose ps
```

启动后访问：

```text
http://127.0.0.1:8080/vue/login.html
```

`APP_PORT` 可以在 `.env` 中覆盖。MySQL 不发布到宿主端口；需要执行数据库命令时使用 `docker compose exec mysql`。

查看日志和控制服务：

```powershell
docker compose logs --follow
docker compose stop
docker compose start
```

删除容器和 Network，但保留数据库 Volume：

```powershell
docker compose down
```

永久清理开发数据库：

```powershell
docker compose down -v --remove-orphans
```

> **危险操作：** `docker compose down -v` 会永久删除 Compose 管理的开发数据库。只有确认数据不再需要，或需要从 `schema.sql` 和 `seed.sql` 重新初始化时才能执行。

MySQL 通过真实 Healthcheck 控制应用启动顺序。Application Healthcheck 每 30 秒请求容器内的静态资源 `/vue/assets/js/app-config.js`，用于确认 Tomcat 已启动且 WAR 静态资源可以访问。探针使用 5 秒超时、连续 3 次失败后标记 unhealthy，并为应用启动保留 30 秒宽限期。

Application Healthcheck 不使用 `/stats`。该接口会执行多条业务数据库查询，而且数据库异常时可能仍返回 HTTP 200、仅在 JSON 中写入 `code=500`；使用它既会产生不必要的数据库负载，也可能形成错误的健康判断。当前静态资源探针是轻量 liveness 检查，不代表数据库或完整业务链路已经 ready。

app 和 MySQL 均配置 `restart: unless-stopped`。该策略会在容器进程退出或 Docker daemon 重启后恢复服务，但手动停止的容器不会被强制拉起。Docker Compose 不会因为容器被 Healthcheck 标记为 unhealthy 而自动重启容器；Healthcheck 与 Restart Policy 是两个独立机制。

两个服务均使用 `json-file` 日志驱动，并设置 `max-size: "10m"`、`max-file: "3"`。每个容器最多保留约 30 MB 的 Docker JSON 日志，避免日志无限增长占满宿主机磁盘。

数据库密码仍通过环境变量注入，具有 Docker 管理权限的人员可以通过 inspect 查看；生产 Secret 文件化将在后续独立阶段处理。

app 容器以非 root 用户 `gameexchange` 运行，固定 UID/GID 为 `10001:10001`，登录 Shell 为 `nologin`。Tomcat 运行时需要写入的 `/usr/local/tomcat/webapps`、`work`、`temp` 和 `logs` 目录归该用户所有；`bin`、`conf` 和 `lib` 继续由 root 持有并供运行用户只读访问。唯一例外是预创建的 `/usr/local/tomcat/conf/Catalina/localhost`，该目录归 `gameexchange:gameexchange` 所有并使用 `0750` 权限，以便 Tomcat HostConfig 在 non-root 模式下管理 localhost 的 Context 配置，而不扩大整个 `conf` 目录的写权限。固定数字 UID/GID 可以避免不同环境中用户名映射不一致，并为后续容器编排的安全策略提供稳定身份。

### Docker 镜像固定与更新

Dockerfile 的 JDK builder、Tomcat runtime 和 Compose 的 MySQL 镜像均使用 `tag@sha256:digest`。tag 便于识别预期版本，digest 固定实际镜像内容，避免同名 tag 更新后在不同时间或环境中构建出不同镜像。当前 digest 已在 `linux/amd64` 平台验证；其他平台不能直接假定使用相同的单平台 digest。

更新镜像时应同时更新 tag 和 digest：先从可信 Registry 获取目标 tag 的 digest，确认它对应部署平台，再更新 Dockerfile 或 `compose.yaml`。提交前依次执行 `docker compose config`、Maven 测试与打包、`docker build`、app-only 重建、Healthcheck 和业务回归。不要只修改 tag 而保留旧 digest，也不要只替换未经平台验证的 digest。

回滚应用 builder 或 runtime 镜像时，将对应 `FROM` 恢复为上一个已验证的 `tag@digest` 后重新构建并仅重建 app。回滚 MySQL 镜像时，将 `compose.yaml` 恢复为上一个已验证的 `tag@digest`；数据库镜像降级可能与现有数据目录格式不兼容，必须先核对 MySQL 官方兼容性说明和可恢复备份，禁止通过删除 `mysql-data` Volume 实现回滚。

## 部署到 Tomcat 9

以下命令假设已经设置 `CATALINA_HOME`，并且数据库环境变量能够传递给 Tomcat：

```powershell
Copy-Item ".\target\GameExchange_war-1.0-SNAPSHOT.war" `
  "$env:CATALINA_HOME\webapps\GameExchange_war.war" -Force

& "$env:CATALINA_HOME\bin\startup.bat"
```

启动后访问：

```text
http://localhost:8080/GameExchange_war/
```

上面的地址由示例 WAR 名称决定。如果部署为 `ROOT.war`，访问地址为 `http://localhost:8080/`；如果使用其他 WAR 名称，Context Path 会随之变化，前端会自动适配。

停止 Tomcat：

```powershell
& "$env:CATALINA_HOME\bin\shutdown.bat"
```

Tomcat 默认端口、自动部署开关或 Context 配置被修改时，实际地址可能不同。

## 日志

应用使用 `java.util.logging`：

- `INFO`：启动、停止和正常状态。
- `WARNING`：可继续运行但需要关注的状态。
- `SEVERE`：数据库、事务或请求处理异常。
- `FINE`：高频模拟事件明细，默认可能不显示。

实际日志格式、级别和输出位置由 Tomcat 配置决定。排查时先查看 Tomcat 控制台及 `$CATALINA_HOME/logs`。

## 项目目录

```text
GameExchange/
├─ compose.yaml                       # Compose 开发环境编排
├─ Dockerfile                         # 应用镜像唯一构建来源
├─ .env.example                       # 非敏感开发环境变量模板
├─ README.md                         # 项目入口与运行说明
├─ DATABASE_MIGRATION.md              # 数据库初始化、升级顺序与 Migration 清单
├─ CODE_REVIEW.md                    # Milestone 1.1 项目审查报告
├─ CHANGELOG.md                      # 工程变更记录
├─ database/
│  ├─ migrations/                    # 已有数据库的一次性增量迁移
│  ├─ schema.sql                     # MySQL 8.4 数据库结构基线
│  └─ seed.sql                       # 最小可重复种子数据
├─ .mvn/wrapper/
│  └─ maven-wrapper.properties       # Maven 版本、下载地址与校验和
├─ docs/
│  ├─ README.md                      # 文档索引与维护规则
│  └─ docker/                        # Docker 与 Compose 验证记录
├─ mvnw                              # Linux/macOS Maven Wrapper
├─ mvnw.cmd                          # Windows Maven Wrapper
├─ pom.xml                           # Maven 构建与依赖配置
├─ deploy.bat                        # 历史远程部署脚本，不作为推荐流程
├─ src/main/
│  ├─ java/com/game/
│  │  ├─ dao/                        # 数据访问
│  │  ├─ entity/                     # 数据实体
│  │  ├─ service/                    # 交易业务服务
│  │  ├─ servlet/                    # HTTP 接口
│  │  ├─ simulator/                  # 模拟任务和监听器
│  │  └─ util/                       # 数据库连接工具
│  ├─ resources/
│  │  └─ db.properties               # 非敏感数据库与连接池默认值
│  └─ webapp/
│     ├─ index.jsp                   # Web 应用入口
│     └─ vue/                        # 页面、共享运行配置与前端资源
└─ tools/
   └─ static-server.js               # 静态页面预览辅助工具，不提供后端接口
```

`.idea/`、`out/` 和 `target/` 属于本地配置或构建产物，不是业务源码。

## 当前限制

- 数据库尚未引入自动迁移工具；历史 Migration 需按版本人工执行并保存记录，Compose 不会为已有数据卷自动执行。
- 已声明 JUnit 5 和 Mockito 依赖，但当前没有 `src/test` 测试代码。
- Maven Wrapper 和项目依赖首次下载时需要访问 Maven Central，离线环境必须预先准备缓存。
- 前端部分资源依赖 CDN，离线环境可能无法完整加载。
- Session 保存在单个 Tomcat 进程内，当前不支持无状态多副本扩展。
- 已提供 Dockerfile 和单机开发 Compose；尚未提供 Kubernetes 或 CI/CD 配置。
- `tools/static-server.js` 只能辅助查看静态资源，不能替代 Tomcat，也不能验证登录、Session 或数据库接口。
- `deploy.bat` 含本机和远程环境假设，不应作为通用或生产部署方案。

## 安全说明

- 不要把数据库密码、Token 或服务器密钥写入源码、README 或 WAR。
- 不要直接部署 `out/` 或 `target/` 中的历史制品，应从当前源码重新构建。
- 如果历史凭据曾进入源码或构建产物，应在数据库侧轮换，并清理不再需要的旧制品。

## 进一步阅读

- 数据库初始化、历史 Migration 顺序及风险：[DATABASE_MIGRATION.md](DATABASE_MIGRATION.md)
- 文档索引与维护规则：[docs/README.md](docs/README.md)
- 项目架构、模块关系、数据库关系、技术债和 Docker 就绪度：[CODE_REVIEW.md](CODE_REVIEW.md)
- 工程升级与兼容性变化：[CHANGELOG.md](CHANGELOG.md)
