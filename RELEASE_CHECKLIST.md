# GameExchange Release Candidate Checklist

## Status Semantics

| Status | Definition |
| --- | --- |
| `BLOCKED` | Gate 未执行、执行失败、结果不匹配、证据缺失或风险尚未批准。禁止发布。 |
| `PASSED` | Gate 的命令成功、验收条件全部满足、证据已保存并完成 Review。 |

所有 Gate 初始状态均为 `BLOCKED`。只有所有 Required Gate 和 Approval 均为 `PASSED` 时，RC 总状态才能标记为 `PASSED`。不得根据历史结果、口头确认或部分成功推断通过。

## Release Record

| Field | Value |
| --- | --- |
| Overall Status | `BLOCKED` |
| RC Gate Status | `PASSED` |
| RC Version | `1.0.0-rc1` |
| Git Commit | `153ca9ad0345d71c8a050e398d5eb32a96e371cd` |
| Git Tag | `BLOCKED - not created` |
| Verification Date | `2026-08-01` |
| Release Gate Evidence | `target/release-gate/20260801-184550-076-d0d614fb/` |
| Docker Runtime Evidence | `target/docker-runtime/20260801-184954-a8d138fc/` |
| Artifact Manifest | `target/release-manifest/20260801-185047-907-957c30b4/release-manifest.json` |

### Verified Artifact Identity

| Artifact | Identity |
| --- | --- |
| RC Image | `gameexchange-rc:153ca9ad0345-20260801-184550-076-d0d614fb` |
| Image ID | `sha256:c4f181caa44efb220bd02387ed2b092fe3c09d73b27fcd821181ac84d4ab10b2` |
| Image Digest | `sha256:c4f181caa44efb220bd02387ed2b092fe3c09d73b27fcd821181ac84d4ab10b2` |
| WAR | `target/GameExchange_war-1.0-SNAPSHOT.war` |
| WAR SHA-256 | `F0D6F39DAC681C8E868AC2FB6B900B51E98AAD07D96ED925CA792FAD3635A407` |

## 1. Release Identity

| Gate | Acceptance Criteria | Evidence | Status |
| --- | --- | --- | --- |
| Version | 版本唯一、不是 `SNAPSHOT`，并与构建制品及 Git Tag 一致 | RC Version 已记录，但 WAR 仍为 `1.0-SNAPSHOT` | `BLOCKED` |
| Git Commit | 记录完整 commit hash，且所有验证均基于该 commit | `target/release-gate/20260801-184550-076-d0d614fb/git-identity.txt` | `PASSED` |
| Git Tag | Tag 唯一，并精确指向已记录的 Git Commit | 本阶段未创建 Tag | `BLOCKED` |
| Working Tree Status | `git status --porcelain` 无输出，无未跟踪或未提交文件 | `target/release-gate/20260801-184550-076-d0d614fb/git-status.txt` | `PASSED` |

## 2. Build Verification

