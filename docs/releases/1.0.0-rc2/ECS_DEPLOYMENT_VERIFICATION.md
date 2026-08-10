# GameExchange 1.0.0-rc2 ECS Deployment Verification

## 1. 验证范围

本文档记录 P3.2-C/D 在阿里云 ECS 上完成的部署准备、App-only 镜像切换、无域名私有验收，以及 P3.3-B/C 完成的 OSS 离机备份和隔离恢复演练证据。该结论仅表示 `GameExchange 1.0.0-rc2` 已通过作品集私有部署和一次恢复演练验证，不代表生产就绪、高可用、可信 TLS、公网服务能力或正式 RPO/RTO 已经完成。

| 字段 | 值 |
| --- | --- |
| Verification Date | `2026-08-10` |
| Operator / Reviewer | `SMzhiman` |
| Status | `PRIVATE_VALIDATION_AND_RESTORE_PASSED` |
| ECS Platform | Ubuntu 22.04，`x86_64` |
| Deployment Mode | 单 ECS、Docker Compose、无域名 SSH Tunnel 私有验收 |
| Approved App Image | `crpi-npa4w6l8amsghlzs.cn-hangzhou.personal.cr.aliyuncs.com/smzhiman/gameexchange@sha256:b920c62c349ef0b89591932f7bac0b030b7b134394363b321842ef55b62e6ac4` |

上游发布与拉取身份分别见 [ACR_PUBLICATION.md](ACR_PUBLICATION.md) 和 [ECS_PULL_VERIFICATION.md](ECS_PULL_VERIFICATION.md)。原始 RC2 签署结论保留在 [RELEASE_APPROVAL.md](RELEASE_APPROVAL.md)，本次后续部署不回写或重新解释签署时点的历史证据。

## 2. 部署准备与回滚边界

P3.2-C 先对在线 Compose、挂载、文件权限、运行用户和回滚镜像进行只读审计，再准备 App-only 候选配置。整个准备过程没有重建 App 或 MySQL。

| 检查项 | 实际结果 | 状态 |
| --- | --- | --- |
| 在线项目 | `gameexchange-prod` 运行 App 与 MySQL；`gameexchange-monitor` 运行 Prometheus、Node Exporter 与 Grafana | `PASSED` |
| 在线 Compose | `/opt/gameexchange/docker-compose.prod.yaml` 与仓库文件的字节级 SHA-256 不同，在线配置单独完成渲染审计 | `RECORDED` |
| Schema | `/opt/gameexchange/database/schema.sql` SHA-256 为 `F915D672C7C6DD3F9CB0E4B96B96F9E936198E264A69CB1EC52B541F268C5541`，与仓库一致 | `PASSED` |
| 环境文件 | `/opt/gameexchange/prod.env` 为 `root:root 0600`；只归档键名和允许公开的非敏感值 | `PASSED` |
| Secret | App Secret 为 `root:10001 0640`，Root Secret 为 `root:root 0600`；未读取或归档 Secret 内容 | `PASSED` |
| App 运行用户 | 容器内为固定 `uid=10001(gameexchange)`、`gid=10001(gameexchange)` | `PASSED` |
| MySQL 数据目录 | `/data/gameexchange/mysql` 绑定到 `/var/lib/mysql`，来源位于独立 ext4 数据盘 | `PASSED` |
| 候选差异 | 归一化后 Base 与 Candidate 配置 SHA-256 均为 `506E8388C249AA177CCBAE8DDA1419796D0725646CEE425E19A21987C6E8A17A`，仅 App 镜像身份不同 | `PASSED` |

回滚准备生成以下受限文件：

| 文件 | Owner / Mode | 用途 |
| --- | --- | --- |
| `/var/backups/gameexchange/config/p3.2-c3-20260809T085419Z/docker-compose.prod.yaml` | `root:root 0600` | 部署前 Compose 快照 |
| `/var/backups/gameexchange/config/p3.2-c3-20260809T085419Z/prod.env` | `root:root 0600` | 部署前环境文件快照 |
| `/opt/gameexchange/docker-compose.rc2-app-override.yaml` | `root:root 0600` | 只覆盖 App 镜像为批准的 RC2 Digest |

