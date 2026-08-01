SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE game_exchange;

-- Phase 5A.4：仅修复 Preflight 已确认的五条历史双重编码数据。
-- 执行流程必须保存以下结构与数据快照，且不得忽略 SQL 错误。
SHOW CREATE TABLE item;
SHOW CREATE TABLE game_event;

SELECT id, HEX(item_name) AS item_name_hex, item_name
FROM item
WHERE id IN (1, 2, 3)
ORDER BY id;

SELECT id, HEX(event_desc) AS event_desc_hex, event_desc
FROM game_event
WHERE id IN (1, 2)
ORDER BY id;

START TRANSACTION;

-- 锁定目标行，避免检查与修复之间发生并发变更。
SELECT id, HEX(item_name) AS item_name_hex, item_name
FROM item
WHERE id IN (1, 2, 3)
ORDER BY id
FOR UPDATE;

SELECT id, HEX(event_desc) AS event_desc_hex, event_desc
FROM game_event
WHERE id IN (1, 2)
ORDER BY id
FOR UPDATE;

SET @phase_5a_item_precheck_count = (
    SELECT COUNT(*)
    FROM item
    WHERE (id = 1 AND HEX(item_name) =
            'C3A6E28093C2B0C3A6E280B0E280B9C3A9E2809CC281C3A5E280B0E28098')
       OR (id = 2 AND HEX(item_name) =
            'C3A6CB9CC5B8C3A5C2B0CB9CC3A9E280A2C2BFC3A5E280B0E28098')
       OR (id = 3 AND HEX(item_name) =
            'C3A9C2BEE284A2C3A9C2B3C5BEC3A6C5A0C2A4C3A7E2809DC2B2')
);

SET @phase_5a_event_precheck_count = (
    SELECT COUNT(*)
    FROM game_event
    WHERE (id = 1 AND HEX(event_desc) =
            'C3A8C5BDC2B7C3A5C2BEE28094C3A3E282ACC290C3A7C2A8E282ACC3A6C593E280B0C3A3E282ACE28098C3A8C2A3E280A6C3A5C2A4E280A1C3AFC2BCC5A1C3A6CB9CC5B8C3A5C2B0CB9CC3A9E280A2C2BFC3A5E280B0E28098')
       OR (id = 2 AND HEX(event_desc) =
            'C3A8C5BDC2B7C3A5C2BEE28094C3A3E282ACC290C3A5C28FC2B2C3A8C2AFE28094C3A3E282ACE28098C3A8C2A3E280A6C3A5C2A4E280A1C3AFC2BCC5A1C3A9C2BEE284A2C3A9C2B3C5BEC3A6C5A0C2A4C3A7E2809DC2B2')
);

SET @phase_5a_precheck_ok = (
    @phase_5a_item_precheck_count = 3
    AND @phase_5a_event_precheck_count = 2
);

SELECT @phase_5a_item_precheck_count AS item_precheck_count,
       @phase_5a_event_precheck_count AS event_precheck_count,
       @phase_5a_precheck_ok AS precheck_ok;

UPDATE item
SET item_name = _utf8mb4'新手铁剑'
WHERE id = 1
  AND HEX(item_name) =
      'C3A6E28093C2B0C3A6E280B0E280B9C3A9E2809CC281C3A5E280B0E28098'
  AND @phase_5a_precheck_ok = 1;
SET @phase_5a_item_1_updated = ROW_COUNT();

UPDATE item
SET item_name = _utf8mb4'星尘长剑'
WHERE id = 2
  AND HEX(item_name) =
      'C3A6CB9CC5B8C3A5C2B0CB9CC3A9E280A2C2BFC3A5E280B0E28098'
  AND @phase_5a_precheck_ok = 1;
SET @phase_5a_item_2_updated = ROW_COUNT();

UPDATE item
SET item_name = _utf8mb4'龙鳞护甲'
WHERE id = 3
  AND HEX(item_name) =
      'C3A9C2BEE284A2C3A9C2B3C5BEC3A6C5A0C2A4C3A7E2809DC2B2'
  AND @phase_5a_precheck_ok = 1;
SET @phase_5a_item_3_updated = ROW_COUNT();

