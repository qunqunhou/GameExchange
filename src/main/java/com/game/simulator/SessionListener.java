package com.game.simulator;

import com.game.entity.Player;
import com.game.service.PresenceService;

import javax.servlet.annotation.WebListener;
import javax.servlet.http.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebListener
public class SessionListener implements HttpSessionListener {

    private static final Logger LOGGER = Logger.getLogger(SessionListener.class.getName());
    private final PresenceService presenceService = new PresenceService();

    @Override
    public void sessionCreated(HttpSessionEvent se) {
        // Session创建时不需要处理
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        // ✅ Session销毁时（超时或invalidate），自动设为下线
        HttpSession session = se.getSession();
        Player player = (Player) session.getAttribute("player");
        if (player != null) {
            if (presenceService.markOffline(player.getId())) {
                LOGGER.log(Level.INFO, "Session 销毁，玩家 {0} 自动下线",
                        player.getUsername());
            } else {
                LOGGER.log(Level.WARNING, "Session 销毁时未能清理玩家 {0} 的在线状态",
                        player.getUsername());
            }
        }
    }
}
