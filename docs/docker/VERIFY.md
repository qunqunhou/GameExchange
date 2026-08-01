# GameExchange Docker 验证记录

## 1. 文档信息

| 项目 | 内容 |
| --- | --- |
| 阶段 | Phase 2B：Container Verification |
| 验证日期 | 2026-07-24（Level 1）、2026-07-27（Level 2） |
| 验证环境 | 见“Environment（验证环境）”章节 |
| Level 1 | 已完成 |
| Level 2 | 已完成 |
| 总体状态 | Phase 2B 静态与动态验证全部通过，等待 Tech Lead Review |

本阶段只验证 Phase 2A 生成的 `Dockerfile` 和 `.dockerignore`。不创建或验证 Docker Compose、Redis、Nginx、Kubernetes 等后续能力。

## 2. Environment（验证环境）

以下信息均来自当前验证主机的实际命令输出：

| 项目 | 实际环境 | 验证方式 |
| --- | --- | --- |
| 操作系统 | Windows 11 24H2，内核 `10.0.26100.4652`，x64 | Maven OS 识别、`cmd /c ver`、Windows 版本注册表 |
| Java | Oracle JDK `17.0.12`，64-Bit Server VM | `java -version` |
| Maven | Apache Maven `3.9.16` | `.\mvnw.cmd --version` |
| Maven Wrapper | Wrapper `3.3.4`，锁定 Maven `3.9.16` 并配置 SHA-256 校验 | `.mvn/wrapper/maven-wrapper.properties` |
| Git | `2.55.0.windows.3` | `git --version` |
| Docker Desktop | `4.83.0` | Docker Desktop About、运行时信息 |
| Docker CLI | `29.6.2`，API `1.55` | `docker version` |
| Docker Engine | `29.6.2`，Linux/amd64 | `docker version`、`docker info` |
| Docker 内核 | WSL2 `6.18.33.2-microsoft-standard-WSL2` | `docker info` |
| BuildKit | `v0.31.2`，状态 `running`，worker network `host` | `docker buildx inspect --bootstrap` |
| Docker Proxy | daemon 使用 `http.docker.internal:3128` 转发 | `docker info` |
| 时区 | Asia/Shanghai（UTC+8） | 当前工作区环境 |

宿主机 Java/Maven 仅用于对照；Level 2 的 WAR 由 Linux Builder 内的 Maven Wrapper 独立生成。

## 3. 验证分级

- **Level 1 静态验证**：检查 Dockerfile、构建输入、Maven 构建、WAR、预期镜像边界和启动契约，不要求存在 Docker Engine。
- **Level 2 动态验证**：实际执行镜像构建、容器启动、HTTP 和数据库业务验证，必须使用真实 Docker Engine。

静态验证只能证明配置和制品符合预期，不能替代真实镜像及容器验证。

## 4. Level 1：静态验证

### 4.1 Docker 环境检查

| 检查项 | 结果 | 说明 |
| --- | --- | --- |
| Docker CLI/Engine | 通过 | Client/Server 均为 `29.6.2` |
| Docker Desktop | 通过 | `4.83.0`，Linux container mode |
| BuildKit | 通过 | `v0.31.2`，状态 `running` |
| Docker Hub 访问 | 通过 | 配置 Desktop 手动代理后四个指定镜像全部拉取成功 |
| Docker DNS | 有已知限制 | Docker VM 使用 `10.255.255.254`；宿主直连 Docker Hub DNS 仍存在污染，当前由代理路径规避 |

历史说明：2026-07-24 执行 Level 1 时 Docker 不可用；2026-07-27 Docker Desktop 环境恢复并完成 Level 2。当前环境可执行 Docker Build 和容器运行验证。