Base App 使用旧镜像 `sha256:fba59bd644daab0be7bd68980977bc3d38c36ab78ab1859370a957feeb7c000c`，Candidate App 使用批准镜像 `sha256:b920c62c349ef0b89591932f7bac0b030b7b134394363b321842ef55b62e6ac4`。MySQL 镜像、用户、Secret 和数据目录在候选配置中保持不变。

## 3. 数据库备份与部署前探测

P3.2-D1 在部署前生成一致性逻辑备份，并验证当前业务基线和 RC2 的非 root、Secret 边界。

| 检查项 | 实际结果 | 状态 |
| --- | --- | --- |
| 数据表 | `6` 张，`NonInnoDB=0` | `PASSED` |
| 备份文件 | `/var/backups/gameexchange/database/game_exchange-20260809T091913Z.sql.gz` | `CREATED` |
| Owner / Mode | `root:root 0600` | `PASSED` |
| 文件大小 | `1758` bytes | `RECORDED` |
| SHA-256 | `2ce399ab8368f8b721aa4bba106513f7fe35cddf63f40a2eee3385f2c65d7690` | `PASSED` |
| 旧 App Smoke | 当前业务基线通过 | `PASSED` |
| RC2 临时探针 | 固定用户与文件型 Secret 边界通过，临时容器完成清理 | `PASSED` |

该备份在 P3.2-D1 时仍与生产数据位于同一台 ECS，只能提供本次发布前的本机回退材料。P3.3-B/C 已在后续步骤完成 OSS 离机复制和一次隔离恢复演练，详见第 7 节；该后续证据降低了 `KL-04` 的未验证范围，但不等同于已经建立生产级 RPO/RTO。

## 4. App-only 部署结果

P3.2-D2 使用 Base Compose 加 RC2 App Override 执行 `--no-deps --pull never` 的 App-only 更新。部署只重建 App，不拉取镜像、不登录 ACR，也不操作 MySQL 服务。

| 检查项 | 部署前 | 部署后 | 状态 |
| --- | --- | --- | --- |
| App Container ID | `3d2c620e5f6acd971f643310512354cecdd957fbc94e5dfb8efb45f7b4e2cfba` | `a4debcd27e82cedacda04b38933d05bbf2889fe3bf99c47e1a877e71a88c53b4` | `CHANGED` |
| App Image ID | `sha256:fba59bd644daab0be7bd68980977bc3d38c36ab78ab1859370a957feeb7c000c` | `sha256:b920c62c349ef0b89591932f7bac0b030b7b134394363b321842ef55b62e6ac4` | `PASSED` |
| App Started At | `2026-08-06T05:59:17.246678076Z` | `2026-08-09T09:39:39.956457464Z` | `CHANGED` |
| App Runtime | `running / healthy` | `running / healthy` | `PASSED` |
| MySQL Container ID | `aa536147f5f7f2925eeed42455ab0aa5ffde5091bf21e1b6a6646716feac74db` | 相同 | `UNCHANGED` |
| MySQL Image ID | `sha256:870634c634aae968ea1a93e5c094a14e00c692da2ee9bed956b3dfcc7bd08cb0` | 相同 | `UNCHANGED` |
| MySQL Started At | `2026-08-06T05:18:56.771402977Z` | 相同 | `UNCHANGED` |
| 静态资源探测 | `/vue/assets/js/app-config.js` 成功 | 部署后成功 | `PASSED` |
| 数据库业务探测 | `/stats` 的 JSON `code` 为 `200` | 部署后为 `200` | `PASSED` |

Compose 在约 `8.7s` 内报告新 App 为 `Healthy`。部署成功，因此自动回滚分支没有触发；旧镜像、基础 Compose 和配置快照均已保留，但不能把“已准备自动回滚”记录为“本次已实际执行并验证回滚”。单实例 App 重建会终止旧 Session，该影响已由 Owner 在部署前接受。

## 5. 无域名私有验收

P3.2-D3 从部署工作站建立本机 `127.0.0.1:18080` 到 ECS `127.0.0.1:8080` 的 SSH Tunnel。隧道关闭后不会改变 ECS 上的 App 状态。

