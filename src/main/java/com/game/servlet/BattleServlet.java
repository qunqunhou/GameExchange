package com.game.servlet;

import com.game.dao.BattleRecordDao;
import com.game.entity.BattleRecord;
import com.game.util.DBUtil;
import com.alibaba.fastjson.JSONObject;
import com.game.entity.Player;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/battle/result")
public class BattleServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(BattleServlet.class.getName());
    private final BattleRecordDao battleRecordDao = new BattleRecordDao();
    private static final Map<String, MonsterReward> MONSTER_REWARDS = Map.ofEntries(
            Map.entry("哥布林", new MonsterReward(20, 60, 0.30)),
            Map.entry("骷髅卫士", new MonsterReward(40, 100, 0.35)),
            Map.entry("石头怪", new MonsterReward(50, 120, 0.40)),
            Map.entry("火焰魔", new MonsterReward(80, 180, 0.45)),
            Map.entry("冰霜巨人", new MonsterReward(100, 220, 0.50)),
            Map.entry("毒蜂女王", new MonsterReward(60, 140, 0.40)),
            Map.entry("暗影刺客", new MonsterReward(90, 200, 0.45)),
            Map.entry("地狱犬", new MonsterReward(110, 240, 0.50)),
            Map.entry("龙族幼崽", new MonsterReward(150, 300, 0.60)),
            Map.entry("远古巨兽", new MonsterReward(200, 500, 0.70))
    );
    private static final List<LootReward> LOOT_REWARDS = List.of(
            new LootReward("破旧长剑", "普通"),
            new LootReward("铁制盔甲", "普通"),
            new LootReward("皮革护腕", "普通"),
            new LootReward("精钢战斧", "稀有"),
            new LootReward("魔法长杖", "稀有"),
            new LootReward("龙鳞护甲", "史诗"),
            new LootReward("雷霆之锤", "史诗"),
            new LootReward("天罚神剑", "传说"),
            new LootReward("混沌法典", "传说"),
            new LootReward("虚空碎片", "传说")
    );

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=utf-8");

        HttpSession session = request.getSession(false);
        Object playerAttribute = session == null ? null : session.getAttribute("player");
        if (!(playerAttribute instanceof Player)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            writeResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "请先登录");
            return;
        }

        Player currentPlayer = (Player) playerAttribute;
        Integer playerId = currentPlayer.getId();
        String requestId = request.getParameter("requestId");
        if (requestId == null || requestId.trim().isEmpty()) {
            writeResponse(response, HttpServletResponse.SC_BAD_REQUEST, "参数错误");
            return;
        }
        requestId = requestId.trim();
        if (requestId.length() > 36) {
            writeResponse(response, HttpServletResponse.SC_BAD_REQUEST, "参数格式错误");
            return;
        }

        String playerIdStr = request.getParameter("playerId");
        if (playerId == null || playerIdStr == null || playerIdStr.trim().isEmpty()) {
            writeResponse(response, HttpServletResponse.SC_BAD_REQUEST, "参数错误");
            return;
        }

        try {
            Integer requestedPlayerId = Integer.valueOf(playerIdStr);
            if (!playerId.equals(requestedPlayerId)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                writeResponse(response, HttpServletResponse.SC_FORBIDDEN, "无权提交其他玩家的战斗结果");
                return;
            }
        } catch (NumberFormatException e) {
            writeResponse(response, HttpServletResponse.SC_BAD_REQUEST, "参数格式错误");
            return;
        }

        String monsterName = request.getParameter("monsterName");
        if (monsterName == null || monsterName.trim().isEmpty()) {
            writeResponse(response, HttpServletResponse.SC_BAD_REQUEST, "参数错误");
            return;
        }
        monsterName = monsterName.trim();

        MonsterReward monsterReward = MONSTER_REWARDS.get(monsterName);
        if (monsterReward == null) {
            writeResponse(response, HttpServletResponse.SC_BAD_REQUEST, "未知怪物");
            return;
        }

        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            BattleRecord existingRecord = battleRecordDao.findByPlayerAndRequestId(
                    conn, playerId, requestId);
            if (existingRecord != null) {
                if (!monsterName.equals(existingRecord.getMonsterName())) {
                    conn.rollback();
                    writeResponse(response, HttpServletResponse.SC_CONFLICT,
                            "requestId与怪物不一致");
                    return;
                }

                conn.commit();
                writeResponse(response, HttpServletResponse.SC_OK, "保存成功");
                return;
            }

            ThreadLocalRandom random = ThreadLocalRandom.current();
            long gold = random.nextInt(monsterReward.minGold, monsterReward.maxGold + 1);
            LootReward lootReward = null;
            if (random.nextDouble() < monsterReward.dropRate) {
                lootReward = LOOT_REWARDS.get(random.nextInt(LOOT_REWARDS.size()));
            }
            Integer lootItemId = null;

            // ✅ 增加玩家金币
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE player SET gold = gold + ? WHERE id = ?")) {
                ps.setLong(1, gold);
                ps.setInt(2, playerId);
                if (ps.executeUpdate() != 1) {
                    throw new SQLException("当前登录玩家不存在");
                }
            }

            // ✅ 写入击杀事件日志
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO game_event(player_name, event_type, event_desc) "
                            + "SELECT username, 'KILL_MONSTER', CONCAT('击败了【', ?, '】，获得 ', ?, ' 金币') "
                            + "FROM player WHERE id = ?")) {
                ps.setString(1, monsterName);
                ps.setLong(2, gold);
                ps.setInt(3, playerId);
                if (ps.executeUpdate() != 1) {
                    throw new SQLException("战斗事件写入失败");
                }
            }

            // ✅ 写入掉落装备
            if (lootReward != null) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO item(item_name, rarity, owner_id) VALUES(?, ?, ?)",
                        PreparedStatement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, lootReward.itemName);
                    ps.setString(2, lootReward.rarity);
                    ps.setInt(3, playerId);
                    if (ps.executeUpdate() != 1) {
                        throw new SQLException("战利品写入失败");
                    }

                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (!rs.next()) {
                            throw new SQLException("战利品写入后未返回主键");
                        }
                        lootItemId = rs.getInt(1);
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO game_event(player_name, event_type, event_desc) "
                                + "SELECT username, 'ITEM_DROP', CONCAT('获得【', ?, '】装备：', ?) "
                                + "FROM player WHERE id = ?")) {
                    ps.setString(1, lootReward.rarity);
                    ps.setString(2, lootReward.itemName);
                    ps.setInt(3, playerId);
                    if (ps.executeUpdate() != 1) {
                        throw new SQLException("战利品事件写入失败");
                    }
                }
            }

            BattleRecord battleRecord = new BattleRecord();
            battleRecord.setPlayerId(playerId);
            battleRecord.setRequestId(requestId);
            battleRecord.setMonsterName(monsterName);
            battleRecord.setGoldReward(gold);
            battleRecord.setLootItemId(lootItemId);
            if (battleRecordDao.insert(conn, battleRecord) != 1) {
                throw new SQLException("战斗记录写入失败");
            }

            conn.commit();
            writeResponse(response, HttpServletResponse.SC_OK, "保存成功");

        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (Exception rollbackEx) {
                    LOGGER.log(Level.SEVERE, "战斗结果事务回滚失败", rollbackEx);
                }
            }
            LOGGER.log(Level.SEVERE, "保存战斗结果失败", e);
            writeResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "保存失败");
        } finally {
            DBUtil.close(conn);
        }
    }

    private void writeResponse(HttpServletResponse response, int code, String message)
            throws IOException {
        JSONObject json = new JSONObject();
        json.put("code", code);
        json.put("msg", message);
        response.getWriter().write(json.toJSONString());
    }

    private static final class MonsterReward {
        private final int minGold;
        private final int maxGold;
        private final double dropRate;

        private MonsterReward(int minGold, int maxGold, double dropRate) {
            this.minGold = minGold;
            this.maxGold = maxGold;
            this.dropRate = dropRate;
        }
    }

    private static final class LootReward {
        private final String itemName;
        private final String rarity;

        private LootReward(String itemName, String rarity) {
            this.itemName = itemName;
            this.rarity = rarity;
        }
    }
}
