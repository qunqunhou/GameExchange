package com.game.servlet;

import com.game.dao.ItemDao;
import com.game.dao.MarketDao;
import com.game.entity.Item;
import com.game.entity.Player;
import com.alibaba.fastjson.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;

@WebServlet("/trade/sell")
public class SellServlet extends HttpServlet {

    private final ItemDao itemDao = new ItemDao();
    private final MarketDao marketDao = new MarketDao();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=utf-8");

        // ✅ 第一步：验证登录
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("player") == null) {
            response.getWriter().write("{\"code\":401,\"msg\":\"请先登录\"}");
            return;
        }

        // ✅ 第二步：从Session取卖家身份
        Player seller = (Player) session.getAttribute("player");
        Integer sellerId = seller.getId();

        // ✅ 第三步：参数校验
        String itemIdStr = request.getParameter("itemId");
        String priceStr  = request.getParameter("price");

        if (itemIdStr == null || itemIdStr.trim().isEmpty()
                || priceStr == null || priceStr.trim().isEmpty()) {
            response.getWriter().write("{\"code\":400,\"msg\":\"参数不完整\"}");
            return;
        }

        Integer itemId;
        Long price;
        try {
            itemId = Integer.parseInt(itemIdStr);
            price  = Long.parseLong(priceStr);
        } catch (NumberFormatException e) {
            response.getWriter().write("{\"code\":400,\"msg\":\"参数格式错误\"}");
            return;
        }

        if (price <= 0) {
            response.getWriter().write("{\"code\":400,\"msg\":\"价格必须大于0\"}");
            return;
        }

        // ✅ 第四步：验证道具归属，防止挂售别人的道具
        Item item = itemDao.findById(itemId);
        if (item == null) {
            response.getWriter().write("{\"code\":404,\"msg\":\"道具不存在\"}");
            return;
        }
        if (!item.getOwnerId().equals(sellerId)) {
            response.getWriter().write("{\"code\":403,\"msg\":\"该道具不属于你\"}");
            return;
        }

        // ✅ 第五步：上架到市场
        int rows = marketDao.addMarket(itemId, sellerId, price);

        JSONObject json = new JSONObject();
        if (rows > 0) {
            json.put("code", 200);
            json.put("msg", "挂售成功");
        } else {
            json.put("code", 500);
            json.put("msg", "挂售失败，请重试");
        }
        response.getWriter().write(json.toJSONString());
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write("{\"code\":405,\"msg\":\"不支持GET请求\"}");
    }
}