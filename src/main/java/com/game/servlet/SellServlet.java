package com.game.servlet;

import com.alibaba.fastjson.JSONObject;
import com.game.dao.MarketDao;
import com.game.entity.Item;
import com.game.entity.Player;
import com.game.util.DBUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/trade/sell")
public class SellServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(SellServlet.class.getName());
    private final MarketDao marketDao = new MarketDao();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=utf-8");

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("player") == null) {
            writeResponse(response, 401, "请先登录");
            return;
        }

        Player seller = (Player) session.getAttribute("player");
        Integer sellerId = seller.getId();
        String itemIdStr = request.getParameter("itemId");
        String priceStr = request.getParameter("price");
        if (itemIdStr == null || itemIdStr.trim().isEmpty()
                || priceStr == null || priceStr.trim().isEmpty()) {
            writeResponse(response, 400, "参数不完整");
            return;
        }

        Integer itemId;
        Long price;
        try {
            itemId = Integer.parseInt(itemIdStr);
            price = Long.parseLong(priceStr);
        } catch (NumberFormatException e) {
            writeResponse(response, 400, "参数格式错误");
            return;
        }
        if (price <= 0) {
            writeResponse(response, 400, "价格必须大于0");
            return;
        }

        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            // 锁定装备，令归属检查、在售检查和插入处于同一事务。
            Item item = marketDao.findItemForUpdate(conn, itemId);
            if (item == null) {
                rollback(conn);
                writeResponse(response, 404, "道具不存在");
                return;
            }
            if (!sellerId.equals(item.getOwnerId())) {
                rollback(conn);
                writeResponse(response, 403, "该道具不属于你");
                return;
            }
            if (marketDao.existsOnSale(conn, itemId)) {
                rollback(conn);
                writeResponse(response, 409, "该道具已经上架");
                return;
            }

            int rows = marketDao.addMarket(conn, itemId, sellerId, price);
            if (rows != 1) {
                throw new SQLException("挂单写入失败");
            }
            conn.commit();
            writeResponse(response, 200, "挂售成功");
        } catch (Exception e) {
            rollback(conn);
            LOGGER.log(Level.SEVERE, "挂售事务失败", e);
            writeResponse(response, 500, "挂售失败，请重试");
        } finally {
            DBUtil.close(conn);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=utf-8");
        writeResponse(response, 405, "不支持GET请求");
    }

    private void writeResponse(HttpServletResponse response, int code, String message)
            throws IOException {
        JSONObject json = new JSONObject();
        json.put("code", code);
        json.put("msg", message);
        response.getWriter().write(json.toJSONString());
    }

    private void rollback(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            conn.rollback();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "挂售事务回滚失败", e);
        }
    }
}
