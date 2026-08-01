-- Phase 3A：为存量数据库增加玩家在线租约时间。
-- 本脚本是一次性增量迁移；执行前应确认目标库尚不存在该列和索引。
-- 历史数据保持 NULL，避免迁移时把未建立租约的玩家误判为在线。

USE game_exchange;

ALTER TABLE player
    ADD COLUMN last_seen_at DATETIME NULL DEFAULT NULL AFTER online_status,
    ADD INDEX idx_player_last_seen_at (last_seen_at);