### 4.2 Dockerfile 检查

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| 多阶段构建 | 通过 | 存在 `builder` 和 `runtime` 两个阶段 |
| Builder | 通过 | `eclipse-temurin:17-jdk-noble` |
| Maven Wrapper | 通过 | Builder 执行项目 `./mvnw`，未使用系统 Maven |
| Wrapper 权限 | 通过 | `COPY --chmod=0755 mvnw` |
| Java 版本 | 通过 | Builder 固定 Java 17 系列 |
| BuildKit Cache | 通过 | `/root/.m2` Cache Mount，`sharing=locked` |
| Runtime | 通过 | `tomcat:9.0.120-jre17-temurin-noble` |
| Runtime JRE | 通过 | Runtime 不使用 JDK 镜像 |
| WAR 部署位置 | 通过 | Builder WAR 复制为 `webapps/ROOT.war` |
| 本地 `target` | 通过 | 没有从 Build Context 复制本地 `target` |
| Runtime COPY 边界 | 通过 | Runtime 只从 Builder 复制一个 WAR |
| 模拟器默认值 | 通过 | `SIMULATOR_ENABLED=false` |
| 端口 | 通过 | `EXPOSE 8080` |
| 前台进程 | 通过 | `CMD ["catalina.sh", "run"]` |
| HEALTHCHECK | 符合设计 | 当前没有专用健康接口，Dockerfile 未设置健康检查 |
| 运行用户 | 已知风险 | 继承官方 Tomcat 镜像默认用户，本阶段未增加非 root 权限改造 |
| 基础镜像 digest | 符合设计 | 当前只固定可读标签，正式发布阶段再锁定 digest |

### 4.3 `.dockerignore` 检查

最终采用黑名单策略。

| 检查项 | 结果 | 说明 |
| --- | --- | --- |
| `.git/` | 已排除 | 不发送版本库对象 |
| `.idea/`、`.vscode/` | 已排除 | 不发送 IDE 状态 |
| `target/`、`out/` | 已排除 | 本地构建产物不进入 Build Context |
| `.env`、`.env.*` | 已排除 | 避免本地环境变量文件进入 Build Context |
| 文档与历史部署文件 | 已排除 | 不属于镜像构建输入 |
| `src/` | 未排除 | Builder 必须读取源码和未来测试代码 |
| `pom.xml` | 未排除 | Maven 构建必需 |
| `mvnw`、`.mvn/` | 未排除 | Maven Wrapper 构建必需 |

Dockerfile 使用显式 `COPY`，即使 Build Context 未来增加其他文件，也不会自动进入 Runtime 镜像。

### 4.4 Maven 与 WAR 验证

实际执行：

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean package
```

| 检查项 | 实际结果 |
| --- | --- |
| Maven 版本 | 3.9.16 |
| Java 版本 | 17.0.12 |
| 编译源码数 | 24 |
| 构建结果 | `BUILD SUCCESS` |
| Maven 总耗时 | 4.338 秒 |
| 测试文件数 | 0 |
| 测试执行结果 | 没有测试可执行，不能视为业务测试通过 |
| Maven Warning | 成功构建日志未出现 Warning |
| WAR | `target/GameExchange_war-1.0-SNAPSHOT.war` |
| WAR 大小 | 8,955,705 字节 |
| SHA-256 | `AB1DFB08D965AF74F93AD3E8D694238DC877C894A1E1036F05B85AEA55DD1798` |

WAR 内容检查：

| 检查项 | 实际结果 |
| --- | --- |
| `index.jsp` | 存在 |
| `vue/login.html` | 存在 |
| `WEB-INF/classes/db.properties` | 存在 |
| Java 源文件 | 0 |
| 编译后的 class | 25 |
| `WEB-INF/lib` 运行库 | 5 |

WAR 中存在 Maven 自动生成的 `META-INF/maven/.../pom.xml` 和 `pom.properties` 元数据；它们不是 Maven CLI、Wrapper 或本地仓库。

### 4.5 构建流程检查

预期 Docker Build 流程：

1. 拉取 Temurin JDK 17 Builder。
2. Builder 安装 `unzip`，用于解压校验过的 Maven Wrapper ZIP。
3. 复制 `mvnw`、`.mvn/` 和 `pom.xml`。
4. 复制 `src/`，不复制本地 `target/`。
5. 使用 BuildKit Cache Mount 执行 Wrapper `clean package`。
6. 验证目标 WAR 存在。
7. 创建独立 Tomcat 9 + JRE 17 Runtime 阶段。
8. 清空默认 `webapps`，只复制生成的 WAR 为 `ROOT.war`。

曾验证独立 `dependency:go-offline` 会额外解析插件依赖并在 120 秒内未完成。该非必要预热步骤已从最终 Dockerfile 移除，Maven Cache 仍由实际 `clean package` 构建层复用。

### 4.6 预期镜像结构检查

根据多阶段构建边界，Runtime 预期包含：

```text
tomcat:9.0.120-jre17-temurin-noble
└─ /usr/local/tomcat/
   ├─ bin/
   ├─ conf/
   ├─ lib/
   ├─ logs/
   ├─ temp/
   ├─ work/
   └─ webapps/
      └─ ROOT.war
