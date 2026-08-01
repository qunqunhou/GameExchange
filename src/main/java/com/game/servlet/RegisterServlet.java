package com.game.servlet;

import com.game.dao.PlayerDao;
import com.game.entity.Player;
import com.alibaba.fastjson.JSONObject;
import org.mindrot.jbcrypt.BCrypt;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;

@WebServlet("/register")
public class RegisterServlet extends HttpServlet {

    private static final int BCRYPT_LOG_ROUNDS = 12;
    private final PlayerDao playerDao = new PlayerDao();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=utf-8");

        String username = request.getParameter("username");
        String password = request.getParameter("password");
        String confirm  = request.getParameter("confirm");

        // ✅ 参数校验
        if (username == null || username.trim().isEmpty()
                || password == null || password.trim().isEmpty()) {
            response.getWriter().write("{\"code\":400,\"msg\":\"用户名和密码不能为空\"}");
            return;
        }

        if (username.trim().length() < 2 || username.trim().length() > 16) {
            response.getWriter().write("{\"code\":400,\"msg\":\"用户名长度为2-16位\"}");
            return;
        }

        if (password.length() < 6) {
            response.getWriter().write("{\"code\":400,\"msg\":\"密码不能少于6位\"}");
            return;
        }

        if (!password.equals(confirm)) {
            response.getWriter().write("{\"code\":400,\"msg\":\"两次密码输入不一致\"}");
            return;
        }

        // ✅ 检查用户名是否已存在
        Player existing = playerDao.findByUsername(username.trim());
        if (existing != null) {
            response.getWriter().write("{\"code\":409,\"msg\":\"用户名已被占用\"}");
            return;
        }

        // ✅ 创建新玩家，初始金币10000
        Player newPlayer = new Player();
        newPlayer.setUsername(username.trim());
        newPlayer.setPassword(BCrypt.hashpw(password, BCrypt.gensalt(BCRYPT_LOG_ROUNDS)));
        newPlayer.setGold(10000L);

        int rows = playerDao.addPlayer(newPlayer);

        JSONObject json = new JSONObject();
        if (rows > 0) {
            json.put("code", 200);
            json.put("msg", "注册成功，初始金币 10000");
        } else {
            json.put("code", 500);
            json.put("msg", "注册失败，请重试");
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
