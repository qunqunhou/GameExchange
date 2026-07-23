package com.game.servlet;

import com.game.dao.MarketDao;
import com.game.entity.Market;
import com.google.gson.Gson;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.util.ArrayList;

@WebServlet("/market/list")
public class MarketServlet extends HttpServlet {

    private final MarketDao marketDao = new MarketDao();
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

        // ✅ 返回对象集合，前端可以用具名字段
        ArrayList<Market> list = marketDao.findOnSale();
        response.getWriter().write(gson.toJson(list));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write("{\"code\":405,\"msg\":\"不支持POST请求\"}");
    }
}