```

Runtime 没有从 Builder 复制以下内容：

- `/workspace/src`
- `/workspace/.mvn`
- `/workspace/mvnw`
- `/root/.m2`
- Builder 的 JDK 和编译工具

以上是 Dockerfile 静态推导结果。实际镜像文件系统、Maven 命令缺失情况及镜像大小仍必须在 Level 2 验证。

### 4.7 启动流程检查

静态确认的启动流程：

1. Docker 启动 Runtime 镜像并执行 `catalina.sh run`。
2. Tomcat 在前台启动并部署 `/usr/local/tomcat/webapps/ROOT.war`。
3. `SIMULATOR_ENABLED=false`，模拟器不会启动。
4. `DatabaseLifecycleListener` 初始化 Druid 数据源。
5. `DBUtil` 按 JVM 属性、环境变量、资源文件的顺序读取数据库配置。
6. 容器环境必须显式提供 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`。
7. `DB_URL` 必须指向数据库容器或外部数据库，不能使用容器内的 `127.0.0.1`。
8. 数据库不可用或必要配置缺失时，应用 Context 应快速启动失败。

当前没有 Health Endpoint，因此不使用登录页面冒充健康检查。

### 4.8 Level 1 结论

Level 1 静态验证通过。Dockerfile、`.dockerignore`、Maven 构建、WAR 内容、预期镜像边界和启动契约与 Phase 2A 批准方案一致。

Level 1 结论本身不代表动态运行通过；实际动态结果见下一章。

## 5. Level 2：动态验证

### 5.1 当前状态

**Complete。** 2026-07-27 已使用真实 Docker Engine 完成镜像构建、MySQL 初始化、应用启动、HTTP、登录、市场、交易、排行榜、日志及优雅停止验证。

本次只使用 `docker build`、`docker network`、`docker volume` 和 `docker run`；未创建 Docker Compose，未进入 Phase 2C。

### 5.2 Environment Fix（Docker Network）

初次构建在拉取 `docker/dockerfile:1` 时无法连接 `auth.docker.io`。根因是宿主 DNS 将 Docker Hub 域名解析到不可达地址，同时 Docker Desktop 原 System proxy 没有正确使用本机 `verge-mihomo` 代理。

修复方式：Docker Desktop 改为 Manual proxy，HTTP/HTTPS 均指向 `http://127.0.0.1:7897`，Containers proxy 使用宿主代理。daemon 最终显示 HTTP/HTTPS Proxy 为 `http.docker.internal:3128`，BuildKit 保持 `running`。

| 镜像 | 结果 | 耗时 | Digest |
| --- | --- | ---: | --- |
| `docker/dockerfile:1` | 通过 | 25.634 秒 | `sha256:87999aa3d42bdc6bea60565083ee17e86d1f3339802f543c0d03998580f9cb89` |
| `eclipse-temurin:17-jdk-noble` | 通过 | 22.710 秒 | `sha256:0386aaf49d6756b4856119f8e037f40cc865c7c8fbdda7c81733cc806f462daf` |
| `tomcat:9.0.120-jre17-temurin-noble` | 通过 | 21.418 秒 | `sha256:c8963563a89328eff74e48ff01c5ac774672e6ffefd5c96decdf8d0718ca87be` |
| `mysql:8.4` | 通过 | 36.453 秒 | `sha256:8dbcf531a03aade657e181b9cf2f1d1803ce621a1d55610cb44cb531ab7d7db6` |