| 检查项 | 实际结果 | 状态 |
| --- | --- | --- |
| 登录页 | HTTP `200` | `PASSED` |
| 静态配置 | HTTP `200` | `PASSED` |
| `/stats` | JSON `code=200` | `PASSED` |
| ECS 公网 `8080/tcp` | 不可连接 | `PASSED` |
| ECS 公网 `3306/tcp` | 不可连接 | `PASSED` |
| 浏览器检查 | 登录页正常渲染；本步骤未归档账号或个人数据 | `PASSED` |
| SSH Tunnel 清理 | 验收后由 Operator 使用 `Ctrl+C` 关闭 | `PASSED` |

本次按 Owner 要求不采集截图。文本化 HTTP、业务码和端口结果作为本步骤证据；该范围不包含完整浏览器 UI E2E。

## 6. 基础 Compose 配置收敛

P3.2-E1 在私有验收通过后，将基础 `/opt/gameexchange/prod.env` 的 `APP_IMAGE` 从旧镜像更新为批准的 RC2 Digest。该步骤只收敛期望配置，不执行 `docker compose up`，也不重建任何容器。

| 检查项 | 实际结果 | 状态 |
| --- | --- | --- |
| 配置快照 | `/var/backups/gameexchange/config/p3.2-e1-20260809T104757Z/prod.env` | `CREATED` |
| 快照权限 | `root:root 0600` | `PASSED` |
| 基础 `APP_IMAGE` | `crpi-npa4w6l8amsghlzs.cn-hangzhou.personal.cr.aliyuncs.com/smzhiman/gameexchange@sha256:b920c62c349ef0b89591932f7bac0b030b7b134394363b321842ef55b62e6ac4` | `PASSED` |
| 环境文件边界 | `APP_IMAGE` 保持唯一；其余内容校验一致；`prod.env` 保持 `root:root 0600` | `PASSED` |
| App 指纹 | Container ID、Image ID、`running / healthy` 和 Started At 均未变化 | `UNCHANGED` |
| MySQL 指纹 | Container ID、Image ID、`running / healthy` 和 Started At 均未变化 | `UNCHANGED` |
| 业务探测 | 静态配置与 `/stats` 的 JSON `code=200` 通过 | `PASSED` |
| 运行时操作 | 未执行 Compose Up，未重建 App 或 MySQL，无中断或 Session 失效 | `UNCHANGED` |

App Override 继续作为 D2 部署历史与回退材料保留，但基础 Compose 现已能单独渲染批准的 RC2 镜像，不再依赖该 Override 才能表达当前期望状态。

## 7. OSS 离机备份与隔离恢复演练

P3.3-B1/B2 使用阿里云 OSS 私有 Bucket 归档 P3.2-D1 备份，并完成下载回验。P3.3-C1 使用 OSS 回读副本执行隔离 MySQL 与 RC2 App 恢复演练。该演练只验证单次手工恢复链路，不代表自动化备份、定期演练、正式 RPO/RTO 或高可用已经完成。

