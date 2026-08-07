# GameExchange 1.0.0-rc2 Release Approval

本文档记录 `GameExchange 1.0.0-rc2` 的人工审批。`SMzhiman` 已明确确认 RC2 仅作为作品集技术预发布、不代表生产就绪，并接受 `KL-01` 至 `KL-09`。本项目由单一 Owner 完成自审，未实施企业团队中的职责分离。

## Release Identity

| 字段 | 值 |
| --- | --- |
| Version | `1.0.0-rc2` |
| Artifact Commit | `562b503a911be996c5b96f6307413265ecdf4caa` |
| WAR SHA-256 | `45E0A9B7EC2AF012A5E636527CF2A2C9A675667E3E7D4BAF556665BE9AAED8F6` |
| Image Digest | `sha256:b920c62c349ef0b89591932f7bac0b030b7b134394363b321842ef55b62e6ac4` |
| Release Gate | `PASSED` |
| Checklist | [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) |

## Technical Approval

| 字段 | 值 |
| --- | --- |
| Reviewer | `SMzhiman` |
| Status | `APPROVED` |
| Date | `2026-08-07` |
| Comment | 已审核 RC2 Commit、42 项单元测试和 21 项集成测试证据。 |
| Signature | `SMzhiman` |

## Database Approval

| 字段 | 值 |
| --- | --- |
| Reviewer | `SMzhiman` |
| Status | `APPROVED` |
| Date | `2026-08-07` |
| Comment | 已审核 Baseline、Upgrade、Migration Guard 和 5 个 Migration 身份。 |
| Signature | `SMzhiman` |

## Runtime Approval

| 字段 | 值 |
| --- | --- |
| Reviewer | `SMzhiman` |
| Status | `APPROVED` |
| Date | `2026-08-07` |
| Comment | 已审核 Docker Runtime、非 root 用户、重启恢复、数据卷复用和清理证据。 |
| Signature | `SMzhiman` |

## Release Owner Approval

| 字段 | 值 |
| --- | --- |
| Reviewer | `SMzhiman` |
| Status | `APPROVED` |
| Date | `2026-08-07` |
| Comment | 批准作品集技术预发布，接受全部已知限制，并授权后续 Tag 与确切制品归档。 |
| Signature | `SMzhiman` |

## Known Limitation Acceptance

| ID | Risk Owner | 状态 | 日期 | 说明 |
| --- | --- | --- | --- | --- |
| KL-01 | `SMzhiman` | `ACCEPTED` | `2026-08-07` | 生产 TLS 不在本次技术预发布验收范围。 |
| KL-02 | `SMzhiman` | `ACCEPTED` | `2026-08-07` | 接受外部 Secret 生命周期尚未完成生产验证。 |
| KL-03 | `SMzhiman` | `ACCEPTED` | `2026-08-07` | 接受单实例边界，不宣称具备高可用。 |
| KL-04 | `SMzhiman` | `ACCEPTED` | `2026-08-07` | 接受未执行备份恢复演练，不宣称 RPO/RTO。 |
| KL-05 | `SMzhiman` | `ACCEPTED` | `2026-08-07` | 接受未执行生产容量测试，不提供生产 SLA。 |
| KL-06 | `SMzhiman` | `ACCEPTED` | `2026-08-07` | 接受未执行完整浏览器 UI E2E。 |
| KL-07 | `SMzhiman` | `ACCEPTED` | `2026-08-07` | 接受完整 Release Gate 仍由人工执行。 |
| KL-08 | `SMzhiman` | `ACCEPTED` | `2026-08-07` | 批准归档 Gate 生成的确切 WAR，禁止重建替换。 |
| KL-09 | `SMzhiman` | `ACCEPTED` | `2026-08-07` | 接受本次不发布 Registry 镜像。 |

## Sign-off Rule

只有满足以下条件后，才能将 Release Owner Approval 更新为 `APPROVED`，并授权创建 Tag 和归档制品：

1. Technical、Database 和 Runtime Approval 均由明确 Reviewer 签署。
2. `KL-01` 至 `KL-09` 均被明确接受，或对应风险已经通过新的验证消除。
3. Release Owner 已核对 Artifact Commit、WAR SHA-256 和 Image Digest。
4. Release Owner 已明确批准 Annotated Tag 和制品归档操作。

Tag 和归档完成后，还必须回填 Checklist 的 Tag、Archive 和 Final Decision 字段，整体发布状态才能由 `BLOCKED` 更新为 `PASSED`。

## Approval Result

| 字段 | 值 |
| --- | --- |
| Approval Status | `APPROVED` |
| Release Scope | 作品集技术预发布，不代表生产就绪 |
| Release Owner | `SMzhiman` |
| Approval Date | `2026-08-07` |
| Authorized Actions | 创建指向 Artifact Commit 的 Annotated Tag；归档 Gate 生成的确切 WAR、Manifest 和 SHA-256 校验文件 |
| Remaining Finalization | `COMPLETED` |
| Documentation Commit | `1dacb3aa5ec625559d76f8a774bcb337244f1d55` |
| Git Tag | `v1.0.0-rc2`（Annotated Tag，指向 `562b503a911be996c5b96f6307413265ecdf4caa`） |
| Artifact Archive | [GitHub Pre-release](https://github.com/qunqunhou/GameExchange/releases/tag/v1.0.0-rc2) |
| Finalization Date | `2026-08-08` |