Temurin 首次拉取曾在 Registry 请求处收到一次 `EOF`；确认代理端点分别返回预期 HTTP 200/401 后再次验证成功。该事件属于代理隧道瞬时中断，不是镜像标签或项目配置问题。

### 5.3 动态验证矩阵

| 编号 | 验证项 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| L2-01 | Docker Build | `docker build` 退出码为 0，日志包含 Maven `BUILD SUCCESS` | 通过 |
| L2-02 | Build Warning | 审查完整 Build 日志，记录所有 Warning | 通过，见 5.6 |
| L2-03 | 镜像标签 | 成功生成 `gameexchange:phase2a` | 通过 |
| L2-04 | 镜像大小 | 记录实际镜像大小 | 通过，`docker images` 为 429 MB |
| L2-05 | 镜像层 | Runtime 没有项目源码、Maven 和本地仓库层 | 通过 |
| L2-06 | ROOT.war | `/usr/local/tomcat/webapps/ROOT.war` 存在 | 通过 |
| L2-07 | Java/Tomcat | Java 17、Tomcat 9.0.120 | 通过 |
| L2-08 | MySQL 启动 | MySQL 8.4 完成初始化，结构和种子脚本执行成功 | 通过 |
| L2-09 | 应用启动 | Tomcat 正常启动，ROOT Context 部署成功 | 通过 |
| L2-10 | 数据库连接 | Druid 初始化成功，无认证、DNS 或 JDBC 错误 | 通过 |
| L2-11 | HTTP 访问 | `/vue/login.html` 返回 HTTP 200；该检查不作为 Healthcheck | 通过 |
| L2-12 | 注册与登录 | 新测试用户可以注册并建立有效 Session | 通过 |
| L2-13 | 市场查询 | 登录用户可以看到初始化市场数据 | 通过 |
| L2-14 | 交易 | 测试买家完成一次购买，金币、物品、市场状态和交易记录一致 | 通过 |
| L2-15 | 排行榜 | 页面或 `/stats` 返回与数据库一致的统计结果 | 通过 |
| L2-16 | 日志检查 | 无未解释的 `SEVERE`、异常堆栈或敏感信息输出 | 通过，见 5.6 |
| L2-17 | 优雅停止 | 停止容器后 Tomcat、Druid 和 JDBC 清理流程正常 | 通过 |

### 5.4 实际执行命令

构建镜像：

```powershell
docker build --progress=plain --tag gameexchange:phase2a .
```

检查镜像元数据和层：

```powershell
docker image inspect gameexchange:phase2a
docker image history --no-trunc gameexchange:phase2a
```

检查 Runtime 边界：

```powershell
docker run --rm --entrypoint sh gameexchange:phase2a -c `
  'test -f /usr/local/tomcat/webapps/ROOT.war && ! command -v mvn && ! test -e /workspace/src'
```

数据库和应用应使用普通 `docker network` 与 `docker run` 完成验证；本阶段不得创建 Compose 文件。运行时必须注入 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`，实际密码不得写入验证记录。

### 5.5 业务验证顺序

1. 初始化全新的隔离 MySQL 数据目录。
2. 执行 `database/schema.sql` 和开发验证用 `database/seed.sql`。
3. 启动应用容器并检查 Tomcat 完整日志。
4. 访问登录页面。
5. 注册一次性测试买家并登录。
6. 查询市场并购买种子数据中的在售物品。
7. 核对买卖双方金币、物品所有者、市场状态和 `trade_record`。
8. 打开排行榜或请求 `/stats`，核对统计结果。
9. 退出登录并停止应用容器。
10. 检查关闭日志，确认连接池和 JDBC 清理完成。

