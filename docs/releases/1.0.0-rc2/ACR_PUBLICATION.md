# GameExchange 1.0.0-rc2 ACR Publication Evidence

## 1. 归档范围

本文档记录 P3.2-A 对 `GameExchange 1.0.0-rc2` 已批准镜像的后续 ACR 发布与身份回验。该操作只建立可从 Registry 拉取的不可变制品，不代表 ECS 部署、生产就绪或高可用验收已经完成。

| 字段 | 值 |
| --- | --- |
| Publication Date | `2026-08-09` |
| Operator / Reviewer | `SMzhiman` |
| Status | `PASSED` |
| Release Scope | 作品集技术预发布，不代表生产就绪 |
| Artifact Commit | `562b503a911be996c5b96f6307413265ecdf4caa` |
| Platform | `linux/amd64` |

## 2. 镜像身份

| 字段 | 值 |
| --- | --- |
| Approved Source Image | `gameexchange-rc:1.0.0-rc2-562b503a911b-20260807-222130-114-a5ccab13` |
| Approved Image ID | `sha256:b920c62c349ef0b89591932f7bac0b030b7b134394363b321842ef55b62e6ac4` |
| ACR Repository | `crpi-npa4w6l8amsghlzs.cn-hangzhou.personal.cr.aliyuncs.com/smzhiman/gameexchange` |
| ACR Unique Tag | `1.0.0-rc2-562b503a911b-20260807-222130-114-a5ccab13` |
| Registry Manifest Digest | `sha256:b920c62c349ef0b89591932f7bac0b030b7b134394363b321842ef55b62e6ac4` |
| Immutable Pull Reference | `crpi-npa4w6l8amsghlzs.cn-hangzhou.personal.cr.aliyuncs.com/smzhiman/gameexchange@sha256:b920c62c349ef0b89591932f7bac0b030b7b134394363b321842ef55b62e6ac4` |

本次 Registry Manifest Digest 与本地 Image ID 的十六进制值恰好相同，但两者仍按不同身份字段记录。后续发布不得假设 Registry Manifest Digest 必然等于 Image ID，必须继续通过按 Digest 拉取和 `docker image inspect` 建立跨环境身份链。

## 3. 验证记录

| 检查项 | 实际结果 | 状态 |
| --- | --- | --- |
| 发布前 Image ID | 已批准源镜像的 Image ID 与 Release Gate 记录一致 | `PASSED` |
| 历史 Tag 保护 | 已有 `1.0.0-rc2` 未被删除、覆盖或作为本次发布目标 | `PASSED` |
| 唯一 Tag 冲突检查 | 发布前 `docker manifest inspect` 返回 `no such manifest`，退出码为 `1` | `PASSED` |
| ACR 身份验证 | 使用交互式安全输入和 `--password-stdin` 登录成功，未记录凭据 | `PASSED` |
| Push | 唯一 Tag 推送成功，返回上述 Registry Manifest Digest，Manifest Size 为 `856` | `PASSED` |
| Digest Pull | 按不可变 Digest 拉取成功，Docker 返回 `Image is up to date` | `PASSED` |
| 拉回后 Image ID | `sha256:b920c62c349ef0b89591932f7bac0b030b7b134394363b321842ef55b62e6ac4`，与批准值一致 | `PASSED` |
| 凭据清理 | 验证完成后执行 `docker logout`，Docker 返回已移除登录凭据 | `PASSED` |

## 4. 历史审批关系

`RELEASE_APPROVAL.md` 和 `RELEASE_CHECKLIST.md` 中的 `KL-09` 记录了 `2026-08-07` RC2 签署时尚未发布 Registry 镜像的真实状态。P3.2-A 是在新的逐步审批下完成的后续发布，因此不回写、不删除也不重新签署原 `KL-09`；该限制在原审批时间点仍然成立，在当前部署轨道中则由本次 ACR 发布与身份回验证据继续承接。

## 5. 当前边界

- 当前状态为 `READY_FOR_ECS_PULL_REVIEW`，只允许进入 ECS 拉取方案设计和审批。
- 尚未授权 ECS 登录 ACR、拉取镜像、修改 `prod.env`、渲染生产 Compose 或部署 RC2。
- 旧 `1.0.0-rc2` Tag 的镜像身份仍未验证，不得用于当前部署或回滚。
- 无正式域名、可信 TLS、外部探测、备份恢复演练、生产容量和高可用能力的边界保持不变。
- ACR 用户名、密码、Token、AccessKey 和 Docker 凭据文件均不属于归档内容。