| 检查项 | 实际结果 | 状态 |
| --- | --- | --- |
| OSS Bucket | `gameexchange-rc2-backup-3ee98609`，华东 1（杭州），私有读写，阻止公共访问，版本控制开启 | `PASSED` |
| 加密与保留 | OSS 完全托管 AES256；生命周期规则 `backup-history-retention-30d` 清理历史版本和删除标记 | `PASSED` |
| 访问方式 | ECS 临时绑定 `GameExchangeEcsOssBackupRole`，策略仅允许 `database/rc2/*` 的 `PutObject` / `GetObject`；完成后解绑 | `PASSED` |
| ossutil | 官方 `v1.7.19` Linux x86_64，SHA-256 `0970061c150262d67e2ff46045ce1237f1cbf0df4fcd9f126802a1dc7e0036bf`；使用杭州内网 HTTPS Endpoint | `PASSED` |
| OSS 主备份 | `database/rc2/game_exchange-20260809T091913Z.sql.gz`，`1758` bytes，SHA-256 `2ce399ab8368f8b721aa4bba106513f7fe35cddf63f40a2eee3385f2c65d7690` | `PASSED` |
| OSS 校验文件 | `database/rc2/game_exchange-20260809T091913Z.sql.gz.sha256`，`104` bytes | `PASSED` |
| 回读验证 | 下载到 `/var/backups/gameexchange/verify/p3.3-b2/` 后通过 SHA-256、`gzip -t` 和字节级比较 | `PASSED` |
| 恢复 MySQL | 临时容器 `gameexchange-restore-mysql-p3-3-c1`，internal 网络，独立目录 `/data/gameexchange-restore/p3.3-c1/mysql`，未挂载生产数据目录 | `PASSED` |
| 导入结果 | `ImportSeconds=0`；MySQL `8.4.10`，`utf8mb4` / `utf8mb4_0900_ai_ci`；6 张表全部 `InnoDB` | `PASSED` |
| 恢复行数 | `player=1`，`item=0`，`market=0`，`trade_record=0`，`battle_record=0`，`game_event=0` | `RECORDED` |
| 关键约束 | `battle_record` 外键、唯一键和 `chk_battle_record_gold_reward` 均恢复 | `PASSED` |
| 恢复 App | 使用批准 RC2 App Image ID `sha256:b920c62c349ef0b89591932f7bac0b030b7b134394363b321842ef55b62e6ac4` 连接恢复库 | `PASSED` |
| App Smoke | 已有非特权玩家账号登录 `LoginCode=200`；玩家信息 `PlayerInfoCode=200`；市场只读 `MarketHTTP=200`，`MarketItems=0` | `PASSED` |
| 端口异常处理 | 恢复 App 宿主端口发布未实际监听，最终改用恢复网络内部 IP 验证；未暴露公网 | `RECORDED` |
| 清理 | 恢复 App、恢复 MySQL、internal 网络和 `/data/gameexchange-restore/p3.3-c1` 已删除 | `PASSED` |
| 生产影响 | App/MySQL 容器 ID、Image ID、Started At、`RestartCount=0` 和 `running/healthy` 前后一致 | `UNCHANGED` |

`ossutil stat` 在最小权限角色下会额外请求 `GetObjectACL` 并返回 `AccessDenied`。本次按最小权限原则不授予 ACL 读取权限，改用直接下载、SHA-256、`gzip -t` 和字节级比较证明 `GetObject` 有效。

## 8. 当前运行边界

- 基础 `/opt/gameexchange/prod.env` 已直接引用批准的 RC2 Digest；`docker-compose.rc2-app-override.yaml` 继续保留，但不再是基础配置正确渲染 RC2 的必要条件。
- App、Grafana 和 Prometheus 继续只绑定 ECS 回环地址；MySQL 未发布宿主机端口。
- SSH Tunnel 只证明受控私有访问，不代表 DNS、可信 TLS、Nginx 公网入口或外部探测已经通过。
- 当前仍是单 ECS、单 App 实例和本机 MySQL，存在节点级单点，不具备高可用能力。
- 数据库备份已完成一次 OSS 离机归档和隔离恢复演练；自动化备份、周期性恢复演练、生产容量和正式 RPO/RTO 仍未验证。
- 本次没有执行完整浏览器业务 E2E、生产流量测试或容量测试。
- [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) 中原始 `KL-01` 至 `KL-09` 的签署记录保持不变；后续 ACR 与 ECS 证据只补充新的时间点，不覆盖历史决策。

## 9. 结论

`GameExchange 1.0.0-rc2` 已按批准的不可变 ACR Digest 部署到目标 ECS，App 健康、数据库业务探测通过、MySQL 运行身份保持不变，并完成无域名 SSH Tunnel 私有验收、基础 Compose 配置收敛、OSS 离机备份上传回读以及一次隔离恢复演练。最终状态为 `PRIVATE_VALIDATION_AND_RESTORE_PASSED`。

该结论仅适用于作品集技术预发布。正式公网运行前仍需完成域名与可信 TLS、入口代理、外部监控、自动化备份、周期性恢复演练、容量验证以及与目标 SLA 相匹配的高可用设计。详细运行边界见 [PRODUCTION_ECS.md](../../deployment/PRODUCTION_ECS.md)。
