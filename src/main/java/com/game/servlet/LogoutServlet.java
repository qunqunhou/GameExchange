package com.game.servlet;

import com.game.dao.PlayerDao;
import com.game.entity.Player;
import com.alibaba.fastjson.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;

@WebServlet("/logout")
public class LogoutServlet extends HttpServlet {

    private final PlayerDao playerDao = new PlayerDao();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=utf-8");

        // ✅ 从Session取玩家，设为下线
        HttpSession session = request.getSession(false);
        if (session != null) {
            Player player = (Player) session.getAttribute("player");
            if (player != null) {
                playerDao.updateOnlineStatus(player.getId(), 0);
            }
            session.invalidate(); // ✅ 销毁Session
        }

        JSONObject json = new JSONObject();
        json.put("code", 200);
        json.put("msg", "已退出登录");
        response.getWriter().write(json.toJSONString());
    }
}