-- Database Constraint Repair：修复 item.rarity 的历史字符集问题。
-- 本迁移只处理已确认的 id=1、id=2、id=3，不修改其他 item 数据。
-- 执行前必须保存 SHOW CREATE TABLE item 和 item 数据快照。

USE game_exchange;

SET NAMES utf8mb4;

ALTER TABLE item
    DROP CHECK chk_item_rarity;

UPDATE item
SET rarity = _utf8mb4'普通'
WHERE id = 1
  AND HEX(rarity) = 'C3A6E284A2C2AEC3A9E282ACC5A1';

UPDATE item
SET rarity = _utf8mb4'稀有'
WHERE id = 2
  AND HEX(rarity) = 'C3A7C2A8E282ACC3A6C593E280B0';

UPDATE item
SET rarity = _utf8mb4'史诗'
WHERE id = 3
  AND HEX(rarity) = 'C3A5C28FC2B2C3A8C2AFE28094';

-- 该查询必须返回 0 行；否则不得继续添加新约束。
SELECT id, HEX(rarity) AS rarity_hex
FROM item
WHERE HEX(rarity) NOT IN (
    'E699AEE9809A',
    'E7A880E69C89',
    'E58FB2E8AF97',
    'E4BCA0E8AFB4'
);

ALTER TABLE item
    ADD CONSTRAINT chk_item_rarity
        CHECK (
            rarity IN (
                _utf8mb4'普通',
                _utf8mb4'稀有',
                _utf8mb4'史诗',
                _utf8mb4'传说'
            )
        );
