-- Phase 5C.6A：为存量数据库增加战斗请求幂等记录表。
-- 本脚本只创建空表，不更新 player、item 或其他历史业务数据。
-- MySQL DDL 会隐式提交；执行前必须确认 battle_record 尚不存在。

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE game_exchange;

CREATE TABLE battle_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    player_id INT NOT NULL,
    request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    monster_name VARCHAR(32) NOT NULL,
    gold_reward BIGINT NOT NULL,
    loot_item_id INT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_battle_record_player_request (player_id, request_id),
    KEY idx_battle_record_player_created_at (player_id, created_at),
    CONSTRAINT fk_battle_record_player
        FOREIGN KEY (player_id) REFERENCES player (id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,
    CONSTRAINT fk_battle_record_loot_item
        FOREIGN KEY (loot_item_id) REFERENCES item (id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,
    CONSTRAINT chk_battle_record_gold_reward CHECK (gold_reward >= 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

SHOW CREATE TABLE battle_record;

SELECT COUNT(*) AS battle_record_count
FROM battle_record;
