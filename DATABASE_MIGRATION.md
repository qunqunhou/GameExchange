# GameExchange 数据库 Migration 管理

## 文档目的

本文档定义 `game_exchange` 数据库的初始化方式、存量环境升级顺序和 Migration 执行规则。仓库当前未引入自动 Migration 工具或 `schema_version` 表，因此执行记录由发布流程保存。

数据库脚本分为两类：

- Baseline：`database/schema.sql` 和 `database/seed.sql`，用于新环境初始化。
- Historical Migration：`database/migrations/*.sql`，仅用于符合前置版本的已存在环境升级。

## Migration 执行规则

1. 执行前确认目标数据库、当前版本和已执行记录，并完成可恢复备份。
2. Migration 必须按照本文档顺序执行，每个脚本只执行一次；已经成功执行的脚本不得重复执行。
3. 执行前阅读脚本中的 Preflight、快照和 Guard 要求。任何检查不满足或 SQL 报错时必须停止，不得跳过错误继续执行。
4. 历史 Migration 视为只读发布制品，不得为适配某个环境而修改原文件。后续修复应新增独立 Migration 并单独评审。
5. 发布记录至少应保存目标环境、脚本文件名、文件校验值、执行人、执行时间、执行结果和验证证据。
6. 本项目当前不使用数据库内的 `schema_version` 表；不得以缺少该表为由重复执行历史 Migration。

## 新环境初始化

新建数据库或全新的空 MySQL Volume 只按以下顺序执行：

```text
database/schema.sql
        |
database/seed.sql
```

不要对新环境执行 `database/migrations/*.sql`。当前 `schema.sql` 已包含最终表结构、Presence Lease 字段、Battle 幂等记录表、索引、`utf8mb4` 字符集契约和最终 CHECK 约束；`seed.sql` 已包含字符集安全的当前种子数据。历史 Migration 的前置结构或历史脏数据在新环境中不存在，重复执行可能失败或破坏正确数据。

手动初始化示例：

```powershell
mysql --default-character-set=utf8mb4 -u <username> -p --execute="SOURCE database/schema.sql"
mysql --default-character-set=utf8mb4 -u <username> -p --execute="SOURCE database/seed.sql"
```

Compose 仅在空 MySQL Volume 首次初始化时自动加载这两个 Baseline 文件；已有 Volume 不会因容器重启而重新执行初始化脚本。

## 已存在环境升级

从 Phase 3A 之前的旧数据库升级到当前状态时，必须依次执行：

```text
phase-3a-add-player-last-seen-at.sql
        |
phase-db-repair-item-rarity-check.sql
        |
phase-4c-drop-player-online-status.sql
        |
phase-5a-data-repair-charset-mojibake.sql
        |
phase-5c-add-battle-record.sql
```

处于中间版本的环境应根据已保存的执行记录，从下一个尚未执行的 Migration 开始；不得从头重放。执行每一步后都应完成脚本要求的验证并保存证据，验证失败时不得进入下一步。

## Migration 清单

| 执行顺序 | 文件名 | 目的 | 影响对象 | 修改 Schema | 修改数据 | 风险说明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `phase-3a-add-player-last-seen-at.sql` | 增加 Presence Lease 时间事实来源及查询索引 | `player.last_seen_at`、`idx_player_last_seen_at` | 是 | 否，历史行保持 `NULL` | 非幂等；列或索引已存在时会失败，并且必须在删除 `online_status` 前执行 |
| 2 | `phase-db-repair-item-rarity-check.sql` | 修复历史 rarity 双重编码并重建字符集安全的 CHECK | `item.rarity`、`chk_item_rarity`，仅目标 `id=1,2,3` | 是 | 是，精确修复 3 条 rarity | MySQL DDL 会隐式提交；中途失败可能使 CHECK 暂时缺失，必须保存结构和数据快照并核对验证查询 |
| 3 | `phase-4c-drop-player-online-status.sql` | 删除 Presence Legacy 字段及约束 | `player.online_status`、`chk_player_online_status` | 是 | 是，删除该 Legacy 列及其历史值 | 不可直接回退；执行前必须确认应用已完全切换到 `last_seen_at` 并保存快照 |
| 4 | `phase-5a-data-repair-charset-mojibake.sql` | 修复已确认的历史中文双重编码数据 | `item.item_name` 的 3 条记录、`game_event.event_desc` 的 2 条记录 | 否 | 是，精确修复 5 条记录 | 仅适用于完整旧 HEX 匹配的目标环境；任一 Guard 或行数检查失败即回滚，不得改为全表自动转换 |
| 5 | `phase-5c-add-battle-record.sql` | 建立 Battle 请求幂等数据库契约 | `battle_record` 表、唯一键、查询索引、外键和金币 CHECK | 是 | 否，只创建空表 | MySQL DDL 会隐式提交且脚本非幂等；执行时会短暂获取 `player`、`item` 的 metadata lock，回滚需单独评审删除新表的风险 |