任何一步失败时，应停止后续业务验证，先定位根因，不得把部分成功记录为整体通过。

### 5.6 Build 与镜像结果

| 项目 | 实际结果 |
| --- | --- |
| Docker Build | Exit Code `0` |
| 总耗时 | 260.501 秒 |
| Maven | `BUILD SUCCESS`，Maven 阶段 1 分 39 秒 |
| 编译 | 24 个源码文件，Java target 17 |
| 测试 | 没有测试文件可执行 |
| 镜像 | `gameexchange:phase2a` |
| Image ID / Digest | `sha256:e34ca809b25f9d0f953901e2adc839d1e9882e5fe429d0659a841a989edefa0b` |
| `docker images` 大小 | 429 MB |
| `docker image inspect .Size` | 117,288,123 字节（111.85 MiB） |
| 架构 | Linux/amd64 |
| Runtime Java | Eclipse Temurin `17.0.19+10` |
| Runtime Tomcat | `9.0.120` |
| ROOT.war | 存在，8,955,702 字节，并已展开为 `webapps/ROOT/` |
| Runtime Maven | 不存在 |
| Builder `/workspace`、`.m2` | 不存在 |
| 项目源码/构建文件 | 不存在 |

最终镜像只从 Builder 复制 WAR。官方 Tomcat 基础镜像自身的 `webapps.dist` 中包含示例源码，但未部署到 `webapps/`，也不属于本项目源码。

Build 日志没有 Maven 编译 Warning 或 Dockerfile Warning。可观察到 Ubuntu 报告 21 个包未升级、Maven 报告 `src/test/resources` 不存在，均为信息提示。首次构建较慢主要来自 Docker Hub metadata 和 Maven 依赖下载；BuildKit Maven Cache 已生效。

### 5.7 MySQL 验证结果

| 项目 | 实际结果 |
| --- | --- |
| 镜像 | `mysql:8.4`，运行版本 `8.4.10` |
| 网络 | `gameexchange-runtime-net` |
| 数据卷 | `gameexchange-runtime-mysql-data` |
| 容器 | `gameexchange-runtime-mysql` |
| 初始化耗时 | 42.520 秒（含首次数据目录初始化） |
| 初始化脚本 | `01-schema.sql`、`02-seed.sql` 均有 Entrypoint 执行证据 |
| 表数量 | 5 |
| 表 | `player`、`item`、`market`、`trade_record`、`game_event` |
| Seed | 1 个卖家、3 个物品、1 个在售记录、2 个事件 |
| 字符集 | `utf8mb4 / utf8mb4_0900_ai_ci` |
| 应用账号 | `gameexchange_app@%` 可连接并查询 `game_exchange` |
| 数据库 Error | 0 |

所有密码均在运行时随机生成，只存在于临时容器环境和进程内存，未写入仓库或本文档。

### 5.8 应用与业务验证结果

应用容器 `gameexchange-runtime-app` 与 MySQL 加入同一隔离网络，宿主只绑定 `127.0.0.1:18080`。`DB_URL` 使用容器 DNS 名 `gameexchange-runtime-mysql`，同时注入 `DB_USERNAME`、`DB_PASSWORD` 和 `SIMULATOR_ENABLED=false`。

| 验证项 | 实际结果 |
| --- | --- |
| `docker run` | Exit Code `0` |
| ROOT.war 部署 | 883 ms |
| Tomcat startup | 965 ms |
| 数据库连接池 | 初始化成功 |
| 模拟器 | 按配置未启用 |
| 登录页 | HTTP 200，34,115 字节 |
| 市场页 | HTTP 200，44,524 字节 |
| 排行榜页 | HTTP 200，30,306 字节 |
| CSS / JS | HTTP 200，内容非空 |
| 未登录市场 API | HTTP 401 |
| 注册 | `code=200` |
| 登录 | `code=200`，创建 1 个 Session Cookie |
| 初始市场 | 1 个在售商品，价格 200 |
| 购买 | `code=200` |
| 购买后市场 | HTTP 200，原始响应 `[]` |
| 买家金币 | 10,000 → 9,800 |
| 卖家金币 | 1,000 → 1,200 |
| 物品所有者 | 更新为买家 ID 2 |
| 市场状态 | `SOLD` |
| 交易记录 | buyer 2、seller 1、price 200，与业务一致 |
| `/stats` | `code=200`、online 1、on-sale 0、today volume 200、total gold 11,000 |

