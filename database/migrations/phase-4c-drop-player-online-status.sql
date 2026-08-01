-- Phase 4C：删除 player.online_status Legacy 字段。
-- 执行前必须保存 SHOW CREATE TABLE player 和 online_status 数据快照。

USE game_exchange;

ALTER TABLE player
    DROP CHECK chk_player_online_status,
    DROP COLUMN online_status;
