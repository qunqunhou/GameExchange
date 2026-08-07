# GameExchange 文档索引

本目录用于保存需要长期维护的工程文档。根目录 `README.md` 负责项目介绍和运行入口，本文件负责文档导航与维护规则。

## 当前文档

| 文档 | 用途 | 更新时机 |
| --- | --- | --- |
| [项目 README](../README.md) | 项目用途、环境要求、配置和运行方式 | 构建、配置或运行方式变化时 |
| [数据库迁移说明](../DATABASE_MIGRATION.md) | 数据库基线、历史 Migration 顺序、验证和风险 | 数据库结构或迁移顺序变化时 |
| [代码审查报告](../CODE_REVIEW.md) | Milestone 1.1 的架构、数据库关系和技术债快照 | 保留审查结论，不覆盖为当前状态 |
| [变更日志](../CHANGELOG.md) | 记录已经实施的工程升级和兼容性变化 | 每个 Milestone 或发布完成时 |
| [Docker 验证记录](docker/VERIFY.md) | Phase 2B 的静态证据、动态验证计划和验收状态 | 每次完成新的 Docker 验证时 |
| [Compose 验证记录](docker/COMPOSE_VERIFY.md) | Phase 2C 的静态配置和 Runtime Verification 证据 | 每次完成新的 Compose 验证时 |
| [生产部署手册](deployment/PRODUCTION_ECS.md) | 单 ECS 生产部署、Secret、备份、验证和回滚基线 | 生产拓扑、部署流程或回滚边界变化时 |
| [监控运行手册](observability/MONITORING.md) | Prometheus、Grafana、指标、Dashboard 和排错方式 | 指标、监控配置或运行方式变化时 |
| [RC2 Release Checklist](releases/1.0.0-rc2/RELEASE_CHECKLIST.md) | RC2 Gate、制品身份、已知限制和最终决策 | RC2 Gate、审批、Tag 或归档状态变化时 |
| [RC2 Release Approval](releases/1.0.0-rc2/RELEASE_APPROVAL.md) | RC2 人工审批与风险接受记录 | Reviewer 完成审核或风险决策时 |

## 文档职责

- 根目录 `README.md` 是新接手人员的第一入口，内容应保持简洁、可执行。
- `CODE_REVIEW.md` 是指定时间点的审查报告，用于解释升级起点和技术债来源。
- `CHANGELOG.md` 只记录已经发生的变化，不记录尚未批准或尚未实施的计划。
- `docs/` 保存后续形成的架构、数据库、部署和运维专题文档。
- `docs/docker/VERIFY.md` 区分已验证结果与待执行项目，不把静态检查记录为容器运行成功。
- `docs/docker/COMPOSE_VERIFY.md` 在 Step 3 前保持 Runtime Pending，不把 `docker compose config` 记录为运行成功。
- `docs/observability/MONITORING.md` 只记录已落地的指标、监控组件和真实验证结果。

## 维护规则

1. 构建命令、运行环境、Context Path 或配置入口发生变化时，同步更新根目录 `README.md`。
2. 每次完成工程升级后，在 `CHANGELOG.md` 的 `Unreleased` 部分记录真实变更。
3. 只有当专题内容已经有实际实现依据时，才新增对应文档，不创建空占位文件。
4. 文档中的命令必须能够从当前项目验证；无法验证的步骤应明确标注限制。
5. 文档不得包含真实密码、Token、服务器密钥、远程服务器地址或个人环境凭据。
6. 新增、移动或重命名文档时，必须同步检查所有相对链接。

## 后续文档边界

以下文档应在对应能力真正实施时建立，而不是在当前阶段提前编写：

- Kubernetes 文档：完成镜像、配置、健康检查和运行边界设计后新增。
- 告警文档：Alertmanager、告警规则和通知链路实际落地后新增。
- CI/CD 文档：流水线实际运行并具备回滚方式后新增。

这样可以避免文档描述尚不存在的能力，也能让后续维护者明确每份文档对应的真实实现。
