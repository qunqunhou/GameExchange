-- GameExchange 最小种子数据
-- 目标版本：MySQL 8.4
-- 初始化卖家的密码由数据库随机生成，不提供固定登录凭据。

USE game_exchange;

START TRANSACTION;

INSERT INTO player (username, password, gold, online_status)
SELECT 'seed_seller', UUID(), 1000, 0
WHERE NOT EXISTS (
    SELECT 1
    FROM player
    WHERE username = 'seed_seller'
);

SET @seed_seller_id = (
    SELECT id
    FROM player
    WHERE username = 'seed_seller'
    LIMIT 1
);

INSERT INTO item (item_name, rarity, owner_id)
SELECT '新手铁剑', '普通', @seed_seller_id
WHERE @seed_seller_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM item
      WHERE item_name = '新手铁剑'
        AND owner_id = @seed_seller_id
  );

INSERT INTO item (item_name, rarity, owner_id)
SELECT '星尘长剑', '稀有', @seed_seller_id
WHERE @seed_seller_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM item
      WHERE item_name = '星尘长剑'
        AND owner_id = @seed_seller_id
  );

INSERT INTO item (item_name, rarity, owner_id)
SELECT '龙鳞护甲', '史诗', @seed_seller_id
WHERE @seed_seller_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM item
      WHERE item_name = '龙鳞护甲'
        AND owner_id = @seed_seller_id
  );

SET @seed_market_item_id = (
    SELECT id
    FROM item
    WHERE item_name = '星尘长剑'
      AND owner_id = @seed_seller_id
    ORDER BY id
    LIMIT 1
);

INSERT INTO market (item_id, seller_id, price, status)
SELECT @seed_market_item_id, @seed_seller_id, 200, 'ON_SALE'
WHERE @seed_market_item_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM market
      WHERE item_id = @seed_market_item_id
  );

INSERT INTO game_event (player_name, event_type, event_desc)
SELECT 'seed_seller', 'ITEM_DROP', '获得【稀有】装备：星尘长剑'
WHERE NOT EXISTS (
    SELECT 1
    FROM game_event
    WHERE player_name = 'seed_seller'
      AND event_type = 'ITEM_DROP'
      AND event_desc = '获得【稀有】装备：星尘长剑'
);

INSERT INTO game_event (player_name, event_type, event_desc)
SELECT 'seed_seller', 'ITEM_DROP', '获得【史诗】装备：龙鳞护甲'
WHERE NOT EXISTS (
    SELECT 1
    FROM game_event
    WHERE player_name = 'seed_seller'
      AND event_type = 'ITEM_DROP'
      AND event_desc = '获得【史诗】装备：龙鳞护甲'
);

COMMIT;