Maven build command：

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
```

| Gate | Acceptance Criteria | Evidence | Status |
| --- | --- | --- | --- |
| Maven Environment | Java 与 Maven Wrapper 版本已记录 | Release Gate `java-version.txt`、`maven-version.txt` | `PASSED` |
| Maven Build | 命令 Exit Code 为 0，日志包含 `BUILD SUCCESS` | Release Gate `maven-verify.log` | `PASSED` |
| WAR Output | 只接受本次 clean build 生成的 WAR | Release Gate `war-artifact.txt` | `PASSED` |
| WAR SHA-256 Record | WAR SHA-256 已写入统一校验清单 | Release Gate `war-artifact.txt` | `PASSED` |

## 3. Test Verification

| Gate | Acceptance Criteria | Evidence | Status |
| --- | --- | --- | --- |
| Unit Test | Surefire 全部通过，无 Failure/Error；Skipped 必须有说明 | Surefire：36 tests，0 failure/error/skipped | `PASSED` |
| Integration Test | Failsafe `*IT` 全部通过，无 Failure/Error；Skipped 必须有说明 | Failsafe：21 tests，0 failure/error/skipped | `PASSED` |
| Migration Test | `DatabaseBaselineIT` 与 `DatabaseUpgradeIT` 均执行并通过 | Failsafe 报告及 Migration 测试日志 | `PASSED` |
| Test Summary | Test 数量、Failure、Error、Skipped 已汇总并与报告一致 | Release Gate `test-summary.txt` | `PASSED` |

## 4. Database Verification

| Gate | Acceptance Criteria | Evidence | Status |
| --- | --- | --- | --- |
| Baseline Migration | 空 MySQL 8.4 环境通过 CLI 执行 `schema.sql`、`seed.sql`；表、约束、字符集和 Seed HEX 验证通过 | `DatabaseBaselineIT` 报告 | `PASSED` |
| Upgrade Migration | 真实 Phase 1.5 Fixture 按文档顺序升级到当前状态 | `DatabaseUpgradeIT` Happy Path 报告 | `PASSED` |
| Migration Guard | Phase 5A HEX Guard 异常路径能够阻断后续 Phase 5C Migration | `DatabaseUpgradeIT` Failure Path 报告 | `PASSED` |
| Isolation | 未连接开发数据库，未使用或修改 `gameexchange_mysql-data` | Testcontainers 与 Runtime 隔离证据 | `PASSED` |

## 5. Docker Runtime Verification

| Gate | Acceptance Criteria | Evidence | Status |
| --- | --- | --- | --- |
| Image Identity | 记录不可变 RC image tag、image ID 和 digest；Runtime 使用同一预构建镜像 | Release Gate `image-identity.txt` | `PASSED` |
| Runtime Result | 隔离 Compose project 的 `result.txt` 为 `status=PASS` | Docker Runtime `result.txt` | `PASSED` |
| Container Runtime | MySQL 与 App 为 `healthy`；App PID 1 UID 为 `10001`；`ROOT.war` 存在 | Docker Runtime Inspect 与 Health evidence | `PASSED` |
| HTTP Smoke | 静态资源、登录页和 `/stats` 为 HTTP 200，Stats JSON `code=200` | Docker Runtime HTTP evidence | `PASSED` |
| Restart Recovery | App 进程退出后 `RestartCount` 增加并恢复 `healthy` | Docker Runtime `restart.txt` | `PASSED` |
| Volume Persistence | Sentinel 保留、Seed 未重复执行、隔离 Volume 行为符合预期 | Docker Runtime `volume-persistence.txt` | `PASSED` |
| Cleanup | 临时 Container、Network、Volume 均为 0，开发 Volume 未被访问或删除 | Docker Runtime `cleanup.txt` | `PASSED` |

## 6. Artifact Verification

| Gate | Acceptance Criteria | Evidence | Status |
| --- | --- | --- | --- |
| WAR Hash | WAR SHA-256 与本次构建记录一致 | Artifact Manifest 与 Release Gate `war-artifact.txt` | `PASSED` |
| Image Digest | 被验证与拟发布镜像的 immutable digest 完全一致 | Artifact Manifest 与 Release Gate `image-identity.txt` | `PASSED` |
| Migration Hash | `database/migrations/*.sql` 的 SHA-256、文件名和执行顺序全部记录 | Artifact Manifest：5 个 Migration | `PASSED` |
| Commit Binding | WAR、Image、Migration hash 与 Release Record 中的 Git Commit 绑定 | `release-manifest.json`，状态 `complete` | `PASSED` |

## 7. Known Limitations

Known Limitation 只有在影响、处置方式和责任人明确并完成风险批准后才能标记为 `PASSED`。

| ID | Uncovered Area | Risk | Mitigation / Acceptance Owner | Status |
| --- | --- | --- | --- | --- |
| KL-01 | 生产 TLS、外部 Secret、备份恢复和高可用不属于本地 RC Runtime 范围 | 本地通过不代表生产运维就绪 | `<required>` | `BLOCKED` |
| KL-02 | 浏览器 UI 端到端流程未实现完整自动化 | 前端资源加载或交互回归可能未被 API 测试发现 | `<required>` | `BLOCKED` |
| KL-03 | Release Gate 尚未接入 CI | 手工执行可能受到主机状态和操作差异影响 | `<required>` | `BLOCKED` |
| KL-04 | `<additional limitation or none>` | `<required>` | `<required>` | `BLOCKED` |

## 8. Approval

| Approval | Reviewer | Date | Status | Evidence / Comment |
| --- | --- | --- | --- | --- |
| Technical Review | `<required>` | `<YYYY-MM-DD>` | `BLOCKED` | `<required>` |
| Database Review | `<required>` | `<YYYY-MM-DD>` | `BLOCKED` | `<required>` |
| Runtime Review | `<required>` | `<YYYY-MM-DD>` | `BLOCKED` | `<required>` |
| Release Approval | `<required>` | `<YYYY-MM-DD>` | `BLOCKED` | `<required>` |

## Final Decision

| Field | Value |
| --- | --- |
| Final Status | `BLOCKED` |
| Approved Version | `<required>` |
| Approved Commit | `<required>` |
| Approved Image Digest | `<required>` |
| Release Approver | `<required>` |
| Approval Date | `<required: YYYY-MM-DD>` |

`Final Status` 只能在本清单所有 Required Gate、Known Limitation 风险接受和 Approval 均为 `PASSED` 后改为 `PASSED`。
