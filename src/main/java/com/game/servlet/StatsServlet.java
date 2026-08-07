package com.game.servlet;

import com.game.service.PresenceService;
import com.game.util.DBUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/stats")
public class StatsServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(StatsServlet.class.getName());
    private final PresenceService presenceService;

    public StatsServlet() {
        this(new PresenceService());
    }

    StatsServlet(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=utf-8");

        // 允许跨域（Vue开发时可能需要）
        response.setHeader("Access-Control-Allow-Origin", "*");

        JSONObject result = new JSONObject();
        Connection conn = null;

        try {
            conn = DBUtil.getConnection();

            // ✅ 数据一：Lease 在线人数（正式读路径）
            int onlineCount = getLeaseOnlineCount(conn);
            result.put("onlineCount", onlineCount);

            // ✅ 数据二：今日掉落稀有装备 Top10
            JSONArray top10 = getTop10RareItems(conn);
            result.put("top10Items", top10);

            // ✅ 数据三：市场在售商品总数
            int onSaleCount = getOnSaleCount(conn);
            result.put("onSaleCount", onSaleCount);

            // ✅ 数据四：今日成交总额
            long todayVolume = getTodayVolume(conn);
            result.put("todayVolume", todayVolume);

            // ✅ 数据五：全服玩家总金币
            long totalGold = getTotalGold(conn);
            result.put("totalGold", totalGold);

            JSONArray eventLogs = getRecentEvents(conn);
            result.put("eventLogs", eventLogs);
            result.put("code", 200);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "查询统计数据失败", e);
            result.put("code", 500);
            result.put("msg", "数据获取失败");
        } finally {
            DBUtil.close(conn);
        }

        response.getWriter().write(result.toJSONString());

    }

    private int getLeaseOnlineCount(Connection conn) throws SQLException {
        return presenceService.countOnlinePlayers(conn);
    }

    // 今日掉落稀有装备 Top10（稀有度不为"普通"的，按id倒序取最新10条）
    private JSONArray getTop10RareItems(Connection conn) throws Exception {
        String sql = "SELECT i.id, i.item_name, i.rarity, p.username "
                + "FROM item i "
                + "JOIN player p ON i.owner_id = p.id "
                + "WHERE i.rarity != '普通' "
                + "ORDER BY i.id DESC "
                + "LIMIT 10";
        JSONArray array = new JSONArray();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                JSONObject item = new JSONObject();
                item.put("id",       rs.getInt("id"));
                item.put("itemName", rs.getString("item_name"));
                item.put("rarity",   rs.getString("rarity"));
                item.put("owner",    rs.getString("username"));
                array.add(item);
            }
        }
        return array;
    }

    // 市场在售商品总数
    private int getOnSaleCount(Connection conn) throws Exception {
        String sql = "SELECT COUNT(*) FROM market WHERE status = 'ON_SALE'";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // 今日成交总额
    private long getTodayVolume(Connection conn) throws Exception {
        String sql = "SELECT COALESCE(SUM(price), 0) FROM trade_record "
                + "WHERE trade_time >= CURDATE() "
                + "AND trade_time < CURDATE() + INTERVAL 1 DAY";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    // 全服玩家总金币
    private long getTotalGold(Connection conn) throws Exception {
        String sql = "SELECT COALESCE(SUM(gold), 0) FROM player";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    // 最新20条游戏事件
    private JSONArray getRecentEvents(Connection conn) throws Exception {
        String sql = "SELECT player_name, event_type, event_desc, create_time "
                + "FROM game_event "
                + "ORDER BY id DESC "
                + "LIMIT 20";
        JSONArray array = new JSONArray();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                JSONObject event = new JSONObject();
                event.put("playerName", rs.getString("player_name"));
                event.put("eventType",  rs.getString("event_type"));
                event.put("eventDesc",  rs.getString("event_desc"));
                event.put("createTime", rs.getString("create_time"));
                array.add(event);
            }
        }
        return array;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write("{\"code\":405,\"msg\":\"不支持POST请求\"}");
    }
}
