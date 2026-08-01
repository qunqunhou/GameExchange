-- GameExchange 数据库结构基线
-- 目标版本：MySQL 8.4
-- 本脚本只负责创建缺失的数据库对象，不删除或覆盖已有业务数据。

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE DATABASE IF NOT EXISTS game_exchange
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE game_exchange;

CREATE TABLE IF NOT EXISTS player (
    id INT NOT NULL AUTO_INCREMENT,
    username VARCHAR(16) NOT NULL,
    password VARCHAR(255) NOT NULL,
    gold BIGINT NOT NULL DEFAULT 1000,
    last_seen_at DATETIME NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_player_username (username),
    KEY idx_player_last_seen_at (last_seen_at),
    CONSTRAINT chk_player_gold CHECK (gold >= 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS item (
    id INT NOT NULL AUTO_INCREMENT,
    item_name VARCHAR(100) NOT NULL,
    rarity VARCHAR(16) NOT NULL,
    owner_id INT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_item_owner_id (owner_id),
    KEY idx_item_rarity_id (rarity, id),
    CONSTRAINT fk_item_owner
        FOREIGN KEY (owner_id) REFERENCES player (id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,
    CONSTRAINT chk_item_rarity
        CHECK (rarity IN (
            _utf8mb4'普通',
            _utf8mb4'稀有',
            _utf8mb4'史诗',
            _utf8mb4'传说'
        ))
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS battle_record (
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

CREATE TABLE IF NOT EXISTS market (
    id INT NOT NULL AUTO_INCREMENT,
    item_id INT NOT NULL,
    seller_id INT NOT NULL,
    price BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ON_SALE',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_market_item_id (item_id),
    KEY idx_market_seller_id (seller_id),
    KEY idx_market_status_create_time (status, create_time),
    CONSTRAINT fk_market_item
        FOREIGN KEY (item_id) REFERENCES item (id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,
    CONSTRAINT fk_market_seller
        FOREIGN KEY (seller_id) REFERENCES player (id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,
    CONSTRAINT chk_market_price CHECK (price > 0),
    CONSTRAINT chk_market_status CHECK (status IN ('ON_SALE', 'SOLD'))
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS trade_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    buyer_id INT NOT NULL,
    seller_id INT NOT NULL,
    item_id INT NOT NULL,
    price BIGINT NOT NULL,
    trade_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_trade_record_buyer_id (buyer_id),
    KEY idx_trade_record_seller_id (seller_id),
    KEY idx_trade_record_item_id (item_id),
    KEY idx_trade_record_trade_time (trade_time),
    CONSTRAINT fk_trade_record_buyer
        FOREIGN KEY (buyer_id) REFERENCES player (id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,
    CONSTRAINT fk_trade_record_seller
        FOREIGN KEY (seller_id) REFERENCES player (id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,
    CONSTRAINT fk_trade_record_item
        FOREIGN KEY (item_id) REFERENCES item (id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,
    CONSTRAINT chk_trade_record_price CHECK (price > 0),
    CONSTRAINT chk_trade_record_participants CHECK (buyer_id <> seller_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS game_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    player_name VARCHAR(16) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    event_desc VARCHAR(255) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_game_event_create_time (create_time),
    KEY idx_game_event_type_create_time (event_type, create_time)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
