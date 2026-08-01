package com.game.dao;

import com.game.entity.Item;
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

    public Item findItemForUpdate(Connection conn, Integer itemId) throws Exception {
        String sql = "SELECT id, item_name, rarity, owner_id FROM item WHERE id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Item item = new Item();
                    item.setId(rs.getInt("id"));
                    item.setItemName(rs.getString("item_name"));
                    item.setRarity(rs.getString("rarity"));
                    item.setOwnerId(rs.getInt("owner_id"));
                    return item;
                }
            }
        }
        return null;
    }

    public boolean existsOnSale(Connection conn, Integer itemId) throws Exception {
        String sql = "SELECT 1 FROM market WHERE item_id = ? AND status = 'ON_SALE' LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public int addMarket(Connection conn, Integer itemId, Integer sellerId, Long price)
            throws Exception {
        String sql = "INSERT INTO market(item_id, seller_id, price, status) "
                + "VALUES(?, ?, ?, 'ON_SALE')";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            ps.setInt(2, sellerId);
            ps.setLong(3, price);
            return ps.executeUpdate();
        }
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
                market.setItemName(rs.getString("item_name"));
                market.setRarity(rs.getString("rarity"));
                list.add(market);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "查询在售市场记录失败", e);
        }
        return list;
    }

    public Market findByIdForUpdate(Connection conn, Integer marketId) throws Exception {
        String sql = "SELECT * FROM market WHERE id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, marketId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public int updateStatus(Connection conn, Integer marketId, String status) throws Exception {
        String sql = "UPDATE market SET status = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, marketId);
            return ps.executeUpdate();
        }
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
