package com.game.dao;

import com.game.entity.BattleRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

public class BattleRecordDao {

    public BattleRecord findByPlayerAndRequestId(Connection conn,
                                                   Integer playerId,
                                                   String requestId) throws Exception {
        String sql = "SELECT id, player_id, request_id, monster_name, "
                + "gold_reward, loot_item_id, created_at "
                + "FROM battle_record WHERE player_id = ? AND request_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, playerId);
            ps.setString(2, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public int insert(Connection conn, BattleRecord record) throws Exception {
        String sql = "INSERT INTO battle_record("
                + "player_id, request_id, monster_name, gold_reward, loot_item_id) "
                + "VALUES(?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(
                sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, record.getPlayerId());
            ps.setString(2, record.getRequestId());
            ps.setString(3, record.getMonsterName());
            ps.setLong(4, record.getGoldReward());
            if (record.getLootItemId() == null) {
                ps.setNull(5, Types.INTEGER);
            } else {
                ps.setInt(5, record.getLootItemId());
            }

            int rows = ps.executeUpdate();
            if (rows == 1) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) {
                        throw new SQLException("插入战斗记录后未返回主键");
                    }
                    record.setId(rs.getLong(1));
                }
            }
            return rows;
        }
    }

    private BattleRecord mapRow(ResultSet rs) throws SQLException {
        BattleRecord record = new BattleRecord();
        record.setId(rs.getLong("id"));
        record.setPlayerId(rs.getInt("player_id"));
        record.setRequestId(rs.getString("request_id"));
        record.setMonsterName(rs.getString("monster_name"));
        record.setGoldReward(rs.getLong("gold_reward"));

        int lootItemId = rs.getInt("loot_item_id");
        record.setLootItemId(rs.wasNull() ? null : lootItemId);
        record.setCreatedAt(rs.getTimestamp("created_at"));
        return record;
    }
}
