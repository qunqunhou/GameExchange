package com.game.servlet;

import com.alibaba.fastjson.JSONObject;
import com.game.dao.PlayerDao;
import com.game.entity.Player;

import javax.servlet.ServletException;
import javax.servlet.http.*;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {
    private PlayerDao playerDao = new PlayerDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write("{\"code\":405,\"msg\":\"不支持GET请求\"}");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=utf-8");

        String username = request.getParameter("username");
        String password = request.getParameter("password");


        if (username == null || username.trim().isEmpty()
                || password == null || password.trim().isEmpty()) {
            response.getWriter().write("{\"code\":400,\"msg\":\"用户名和密码不能为空\"}");
            return;
        }

        Player player = playerDao.findByUsername(username);

        if (player != null && player.getPassword().equals(password)) {

            // ✅ 登录成功，创建Session，存入player对象
            HttpSession session = request.getSession();
            session.setAttribute("player", player);

            playerDao.updateOnlineStatus(player.getId(), 1);

            // ✅ 用Fastjson构建响应，避免手拼JSON出错
            JSONObject json = new JSONObject();
            json.put("code", 200);
            json.put("msg", "登录成功");
            json.put("id", player.getId());
            json.put("username", player.getUsername());
            json.put("gold", player.getGold());
            response.getWriter().write(json.toJSONString());

        } else {
            JSONObject json = new JSONObject();
            json.put("code", 500);
            json.put("msg", "用户名或密码错误");
            response.getWriter().write(json.toJSONString());
        }
    }
}
