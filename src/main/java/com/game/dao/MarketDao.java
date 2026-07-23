package com.game.dao;

import com.game.entity.Market;
import com.game.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MarketDao {
    private static final Logger LOGGER = Logger.getLogger(MarketDao.class.getName());

    public int addMarket(Integer itemId, Integer sellerId, Long price) {
        String sql = "INSERT INTO market(item_id, seller_id, price, status) "
                + "VALUES(?, ?, ?, 'ON_SALE')";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, itemId);
            ps.setInt(2, sellerId);
            ps.setLong(3, price);
            return ps.executeUpdate();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "新增市场挂单失败", e);
        }
        return 0;
    }

    public ArrayList<Market> findOnSale() {
        String sql = "SELECT m.id, m.item_id, m.seller_id, m.price, m.status, "
                + "m.create_time, i.item_name, i.rarity "
                + "FROM market m "
                + "JOIN item i ON m.item_id = i.id "
                + "WHERE m.status = 'ON_SALE'";
        ArrayList<Market> list = new ArrayList<>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Market market = new Market();
                market.setId(rs.getInt("id"));
                market.setItemId(rs.getInt("item_id"));
                market.setSellerId(rs.getInt("seller_id"));
                market.setPrice(rs.getLong("price"));
                market.setStatus(rs.getString("status"));
                market.setCreateTime(rs.getTimestamp("create_time"));
                market.setItemName(rs.getString("item_name")); // ✅ 来自JOIN
                market.setRarity(rs.getString("rarity"));       // ✅ 来自JOIN
                list.add(market);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "查询在售市场记录失败", e);
        }
        return list;
    }

    public Market findByIdForUpdate(Connection conn,
                                    Integer marketId) throws Exception {
        String sql = "SELECT * FROM market WHERE id = ? FOR UPDATE";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, marketId);
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return mapRow(rs);
            }
        }
        return null;
    }

    public int updateStatus(Connection conn,
                            Integer marketId,
                            String status) throws Exception {
        String sql = "UPDATE market SET status = ? WHERE id = ?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, status);
        ps.setInt(2, marketId);
        return ps.executeUpdate();
    }
    private Market mapRow(ResultSet rs) throws Exception {
        Market market = new Market();
        market.setId(rs.getInt("id"));
        market.setItemId(rs.getInt("item_id"));
        market.setSellerId(rs.getInt("seller_id"));
        market.setPrice(rs.getLong("price"));
        market.setStatus(rs.getString("status"));
        market.setCreateTime(rs.getTimestamp("create_time"));
        return market;
    }
}
