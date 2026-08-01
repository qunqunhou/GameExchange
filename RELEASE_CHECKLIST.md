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
| Git Commit (Artifact) | `6cc2c88dd01bd6aea29f4e58d591031e6275079e` |
| Documentation Commit | `f3d1534fe579ba9e38d27bbead57a023b9de0ebf` |
| Git Tag | `BLOCKED - not created` |
| Verification Date | `2026-08-01` |
| Release Gate Evidence | `target/release-gate/20260801-194735-296-859df335/` |
| Docker Runtime Evidence | `target/docker-runtime/20260801-195124-baecc6d3/` |
| Artifact Manifest | `target/release-manifest/20260801-195218-985-d4cd4758/release-manifest.json` |

Artifact Identity 来源于 commit `6cc2c88dd01bd6aea29f4e58d591031e6275079e`。Documentation Freeze 来源于 commit `f3d1534fe579ba9e38d27bbead57a023b9de0ebf`，不改变已验证的 Artifact Gate 结果。

### Verified Artifact Identity

| Artifact | Identity |
| --- | --- |
| RC Image | `gameexchange-rc:1.0.0-rc1-6cc2c88dd01b-20260801-194735-296-859df335` |
| Image ID | `sha256:bae834eaf90a8dbb15a154524f0ace9ae8b4e5247469f39b8f9d333e35703ba2` |
| Image Digest | `sha256:bae834eaf90a8dbb15a154524f0ace9ae8b4e5247469f39b8f9d333e35703ba2` |
| WAR | `target/GameExchange_war-1.0.0-rc1.war` |
| WAR SHA-256 | `C27AF41FC6B52C842E7AD687F1B6C176DEB1A88558E41C6BBCBF24A4D758F452` |

### Gate Summary

| Gate | Status |
| --- | --- |
| Build Gate | `PASSED` |
| Test Gate | `PASSED` |
| Database Gate | `PASSED` |
| Docker Runtime Gate | `PASSED` |
| Artifact Gate | `PASSED` |

## 1. Release Identity

| Gate | Acceptance Criteria | Evidence | Status |
| --- | --- | --- | --- |
| Version | 版本唯一、不是 `SNAPSHOT`，并与 Maven、WAR 和 Image 版本一致 | RC Version 与 Artifact Manifest 均记录 `1.0.0-rc1` | `PASSED` |
| Git Commit | 记录完整 commit hash，且所有验证均基于该 commit | `target/release-gate/20260801-194735-296-859df335/git-identity.txt` | `PASSED` |
| Git Tag | Tag 唯一，并精确指向已记录的 Git Commit | 本阶段未创建 Tag | `BLOCKED` |
| Working Tree Status | `git status --porcelain` 无输出，无未跟踪或未提交文件 | `target/release-gate/20260801-194735-296-859df335/git-status.txt` | `PASSED` |

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
| KL-01 | 未验证生产 TLS 配置 | 本地 HTTP 验证不能证明生产传输链路安全 | `Waiting for risk acceptance owner.` | `BLOCKED` |
| KL-02 | 未验证外部 Secret 管理 | 当前验证不能证明生产凭据的分发、轮换和撤销流程 | `Waiting for risk acceptance owner.` | `BLOCKED` |
| KL-03 | 未验证高可用部署 | 单实例通过不能证明节点故障时的服务连续性 | `Waiting for risk acceptance owner.` | `BLOCKED` |
| KL-04 | 未验证备份恢复流程 | 当前验证不能证明数据可在故障后按目标恢复 | `Waiting for risk acceptance owner.` | `BLOCKED` |
| KL-05 | 未执行真实生产流量测试 | 隔离 Runtime 结果不能代表生产负载下的容量与稳定性 | `Waiting for risk acceptance owner.` | `BLOCKED` |
| KL-06 | 浏览器 UI 端到端流程未实现完整自动化 | 前端资源加载或交互回归可能未被 API 测试发现 | `Waiting for risk acceptance owner.` | `BLOCKED` |
| KL-07 | Release Gate 尚未接入 CI | 手工执行可能受到主机状态和操作差异影响 | `Waiting for risk acceptance owner.` | `BLOCKED` |

## 8. Approval

| Approval | Reviewer | Date | Status | Evidence / Comment |
| --- | --- | --- | --- | --- |
| Technical Approval | `<required>` | `<YYYY-MM-DD>` | `BLOCKED` | `Waiting for human approval.` |
| Database Approval | `<required>` | `<YYYY-MM-DD>` | `BLOCKED` | `Waiting for human approval.` |
| Runtime Approval | `<required>` | `<YYYY-MM-DD>` | `BLOCKED` | `Waiting for human approval.` |
| Release Owner Approval | `<required>` | `<YYYY-MM-DD>` | `BLOCKED` | `Waiting for human approval.` |

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