浏览器 smoke test 确认登录页面完成渲染、主登录区域可见、Canvas 为 1280×720、资源图片没有加载失败。控制台无 Error。

### 5.9 日志与停止结果

| 项目 | 实际结果 |
| --- | --- |
| 应用运行日志 | 47 行；Warning/Error/SEVERE/Exception 计数为 0 |
| MySQL 运行日志 | Error 计数为 0；Warning 11 条，均已解释 |
| 应用停止 | `docker stop` 0.507 秒；SIGTERM 对应 Exit Code 143 |
| Druid/JDBC 清理 | Druid closed、连接池关闭、清理线程关闭、JDBC Driver 注销 |
| MySQL 停止 | 2.090 秒，Exit Code 0，日志包含 `Shutdown complete` |

MySQL Warning 来源：官方 Entrypoint 的临时 `--initialize-insecure` 初始化、self-signed CA、pid-file 目录权限提示及镜像中缺少部分 timezone 文件。它们不影响本次无 TLS 的隔离开发验证，但生产环境必须重新评估 TLS、证书、时区数据和权限配置。

浏览器控制台有一条 Three.js r160 对旧版 `build/three.min.js` 入口的弃用提示；当前功能正常，属于现有前端依赖升级风险。

## 6. Known Limitations（已知限制）

**当前状态：Phase 2B Runtime Verification Complete。**

- 项目没有自动化测试，当前业务验收依赖一次性 API 与浏览器 smoke test。
- 当前没有 Health Endpoint，无法建立可靠的 Docker Healthcheck。
- Runtime 暂时继承官方 Tomcat 镜像默认用户，非 root 加固尚未实施。
- 基础镜像未锁定 digest，同名标签仍可能发生内容漂移。
- 宿主直连 Docker Hub DNS 仍不可靠，当前构建依赖 Docker Desktop 手动代理；代理不可用时拉取会再次失败。
- 官方 Tomcat 基础镜像保留未部署的 `webapps.dist` 示例及示例源码；项目源码没有进入 Runtime。
- 本地 Windows WAR 与 Linux Builder WAR 大小相差 3 字节，当前只验证功能和依赖流程可重复，尚未声明字节级可复现构建。
- 前端依赖公共 CDN；本次加载成功，但离线或受限网络会影响页面脚本。
- Three.js 旧版全局构建入口已弃用，后续前端依赖升级时需要处理。
- Session 存储在单个 Tomcat 进程内，本阶段只能验证单实例。
- MySQL 初始化脚本只适用于新的空数据目录，不能替代数据库迁移工具。
- 验证使用开发隔离网络和随机临时密码，不代表生产 TLS、Secret 管理、备份恢复或高可用能力已通过。

## 7. Phase 2B 验收状态

| 范围 | 状态 |
| --- | --- |
| Level 1 静态验证 | 通过 |
| Environment Fix | 通过，四个指定镜像均拉取成功 |
| Level 2 动态验证 | 通过 |
| 当前项目状态 | Phase 2B Complete，等待 Tech Lead Review |
| Phase 2B 完整容器验收 | 完成 |
| Phase 2C Docker Compose | 未开始，等待 Tech Lead 决定 |

## 8. 参考资料

- [Docker Multi-stage builds](https://docs.docker.com/build/building/multi-stage/)
- [Docker Build cache optimization](https://docs.docker.com/build/cache/optimize/)
- [Dockerfile reference](https://docs.docker.com/reference/dockerfile/)
- [Official Tomcat Docker image source](https://github.com/docker-library/tomcat)
- [Eclipse Temurin container images](https://github.com/adoptium/containers)
