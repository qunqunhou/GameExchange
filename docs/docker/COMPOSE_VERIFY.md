# GameExchange Compose 验证记录

## 1. 文档信息

| 项目 | 内容 |
| --- | --- |
| 阶段 | Phase 2C：Development Environment Orchestration |
| 编排文件 | `compose.yaml` |
| 创建日期 | 2026-07-27 |
| Static Config Verification | Complete |
| Runtime Verification | Verified（Phase 5C.7F.1） |

本文档只记录实际执行结果。未执行的检查保持 `Pending`，不得根据配置推断为通过。

## 2. Static Config Verification

| 检查项 | 状态 | 证据 |
| --- | --- | --- |
| YAML 语法 | 通过 | `docker compose config --quiet` Exit Code 0 |
| 环境变量展开 | 通过 | 使用进程内随机密码和覆盖值解析，敏感值未输出 |
| 必填密码 Fail-fast | 通过 | 不提供 `.env` 或密码变量时配置解析 Exit Code 1 |
| `app` 与 `mysql` 服务 | 通过 | 解析结果仅包含 `app,mysql` |
| Dockerfile 构建来源 | 通过 | `app.build.context=.`、`dockerfile=Dockerfile` |
| Bridge Network | 通过 | `backend`，driver 为 `bridge` |
| Named Volume | 通过 | `mysql-data`，driver 为 `local` |
| `depends_on: service_healthy` | 通过 | `app` 等待 `mysql` 的 `service_healthy` |
| MySQL Healthcheck | 通过 | 使用 `MYSQL_PWD` 和 `mysqladmin ping` |
| Application Healthcheck | Verified | 使用轻量静态资源 `/vue/assets/js/app-config.js`，容器达到 `healthy` |
| Schema/Seed 挂载 | 通过 | 两个初始化脚本均为只读 bind mount |
| 端口边界 | 通过 | app 只绑定 `127.0.0.1`；MySQL 不发布宿主端口 |

静态验证使用一次性 PowerShell 进程环境变量，没有创建 `.env`、容器、Network 或 Volume，也没有执行镜像构建。

## 3. Runtime Verification Matrix

| 编号 | 验证项 | 状态 |
| --- | --- | --- |
| C-01 | 隔离 project 执行 `docker compose up --detach --build` | 通过 |
| C-02 | MySQL 初始化与 `healthy` 状态 | 通过 |
| C-03 | 应用在 MySQL 健康后启动并达到 `healthy` | 通过 |
| C-04 | ROOT.war、Tomcat 与数据库连接 | 通过，`/stats` 返回 HTTP 200 和 JSON `code=200` |
| C-05 | 登录页和静态资源 | 通过，均返回 HTTP 200 |
| C-06 | 注册与登录 | Pending |
| C-07 | 市场与交易 | Pending |
| C-08 | 排行榜 | Pending |
| C-09 | App PID 1 退出后的 restart policy 恢复 | 通过，`RestartCount` 从 0 增加到 1 并恢复 `healthy` |
| C-10 | `docker compose down` 后隔离 Volume 保留 | 通过 |
| C-11 | 再次启动后 Sentinel 与 Seed 数据保留 | 通过，初始化脚本未重复执行 |
| C-12 | 标签与名称校验后的隔离资源清理 | 通过，Container、Network、Volume 均为 0 |
| C-13 | 应用与 MySQL 诊断证据保存 | 通过，日志、Inspect 与 Compose 状态保存到 `target/docker-runtime/` |

## 4. Runtime Evidence

执行命令：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools\docker\verify-compose-runtime.ps1
```

2026-08-01 验证记录：

- Run ID：`20260801-133725-89c5604e`。
- Compose project：`gameexchange-rt-20260801-133725-89c5604e`。
- App image：`gameexchange-runtime-test:20260801-133725-89c5604e`。
- 随机宿主端口：`10557`，未占用生产 Compose 的默认端口。
- MySQL 与 App 均达到 `healthy`；App PID 1 UID 为 `10001`，`ROOT.war` 存在。
- `/vue/assets/js/app-config.js`、`/vue/login.html` 和 `/stats` 均返回 HTTP 200，`/stats` JSON `code=200`。
- App PID 1 退出后 `RestartCount` 从 0 增加到 1，随后重新达到 `healthy`。
- Sentinel 数据在不带 `--volumes` 的 `compose down` 和再次 `up` 后仍存在，Seed 未重复执行。
- 最终清理前校验 Compose project label 和 Volume 名称；清理后测试 Container、Network、Volume 数量均为 0。
- 证据保存于 `target/docker-runtime/20260801-133725-89c5604e/`。

## 5. Known Limitations

- App Healthcheck 只验证 Tomcat 静态资源可用；数据库链路由独立的 `/stats` smoke test 验证。
- `depends_on: service_healthy` 只控制初次启动顺序，不负责运行期间故障恢复。
- `seed.sql` 仅用于该开发环境。
- 本阶段未覆盖注册、登录、市场、交易和排行榜业务回归。
- Runtime 脚本只清理带唯一测试 project label 且名称匹配的资源，不访问或删除 `gameexchange_mysql-data`。
