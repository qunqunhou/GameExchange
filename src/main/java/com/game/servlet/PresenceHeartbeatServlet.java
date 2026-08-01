package com.game.servlet;

import com.alibaba.fastjson.JSONObject;
import com.game.entity.Player;
import com.game.service.PresenceService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

@WebServlet("/presence/heartbeat")
public class PresenceHeartbeatServlet extends HttpServlet {

    private final PresenceService presenceService = new PresenceService();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=utf-8");

        HttpSession session = request.getSession(false);
        Object playerAttribute = session == null ? null : session.getAttribute("player");
        if (!(playerAttribute instanceof Player)) {
            writeResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "请先登录");
            return;
        }

        Player player = (Player) playerAttribute;
        if (!presenceService.refreshLease(player.getId())) {
            writeResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "在线状态刷新失败");
            return;
        }

        writeResponse(response, HttpServletResponse.SC_OK, "在线状态已刷新");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setHeader("Allow", "POST");
        response.setContentType("application/json;charset=utf-8");
        writeResponse(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                "不支持GET请求");
    }

    private void writeResponse(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        JSONObject json = new JSONObject();
        json.put("code", status);
        json.put("msg", message);
        response.getWriter().write(json.toJSONString());
    }
}
