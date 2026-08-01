package com.game.simulator;

import com.game.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GameSimulator {

    private static final Logger LOGGER = Logger.getLogger(GameSimulator.class.getName());
    private final Random random = new Random();
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(3); // 3个线程

    // 模拟的10个玩家ID（对应数据库里真实存在的玩家）
    private List<Integer> playerIds;

    // 怪物名称池
    private final String[] MONSTERS = {
            "哥布林", "骷髅兵", "石头怪", "火焰魔", "冰霜巨人",
            "毒蜘蛛", "暗影刺客", "地狱犬", "龙族幼崽", "远古巨兽"
    };

    // 装备名称池
    private final String[][] ITEMS = {
            // {名称, 稀有度}
            {"破旧长剑",   "普通"},
            {"铁制盔甲",   "普通"},
            {"皮革护腿",   "普通"},
            {"精钢战斧",   "稀有"},
            {"魔法长杖",   "稀有"},
            {"龙鳞护甲",   "史诗"},
            {"雷霆之锤",   "史诗"},
            {"天罚神剑",   "传说"},
            {"混沌法典",   "传说"},
            {"虚空碎片",   "传说"}
    };

    // 启动模拟器
    public void start() {
        LOGGER.info("游戏模拟器启动");

        // 第一步：从数据库加载玩家ID列表
        loadPlayerIds();

        if (playerIds == null || playerIds.isEmpty()) {
            LOGGER.warning("数据库中没有玩家数据，游戏模拟器暂停");
            return;
        }

        LOGGER.log(Level.INFO, "已加载 {0} 个玩家", playerIds.size());

        // 第二步：启动击杀怪物任务（每秒执行，模拟3-5个事件）
        //scheduler.scheduleAtFixedRate(
        //        this::simulateKillMonster,
        //        1, 1, TimeUnit.SECONDS
        //);

        // 第三步：启动装备掉落任务（每2秒执行，概率性掉落）
        //scheduler.scheduleAtFixedRate(
        //        this::simulateItemDrop,
        //        2, 2, TimeUnit.SECONDS
        //);

        // 第四步：启动玩家上下线任务（每5秒执行）
        //scheduler.scheduleAtFixedRate(
         //       this::simulateOnlineStatus,
          //      3, 5, TimeUnit.SECONDS
        //);

        LOGGER.info("游戏模拟器所有任务已启动");
    }

    // 停止模拟器
    public void stop() {
        scheduler.shutdownNow();
        LOGGER.info("游戏模拟器已停止");
    }

    // =========================================================
    // 事件一：击杀怪物，奖励金币
    // =========================================================
    private void simulateKillMonster() {
        int count = 3 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            Integer playerId = randomPlayerId();
            String monster   = MONSTERS[random.nextInt(MONSTERS.length)];
            long goldReward  = 10 + random.nextInt(191);

            Connection conn = null;
            try {
                conn = DBUtil.getConnection();

                // 更新金币
                String updateGold = "UPDATE player SET gold = gold + ? WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateGold)) {
                    ps.setLong(1, goldReward);
                    ps.setInt(2, playerId);
                    ps.executeUpdate();
                }

                // 查玩家名
                String playerName = getPlayerName(conn, playerId);

                // ✅ 写入事件日志
                String desc = "击杀了【" + monster + "】，获得 " + goldReward + " 金币";
                logEventWithConn(conn, playerName, "KILL_MONSTER", desc);

                LOGGER.log(Level.FINE, "击杀事件：{0} {1}",
                        new Object[]{playerName, desc});

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "模拟击杀怪物事件失败", e);
            } finally {
                DBUtil.close(conn);
            }
        }
    }

    // =========================================================
    // 事件二：装备掉落，生成新道具
    // =========================================================
    private void simulateItemDrop() {
        if (random.nextInt(10) >= 3) return;

        Integer playerId  = randomPlayerId();
        String[] itemData = randomItem();
        String itemName   = itemData[0];
        String rarity     = itemData[1];

        Connection conn = null;
        try {
            conn = DBUtil.getConnection();

            String insertItem = "INSERT INTO item(item_name, rarity, owner_id) VALUES(?, ?, ?)";
            int newItemId;
            try (PreparedStatement ps = conn.prepareStatement(
                    insertItem, PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, itemName);
                ps.setString(2, rarity);
                ps.setInt(3, playerId);
                ps.executeUpdate();

                try (ResultSet rs = ps.getGeneratedKeys()) {
                    newItemId = rs.next() ? rs.getInt(1) : -1;
                }
            }

            // 查玩家名
            String playerName = getPlayerName(conn, playerId);

            // ✅ 写入事件日志
            String desc = "获得【" + rarity + "】装备：" + itemName + "（ID:" + newItemId + "）";
            logEventWithConn(conn, playerName, "ITEM_DROP", desc);

            LOGGER.log(Level.FINE, "掉落事件：{0} {1}",
                    new Object[]{playerName, desc});

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "模拟装备掉落事件失败", e);
        } finally {
            DBUtil.close(conn);
        }
    }

    // =========================================================
    // 事件三：玩家上下线
    // =========================================================
    private void simulateOnlineStatus() {

    }

    // =========================================================
    // 工具方法
    // 写入游戏事件日志
    private void logEvent(String playerName, String eventType, String eventDesc) {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            String sql = "INSERT INTO game_event(player_name, event_type, event_desc) "
                    + "VALUES(?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerName);
                ps.setString(2, eventType);
                ps.setString(3, eventDesc);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "写入游戏事件日志失败", e);
        } finally {
            DBUtil.close(conn);
        }
    }

    // 复用conn写入事件日志（在已有连接内调用）
    private void logEventWithConn(Connection conn, String playerName,
                                  String eventType, String eventDesc) {
        try {
            String sql = "INSERT INTO game_event(player_name, event_type, event_desc) "
                    + "VALUES(?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerName);
                ps.setString(2, eventType);
                ps.setString(3, eventDesc);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "使用已有连接写入游戏事件日志失败", e);
        }
    }

    // 根据playerId查玩家名
    private String getPlayerName(Connection conn, Integer playerId) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT username FROM player WHERE id = ?")) {
            ps.setInt(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("username");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "查询玩家名称失败", e);
        }
        return "玩家" + playerId;
    }
    // =========================================================

    // 从数据库加载所有玩家ID
    private void loadPlayerIds() {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM player LIMIT 10");
                 ResultSet rs = ps.executeQuery()) {
                playerIds = new java.util.ArrayList<>();
                while (rs.next()) {
                    playerIds.add(rs.getInt("id"));
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "加载玩家 ID 列表失败", e);
        } finally {
            DBUtil.close(conn);
        }
    }

    // 随机取一个玩家ID
    private Integer randomPlayerId() {
        return playerIds.get(random.nextInt(playerIds.size()));
    }

    // 按稀有度权重随机取一件装备
    // 普通60% 稀有25% 史诗12% 传说3%
    private String[] randomItem() {
        int roll = random.nextInt(100);
        if (roll < 60) {
            // 普通装备：前3个
            return ITEMS[random.nextInt(3)];
        } else if (roll < 85) {
            // 稀有装备：4-5
            return ITEMS[3 + random.nextInt(2)];
        } else if (roll < 97) {
            // 史诗装备：6-7
            return ITEMS[5 + random.nextInt(2)];
        } else {
            // 传说装备：8-9
            return ITEMS[7 + random.nextInt(3)];
        }
    }
}
