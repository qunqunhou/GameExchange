package com.game.dao;

import com.game.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class TradeRecordDao {

    public int addRecord(Connection conn,
                         Integer buyerId,
                         Integer sellerId,
                         Integer itemId,
                         Long price) throws Exception {
        String sql = "INSERT INTO trade_record(buyer_id, seller_id, item_id, price) "
                + "VALUES(?, ?, ?, ?)";
        // trade_time 有 DEFAULT CURRENT_TIMESTAMP，不需要传值
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, buyerId);
            ps.setInt(2, sellerId);
            ps.setInt(3, itemId);
            ps.setLong(4, price);
            return ps.executeUpdate();
        }
    }
}

