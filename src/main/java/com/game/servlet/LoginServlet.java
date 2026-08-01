package com.game.servlet;

import com.alibaba.fastjson.JSONObject;
import com.game.dao.PlayerDao;
import com.game.entity.Player;
import com.game.service.PresenceService;
import com.game.util.DBUtil;
import org.mindrot.jbcrypt.BCrypt;

import javax.servlet.ServletException;
import javax.servlet.http.*;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(LoginServlet.class.getName());
    private static final int BCRYPT_LOG_ROUNDS = 12;
    private static final String BCRYPT_PREFIX = "$2a$";
    private final PlayerDao playerDao = new PlayerDao();
    private final PresenceService presenceService = new PresenceService();

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

        if (player != null && verifyPasswordAndUpgrade(player, password)) {

            // ✅ 登录成功，创建Session，存入player对象
            HttpSession session = request.getSession();
            request.changeSessionId();
            player.setPassword(null);
            session.setAttribute("player", player);

            presenceService.markOnline(player.getId());

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

    private boolean verifyPasswordAndUpgrade(Player player, String candidatePassword) {
        String storedPassword = player.getPassword();
        if (storedPassword == null) {
            return false;
        }

        if (storedPassword.startsWith(BCRYPT_PREFIX)) {
            try {
                return BCrypt.checkpw(candidatePassword, storedPassword);
            } catch (IllegalArgumentException e) {
                LOGGER.log(Level.WARNING, "玩家密码哈希格式无效，拒绝登录，playerId={0}",
                        player.getId());
                return false;
            }
        }

        if (!storedPassword.equals(candidatePassword)) {
            return false;
        }

        String passwordHash = BCrypt.hashpw(candidatePassword,
                BCrypt.gensalt(BCRYPT_LOG_ROUNDS));
        return upgradeLegacyPassword(player.getId(), storedPassword, passwordHash);
    }

    private boolean upgradeLegacyPassword(Integer playerId, String legacyPassword,
                                          String passwordHash) {
        String sql = "UPDATE player SET password = ? WHERE id = ? AND password = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, passwordHash);
            ps.setInt(2, playerId);
            ps.setString(3, legacyPassword);
            return ps.executeUpdate() == 1;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "升级历史玩家密码失败，playerId=" + playerId, e);
            return false;
        }
    }
}
