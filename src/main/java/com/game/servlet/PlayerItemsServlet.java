package com.game.servlet;

import com.game.entity.Item;
import com.game.entity.Player;
import com.game.util.DBUtil;
import com.google.gson.Gson;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/player/items")
public class PlayerItemsServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(PlayerItemsServlet.class.getName());
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=utf-8");

        // ✅ 验证登录
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("player") == null) {
            response.setStatus(401);
            response.getWriter().write("{\"code\":401,\"msg\":\"请先登录\"}");
            return;
        }

        // ✅ 从Session取玩家ID
        Player player = (Player) session.getAttribute("player");
        Integer playerId = player.getId();

        Connection conn = null;
        try {
            conn = DBUtil.getConnection();

            // 查询该玩家拥有的、且不在市场在售中的道具
            String sql = "SELECT i.id, i.item_name, i.rarity "
                    + "FROM item i "
                    + "WHERE i.owner_id = ? "
                    + "AND i.id NOT IN ( "
                    + "    SELECT item_id FROM market WHERE status = 'ON_SALE' "
                    + ")";

            ArrayList<Item> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Item item = new Item();
                        item.setId(rs.getInt("id"));
                        item.setItemName(rs.getString("item_name"));
                        item.setRarity(rs.getString("rarity"));
                        items.add(item);
                    }
                }
            }

            response.getWriter().write(gson.toJson(items));

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "查询玩家物品失败", e);
            response.getWriter().write("{\"code\":500,\"msg\":\"查询失败\"}");
        } finally {
            DBUtil.close(conn);
        }
    }
}