UPDATE game_event
SET event_desc = _utf8mb4'获得【稀有】装备：星尘长剑'
WHERE id = 1
  AND HEX(event_desc) =
      'C3A8C5BDC2B7C3A5C2BEE28094C3A3E282ACC290C3A7C2A8E282ACC3A6C593E280B0C3A3E282ACE28098C3A8C2A3E280A6C3A5C2A4E280A1C3AFC2BCC5A1C3A6CB9CC5B8C3A5C2B0CB9CC3A9E280A2C2BFC3A5E280B0E28098'
  AND @phase_5a_precheck_ok = 1;
SET @phase_5a_event_1_updated = ROW_COUNT();

UPDATE game_event
SET event_desc = _utf8mb4'获得【史诗】装备：龙鳞护甲'
WHERE id = 2
  AND HEX(event_desc) =
      'C3A8C5BDC2B7C3A5C2BEE28094C3A3E282ACC290C3A5C28FC2B2C3A8C2AFE28094C3A3E282ACE28098C3A8C2A3E280A6C3A5C2A4E280A1C3AFC2BCC5A1C3A9C2BEE284A2C3A9C2B3C5BEC3A6C5A0C2A4C3A7E2809DC2B2'
  AND @phase_5a_precheck_ok = 1;
SET @phase_5a_event_2_updated = ROW_COUNT();

SET @phase_5a_item_postcheck_count = (
    SELECT COUNT(*)
    FROM item
    WHERE (id = 1
            AND item_name = _utf8mb4'新手铁剑'
            AND HEX(item_name) = 'E696B0E6898BE99381E58991')
       OR (id = 2
            AND item_name = _utf8mb4'星尘长剑'
            AND HEX(item_name) = 'E6989FE5B098E995BFE58991')
       OR (id = 3
            AND item_name = _utf8mb4'龙鳞护甲'
            AND HEX(item_name) = 'E9BE99E9B39EE68AA4E794B2')
);

SET @phase_5a_event_postcheck_count = (
    SELECT COUNT(*)
    FROM game_event
    WHERE (id = 1
            AND event_desc = _utf8mb4'获得【稀有】装备：星尘长剑'
            AND HEX(event_desc) =
                'E88EB7E5BE97E38090E7A880E69C89E38091E8A385E5A487EFBC9AE6989FE5B098E995BFE58991')
       OR (id = 2
            AND event_desc = _utf8mb4'获得【史诗】装备：龙鳞护甲'
            AND HEX(event_desc) =
                'E88EB7E5BE97E38090E58FB2E8AF97E38091E8A385E5A487EFBC9AE9BE99E9B39EE68AA4E794B2')
);

SET @phase_5a_repair_ok = (
    @phase_5a_precheck_ok = 1
    AND @phase_5a_item_1_updated = 1
    AND @phase_5a_item_2_updated = 1
    AND @phase_5a_item_3_updated = 1
    AND @phase_5a_event_1_updated = 1
    AND @phase_5a_event_2_updated = 1
    AND @phase_5a_item_postcheck_count = 3
    AND @phase_5a_event_postcheck_count = 2
);

SELECT @phase_5a_item_1_updated AS item_1_updated,
       @phase_5a_item_2_updated AS item_2_updated,
       @phase_5a_item_3_updated AS item_3_updated,
       @phase_5a_event_1_updated AS event_1_updated,
       @phase_5a_event_2_updated AS event_2_updated,
       @phase_5a_item_postcheck_count AS item_postcheck_count,
       @phase_5a_event_postcheck_count AS event_postcheck_count,
       @phase_5a_repair_ok AS repair_ok;

-- 只有所有保护条件通过时才提交；否则保留事务给后续静态回滚。
SET @phase_5a_transaction_statement = IF(
    @phase_5a_repair_ok = 1,
    'COMMIT',
    'DO 0'
);
PREPARE phase_5a_transaction FROM @phase_5a_transaction_statement;
EXECUTE phase_5a_transaction;
DEALLOCATE PREPARE phase_5a_transaction;

-- 成功路径已提交，此时为空操作；失败路径在此回滚全部修复。
ROLLBACK;

SELECT IF(
           @phase_5a_repair_ok = 1,
           'COMMITTED',
           'ROLLED_BACK'
       ) AS migration_status;

SELECT id, HEX(item_name) AS item_name_hex, item_name
FROM item
WHERE id IN (1, 2, 3)
ORDER BY id;

SELECT id, HEX(event_desc) AS event_desc_hex, event_desc
FROM game_event
WHERE id IN (1, 2)
ORDER BY id;

SHOW CREATE TABLE item;
SHOW CREATE TABLE game_event;
