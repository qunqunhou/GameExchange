# GameExchange 1.0.0-rc2 Release Checklist

本文档记录 `GameExchange 1.0.0-rc2` 的发布门禁、制品身份、已知限制和最终决策。Required Gate、人工审批、风险接受、Git Tag 和制品归档均已完成，RC2 作为作品集技术预发布归档。

## 状态语义

| 状态 | 含义 |
| --- | --- |
| `BLOCKED` | 条件未满足或证据缺失，禁止进入发布。 |
| `PASSED` | 自动化 Gate 已执行成功，证据与验收条件一致。 |
| `APPROVED` | 指定 Reviewer 已完成审核并签署。 |
| `ACCEPTED` | Risk Owner 已明确接受已知限制。 |

只有 Required Gate 全部为 `PASSED`、人工审批全部为 `APPROVED`、已知限制全部为 `ACCEPTED`，并且 Git Tag 与制品归档完成后，整体状态才能改为 `PASSED`。

## Release Record

| 字段 | 值 |
| --- | --- |
| Overall Status | `PASSED` |
| RC Gate Status | `PASSED` |
| RC Version | `1.0.0-rc2` |
| Artifact Commit | `562b503a911be996c5b96f6307413265ecdf4caa` |
| Artifact Branch | `main` |
| Documentation Commit | `1dacb3aa5ec625559d76f8a774bcb337244f1d55` |
| Git Tag | `v1.0.0-rc2` |
| Tag Target Commit | `562b503a911be996c5b96f6307413265ecdf4caa` |
| Tag Type | `annotated tag` |
| Release URL | [GitHub Pre-release](https://github.com/qunqunhou/GameExchange/releases/tag/v1.0.0-rc2) |
| Verification Date | `2026-08-07` |
| Finalization Date | `2026-08-08` |
| Release Gate Evidence | `target/release-gate/20260807-222130-114-a5ccab13/` |
| Docker Runtime Evidence | `target/docker-runtime/20260807-222507-ee3113cd/` |
| Artifact Manifest | `target/release-manifest/20260807-222602-313-b9d5b44f/release-manifest.json` |
| Artifact Archive | `PASSED`，WAR、Manifest 和 `SHA256SUMS.txt` 已归档到 GitHub Pre-release |

## Verified Artifact Identity

| 制品 | 身份 |
| --- | --- |
| WAR | `target/GameExchange_war-1.0.0-rc2.war` |
| WAR Size | `12239800` bytes |
| WAR SHA-256 | `45E0A9B7EC2AF012A5E636527CF2A2C9A675667E3E7D4BAF556665BE9AAED8F6` |
| Manifest Archive | `release-manifest-1.0.0-rc2.json` |
| Manifest Size | `5455` bytes |
| Manifest SHA-256 | `8CC3821C2622556849CBE4A02363EEEA1F2C4925ABDA4160349F96B561E5F3EA` |
| Checksum Archive | `SHA256SUMS.txt` |
| RC Image | `gameexchange-rc:1.0.0-rc2-562b503a911b-20260807-222130-114-a5ccab13` |
| Image ID | `sha256:b920c62c349ef0b89591932f7bac0b030b7b134394363b321842ef55b62e6ac4` |
| Image Digest | `sha256:b920c62c349ef0b89591932f7bac0b030b7b134394363b321842ef55b62e6ac4` |
| Runtime User | `gameexchange:gameexchange` |
| Migration Count | `5` |

以上身份只适用于本次 Gate 生成并验证的具体制品。不得使用重新构建但 Hash 不同的文件替换该 WAR。

## Gate Summary

| Gate | 验收结果 | 状态 |
| --- | --- | --- |
| Git Identity | Commit、分支和干净工作区与 Manifest 一致 | `PASSED` |
| Maven Build | `clean verify` 返回 Exit Code `0`，日志包含 `BUILD SUCCESS` | `PASSED` |
| Unit Test | `42` tests，`0` failure/error/skipped | `PASSED` |
| Integration Test | `21` tests，`0` failure/error/skipped | `PASSED` |
| Database | Baseline、Upgrade、Migration Guard 与隔离验证通过 | `PASSED` |
| Docker Image | 镜像构建成功，Image ID/Digest 已记录 | `PASSED` |
| Docker Runtime | MySQL/App 健康、HTTP Smoke、重启恢复和数据卷复用通过 | `PASSED` |
| Runtime Security | App 使用 `gameexchange:gameexchange` 非 root 用户 | `PASSED` |
| Cleanup | 隔离 Container、Network、Volume 均为 `0` | `PASSED` |
| Artifact Manifest | 版本、Commit、WAR、Image 和 Migration 身份完整 | `PASSED` |

## Known Limitations

Verification Status 为 `BLOCKED` 表示该能力不在本次 Gate 的已验证范围内。`PARTIALLY_VERIFIED` 表示后续补充过有限范围的实测证据，但仍保留未覆盖的生产边界。只有 Risk Owner 明确签署 `ACCEPTED` 后，它才不会阻塞 RC2。

| ID | 未覆盖范围 | 主要风险 | Verification Status |
| --- | --- | --- | --- |
| KL-01 | 未验证真实生产 TLS | 本地 HTTP 不能证明生产传输链路安全 | `BLOCKED` |
| KL-02 | 未验证外部 Secret 分发与轮换 | 当前结果不能证明生产凭据生命周期安全 | `BLOCKED` |
| KL-03 | 未验证高可用部署 | 单实例通过不能证明节点故障时服务连续 | `BLOCKED` |
| KL-04 | 已完成一次 OSS 回读副本隔离恢复演练；未验证自动化备份、周期性演练和正式 RPO/RTO | 单次手工恢复不能证明持续恢复能力或故障恢复目标 | `PARTIALLY_VERIFIED` |
| KL-05 | 未执行生产流量和容量测试 | 隔离 Smoke Test 不能代表生产负载表现 | `BLOCKED` |
| KL-06 | 未执行完整浏览器 UI E2E | 静态资源或交互回归可能未被 API 测试发现 | `BLOCKED` |
| KL-07 | 完整 Release Gate 尚未接入 CI | Gate 仍依赖人工在指定主机执行 | `BLOCKED` |
| KL-08 | WAR 尚未实现可复现构建 | 相同 Commit 重新构建可能产生不同 SHA-256 | `BLOCKED` |
| KL-09 | RC2 镜像尚未发布到 Registry | 本地 Image ID 不能作为跨环境可拉取制品 | `BLOCKED` |

## Risk Acceptance

| ID | Risk Owner | 状态 | 日期 | 说明 |
| --- | --- | --- | --- | --- |
| KL-01 | `SMzhiman` | `ACCEPTED` | `2026-08-07` | RC2 仅作为作品集技术预发布，生产 TLS 不在本次验收范围。 |
| KL-02 | `SMzhiman` | `ACCEPTED` | `2026-08-07` | 接受外部 Secret 生命周期尚未完成生产验证。 |
| KL-03 | `SMzhiman` | `ACCEPTED` | `2026-08-07` | 接受当前单实例边界，不将 RC2 宣称为高可用部署。 |
| KL-04 | `SMzhiman` | `ACCEPTED` | `2026-08-07` | 原始签署时接受备份恢复尚无真实演练结果；P3.3-C1 于 `2026-08-10` 完成一次基于 OSS 回读副本的隔离恢复演练，但仍不宣称已达到生产 RPO/RTO。 |
| KL-05 | `SMzhiman` | `ACCEPTED` | `2026-08-07` | 接受未执行生产容量测试，不提供生产 SLA。 |
| KL-06 | `SMzhiman` | `ACCEPTED` | `2026-08-07` | 接受未执行完整浏览器 UI E2E，保留现有自动化与 Smoke Test 证据。 |
| KL-07 | `SMzhiman` | `ACCEPTED` | `2026-08-07` | 接受完整 Gate 仍为人工执行，远端 CI 仅提供核心质量门禁。 |
| KL-08 | `SMzhiman` | `ACCEPTED` | `2026-08-07` | 以归档 Gate 生成的确切 WAR 为前提，禁止重新构建后替换。 |
| KL-09 | `SMzhiman` | `ACCEPTED` | `2026-08-07` | 接受 RC2 镜像仅用于本地验证，本次不作为 Registry 可拉取制品。 |

## Approval

人工审批记录位于 [RELEASE_APPROVAL.md](RELEASE_APPROVAL.md)。本项目由单一 Owner 完成自审，未实施企业团队中的职责分离。

| Approval | Reviewer | 日期 | 状态 |
| --- | --- | --- | --- |
| Technical Approval | `SMzhiman` | `2026-08-07` | `APPROVED` |
| Database Approval | `SMzhiman` | `2026-08-07` | `APPROVED` |
| Runtime Approval | `SMzhiman` | `2026-08-07` | `APPROVED` |
| Release Owner Approval | `SMzhiman` | `2026-08-07` | `APPROVED` |

## Final Decision

| 字段 | 值 |
| --- | --- |
| Final Status | `PASSED` |
| Approved Version | `1.0.0-rc2` |
| Approved Commit | `562b503a911be996c5b96f6307413265ecdf4caa` |
| Approved WAR SHA-256 | `45E0A9B7EC2AF012A5E636527CF2A2C9A675667E3E7D4BAF556665BE9AAED8F6` |
| Approved Image Digest | `sha256:b920c62c349ef0b89591932f7bac0b030b7b134394363b321842ef55b62e6ac4` |
| Release Approver | `SMzhiman` |
| Approval Date | `2026-08-07` |
| Release URL | [GitHub Pre-release](https://github.com/qunqunhou/GameExchange/releases/tag/v1.0.0-rc2) |

RC2 发布归档已完成。该结论仅表示作品集技术预发布门禁通过，不代表生产就绪；`KL-01` 至 `KL-09` 的适用边界继续有效。
