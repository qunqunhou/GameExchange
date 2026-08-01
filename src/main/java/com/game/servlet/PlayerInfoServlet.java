package com.game.servlet;

import com.game.dao.PlayerDao;
import com.game.entity.Player;
import com.alibaba.fastjson.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;

@WebServlet("/player/info")
public class PlayerInfoServlet extends HttpServlet {

    private final PlayerDao playerDao = new PlayerDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=utf-8");

        HttpSession session = request.getSession(false);
        Object playerAttribute = session == null ? null : session.getAttribute("player");
        if (!(playerAttribute instanceof Player)) {
            writeForbidden(response);
            return;
        }

        String playerIdStr = request.getParameter("playerId");
        if (playerIdStr == null || playerIdStr.trim().isEmpty()) {
            response.getWriter().write("{\"code\":400,\"msg\":\"参数错误\"}");
            return;
        }

        Integer playerId;
        try {
            playerId = Integer.valueOf(playerIdStr);
        } catch (NumberFormatException e) {
            response.getWriter().write("{\"code\":400,\"msg\":\"参数格式错误\"}");
            return;
        }

        Player currentPlayer = (Player) playerAttribute;
        if (currentPlayer.getId() == null || !currentPlayer.getId().equals(playerId)) {
            writeForbidden(response);
            return;
        }

        Player player = playerDao.findById(currentPlayer.getId());
        if (player == null) {
            response.getWriter().write("{\"code\":404,\"msg\":\"玩家不存在\"}");
            return;
        }

        JSONObject json = new JSONObject();
        json.put("code", 200);
        json.put("gold", player.getGold());
        json.put("username", player.getUsername());
        response.getWriter().write(json.toJSONString());
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.getWriter().write("{\"code\":403,\"msg\":\"无权访问该玩家信息\"}");
    }
}