## Migration 详细说明

### `phase-3a-add-player-last-seen-at.sql`

- 为 `player` 增加可空的 `last_seen_at` 字段和 `idx_player_last_seen_at` 索引。
- 不回填历史玩家，避免把未建立 Lease 的账号误判为在线。
- 脚本依赖旧结构中的 `online_status` 定位新字段，因此必须先于 Phase 4C 执行。

### `phase-db-repair-item-rarity-check.sql`

- 删除历史字符集错误的 `chk_item_rarity`，精确修复 `item.id IN (1,2,3)` 的 rarity，再使用 `_utf8mb4` 常量重建 CHECK。
- 同时修改表约束和 3 条已确认数据。
- 添加新 CHECK 前的验证查询必须返回 0 行；否则应停止并调查，不得继续执行。

### `phase-4c-drop-player-online-status.sql`

- 删除 `chk_player_online_status` 和 `player.online_status`。
- 完成后，`last_seen_at + 60s Lease` 是唯一在线事实来源。
- 删除列会永久移除该列的历史值，必须在应用和统计读路径完成切换后执行。

### `phase-5a-data-repair-charset-mojibake.sql`

- 只修复 Preflight 已确认的 5 条历史中文乱码：3 条 `item.item_name` 和 2 条 `game_event.event_desc`。
- 每条更新都使用完整旧 HEX Guard，不使用 `CONVERT()`、`CAST()`、前缀匹配或全表推测转换。
- 所有数据修复位于同一事务，包含目标行锁、更新行数检查、修复后 HEX 验证及失败回滚。
- 不改变任何表结构。该修复已在当前已验证环境完成，新环境不得执行此脚本。

### `phase-5c-add-battle-record.sql`

- 创建空的 `battle_record` 表，为每个玩家的 Battle 请求保存持久化 `request_id` 和服务端奖励结果。
- `uk_battle_record_player_request(player_id, request_id)` 是并发请求和应用重启后仍有效的幂等边界；`request_id` 使用 ASCII 二进制排序规则进行精确比较。
- 外键只引用已有的 `player` 和 `item`，Migration 不更新、不删除任何历史业务数据。
- MySQL DDL 会隐式提交，普通 `ROLLBACK` 不能撤销建表。若应用尚未写入记录，可在确认依赖和备份后单独评审删除新表；一旦产生 Battle 记录，删除表会丢失幂等和审计事实，不应作为常规回滚方式。

## 当前数据库版本

当前仓库 Baseline 的目标状态为：

- `player.last_seen_at` 及其索引存在，`player.online_status` 已删除。
- `item.rarity` CHECK 使用 `_utf8mb4` 常量。
- Schema、表和字符列采用 `utf8mb4` 契约。
- Phase 5A 历史中文乱码修复已完成。
- `battle_record` 提供 `(player_id, request_id)` 唯一键、玩家/掉落装备外键和金币非负约束。

新环境通过 `schema.sql` 和 `seed.sql` 直接达到该状态，不需要也不得重放上述历史 Migration。已存在数据库的实际版本以发布执行记录为准；只有成功执行并记录 `phase-5c-add-battle-record.sql` 后，才具备 `battle_record` 契约，不能以仓库中存在脚本代替执行证据。
