package com.game.service;

import com.game.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PresenceService {

    private static final Logger LOGGER = Logger.getLogger(PresenceService.class.getName());

    public boolean markOnline(Integer playerId) {
        String sql = "UPDATE player SET last_seen_at = CURRENT_TIMESTAMP WHERE id = ?";
        return updatePresence(playerId, sql, "初始化玩家在线租约失败");
    }

    public boolean refreshLease(Integer playerId) {
        String sql = "UPDATE player SET last_seen_at = CURRENT_TIMESTAMP WHERE id = ?";
        return updatePresence(playerId, sql, "刷新玩家在线租约失败");
    }

    public boolean markOffline(Integer playerId) {
        String sql = "UPDATE player SET last_seen_at = NULL WHERE id = ?";
        return updatePresence(playerId, sql, "清理玩家在线租约失败");
    }

    private boolean updatePresence(Integer playerId, String sql, String errorMessage) {
        if (playerId == null) {
            LOGGER.log(Level.WARNING, "忽略缺少玩家 ID 的在线租约更新");
            return false;
        }

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, playerId);
            return ps.executeUpdate() == 1;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, errorMessage, e);
            return false;
        }
    }
}
