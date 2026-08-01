package com.game.dao;

import com.game.entity.Player;
import com.game.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlayerDao {
    private static final Logger LOGGER = Logger.getLogger(PlayerDao.class.getName());

    //注册玩家
    public int addPlayer(Player player) {
        String sql = "INSERT INTO player(username, password, gold) VALUES(?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, player.getUsername());
            ps.setString(2, player.getPassword());
            ps.setLong(3, player.getGold());
            return ps.executeUpdate();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "新增玩家失败", e);
        }
        return 0;
    }

    //根据用户名查询
    public Player findByUsername(String username) {
        String sql = "SELECT id, username, password, gold "
                + "FROM player WHERE username = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "按用户名查询玩家失败", e);
        }
        return null;
    }

    public Player findById(Connection conn, Integer id) throws Exception {
        String sql = "SELECT id, username, password, gold "
                + "FROM player WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public int updateGold(Connection conn,
                          Integer playerId,
                          Long delta) throws Exception {
        // delta为正数增加金币，负数扣除金币
        String sql = "UPDATE player SET gold = gold + ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, delta);
            ps.setInt(2, playerId);
            return ps.executeUpdate();
        }
    }
    private Player mapRow(ResultSet rs) throws Exception {
    Player player = new Player();
    player.setId(rs.getInt("id"));
    player.setUsername(rs.getString("username"));
    player.setPassword(rs.getString("password"));
    player.setGold(rs.getLong("gold"));
    return player;
}

    // 非事务版，自己管理连接
    public Player findById(Integer id) {
        String sql = "SELECT id, username, password, gold "
                + "FROM player WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "按 ID 查询玩家失败", e);
        }
        return null;
    }
}
