package com.game.servlet;

import com.game.util.DBUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/battle/result")
public class BattleServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(BattleServlet.class.getName());

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=utf-8");

        String playerIdStr  = request.getParameter("playerId");
        String goldStr      = request.getParameter("gold");
        String monsterName  = request.getParameter("monsterName");
        String lootsJson    = request.getParameter("loots");

        if (playerIdStr == null || goldStr == null) {
            response.getWriter().write("{\"code\":400,\"msg\":\"参数错误\"}");
            return;
        }

        Integer playerId = Integer.parseInt(playerIdStr);
        Long gold        = Long.parseLong(goldStr);

        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            // ✅ 增加玩家金币
            PreparedStatement ps1 = conn.prepareStatement(
                    "UPDATE player SET gold = gold + ? WHERE id = ?"
            );
            ps1.setLong(1, gold);
            ps1.setInt(2, playerId);
            ps1.executeUpdate();

            // ✅ 写入击杀事件日志
            PreparedStatement ps2 = conn.prepareStatement(
                    "INSERT INTO game_event(player_name, event_type, event_desc) "
                            + "SELECT username, 'KILL_MONSTER', CONCAT('击败了【', ?, '】，获得 ', ?, ' 金币') "
                            + "FROM player WHERE id = ?"
            );
            ps2.setString(1, monsterName);
            ps2.setLong(2, gold);
            ps2.setInt(3, playerId);
            ps2.executeUpdate();

            // ✅ 写入掉落装备
            if (lootsJson != null && !lootsJson.equals("[]")) {
                JSONArray loots = JSONArray.parseArray(lootsJson);
                for (int i = 0; i < loots.size(); i++) {
                    JSONObject loot = loots.getJSONObject(i);
                    String itemName = loot.getString("itemName");
                    String rarity   = loot.getString("rarity");

                    // 插入道具
                    PreparedStatement ps3 = conn.prepareStatement(
                            "INSERT INTO item(item_name, rarity, owner_id) VALUES(?, ?, ?)"
                    );
                    ps3.setString(1, itemName);
                    ps3.setString(2, rarity);
                    ps3.setInt(3, playerId);
                    ps3.executeUpdate();

                    // 写入掉落事件日志
                    PreparedStatement ps4 = conn.prepareStatement(
                            "INSERT INTO game_event(player_name, event_type, event_desc) "
                                    + "SELECT username, 'ITEM_DROP', CONCAT('获得【', ?, '】装备：', ?) "
                                    + "FROM player WHERE id = ?"
                    );
                    ps4.setString(1, rarity);
                    ps4.setString(2, itemName);
                    ps4.setInt(3, playerId);
                    ps4.executeUpdate();
                }
            }

            conn.commit();
            response.getWriter().write("{\"code\":200,\"msg\":\"保存成功\"}");

        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (Exception rollbackEx) {
                    LOGGER.log(Level.SEVERE, "战斗结果事务回滚失败", rollbackEx);
                }
            }
            LOGGER.log(Level.SEVERE, "保存战斗结果失败", e);
            response.getWriter().write("{\"code\":500,\"msg\":\"保存失败\"}");
        } finally {
            DBUtil.close(conn);
        }
    }
}
