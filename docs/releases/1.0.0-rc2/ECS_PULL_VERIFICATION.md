# GameExchange 1.0.0-rc2 ECS Pull Verification

## 1. 验证范围

本文档记录 P3.2-B 在阿里云 ECS 上按不可变 ACR Digest 拉取 `GameExchange 1.0.0-rc2` 镜像的验证证据。本步骤只把已批准镜像加入 ECS Docker 镜像存储，不修改部署配置、不重建容器，也不代表 RC2 已经部署。

| 字段 | 值 |
| --- | --- |
| Verification Date | `2026-08-09` |
| Operator / Reviewer | `SMzhiman` |
| Status | `PASSED` |
| ECS Platform | Ubuntu 22.04，`x86_64` |
| Docker | Client/Server `29.7.1` |
| Immutable Pull Reference | `crpi-npa4w6l8amsghlzs.cn-hangzhou.personal.cr.aliyuncs.com/smzhiman/gameexchange@sha256:b920c62c349ef0b89591932f7bac0b030b7b134394363b321842ef55b62e6ac4` |

ACR 上游发布身份与验证见 [ACR_PUBLICATION.md](ACR_PUBLICATION.md)。

## 2. 拉取前置检查

| 检查项 | 实际结果 | 状态 |
| --- | --- | --- |
| 系统时间 | NTP 同步为 `yes` | `PASSED` |
| Docker Root | `/var/lib/docker`，所在 ext4 文件系统剩余约 `31G` | `PASSED` |
| 数据盘 | `/data` 所在 ext4 文件系统剩余约 `37G` | `PASSED` |
| Docker 当前占用 | 镜像约 `2.994GB`，运行容器 `5` 个 | `PASSED` |
| 目标镜像 | 拉取前 ECS 本地不存在目标 Digest | `PASSED` |
| ACR DNS/TLS | DNS 解析成功；未认证 `/v2/` 返回预期 HTTP `401` | `PASSED` |
| root Docker 配置 | `/root/.docker/config.json` 为 `root:root 0600`，未读取内容 | `PASSED` |

## 3. 认证失败与恢复

首次隔离登录返回 `unauthorized: authentication required`。流程在 login 阶段停止，未执行 Pull，并自动 logout 和清理临时配置。确认使用 ACR 访问凭证登录名和固定密码后，单独的 login-only 探测成功，退出码为 `0`，临时目录清理完成。根因确定为首次凭据输入不匹配，而不是 DNS、TLS、Registry 地址、账号类型或 Docker 权限故障。

该失败没有通过重复 Pull、公开仓库、覆盖 root Docker 配置或关闭认证绕过。最终 Pull 只在根因确认和 login-only 验证通过后执行。

## 4. Digest 拉取与身份验证

| 检查项 | 实际结果 | 状态 |
| --- | --- | --- |
| Pull 工作流 | `PullWorkflowExit=0` | `PASSED` |
| Pulled Image ID | `sha256:b920c62c349ef0b89591932f7bac0b030b7b134394363b321842ef55b62e6ac4` | `PASSED` |
| RepoDigest | 与批准的 ACR Registry Manifest Digest 一致 | `PASSED` |
| 平台 | `linux/amd64` | `PASSED` |
| 临时配置 | `/run/gameexchange-acr-pull.*` 已删除 | `PASSED` |
| root Docker 配置 | 工作流前后内容校验一致，权限保持 `root:root 0600` | `PASSED` |

## 5. 无部署证明

| 检查项 | 实际结果 | 状态 |
| --- | --- | --- |
| App Container ID | `3d2c620e5f6acd971f643310512354cecdd957fbc94e5dfb8efb45f7b4e2cfba` | `UNCHANGED` |
| Running App Image ID | `sha256:fba59bd644daab0be7bd68980977bc3d38c36ab78ab1859370a957feeb7c000c` | `UNCHANGED` |
| App Health | `healthy`，静态配置访问检查为 `PASSED` | `PASSED` |
| App Started At | `2026-08-06T05:59:17.246678076Z` | `UNCHANGED` |

新 RC2 镜像已存在于 ECS，但当前 App 仍运行历史镜像。未执行 `docker compose up`、容器重建、服务重启或 `prod.env` 修改。

## 6. 当前边界

- 当前状态为 `READY_FOR_DEPLOY_CONFIG_REVIEW`，只允许进入生产配置和 Compose 渲染方案设计。
- 尚未授权修改 `/opt/gameexchange` 部署配置、创建 Secret、切换 App 镜像或部署 RC2。
- ACR 用户名、密码、Token、Docker 配置内容及配置文件哈希值均未归档。
- 无域名、可信 TLS、外部探测、备份恢复演练、生产容量和高可用能力的限制保持不变。
