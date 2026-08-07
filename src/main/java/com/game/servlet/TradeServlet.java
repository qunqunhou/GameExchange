package com.game.servlet;

import com.game.entity.Player;
import com.game.monitor.BusinessMetrics;
import com.game.service.TradeService;
import com.alibaba.fastjson.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;

@WebServlet("/trade/buy")
public class TradeServlet extends HttpServlet {

    private final TradeService tradeService = new TradeService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write("{\"code\":405,\"msg\":\"不支持GET请求\"}");
    }

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

        // ✅ 第二步：从Session取buyerId，不信任前端传值
        Player buyer = (Player) session.getAttribute("player");
        Integer buyerId = buyer.getId();

        // ✅ 第三步：参数校验
        String marketIdStr = request.getParameter("marketId");
        if (marketIdStr == null || marketIdStr.trim().isEmpty()) {
            response.getWriter().write("{\"code\":400,\"msg\":\"缺少marketId参数\"}");
            return;
        }

        Integer marketId;
        try {
            marketId = Integer.parseInt(marketIdStr);
        } catch (NumberFormatException e) {
            response.getWriter().write("{\"code\":400,\"msg\":\"marketId格式错误\"}");
            return;
        }

        // ✅ 第四步：执行交易
        String result = tradeService.buyItem(marketId, buyerId);

        JSONObject json = new JSONObject();
        if ("购买成功".equals(result)) {
            BusinessMetrics.tradeSuccess();
            json.put("code", 200);
        } else {
            json.put("code", 500);
        }
        json.put("msg", result);
        response.getWriter().write(json.toJSONString());
    }
}