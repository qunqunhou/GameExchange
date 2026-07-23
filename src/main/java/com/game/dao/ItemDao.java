package com.game.dao;

import com.game.entity.Item;
import com.game.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ItemDao {
    private static final Logger LOGGER = Logger.getLogger(ItemDao.class.getName());

    public Item findById(Integer itemId) {
        String sql = "SELECT id, item_name, rarity, owner_id FROM item WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "按 ID 查询物品失败", e);
        }
        return null;
    }

    public int updateOwner(Connection conn,
                           Integer itemId,
                           Integer ownerId) throws Exception {
        String sql = "UPDATE item SET owner_id = ? WHERE id = ?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, ownerId);
        ps.setInt(2, itemId);
        return ps.executeUpdate();
    }
    private Item mapRow(ResultSet rs) throws Exception {
        Item item = new Item();
        item.setId(rs.getInt("id"));
        item.setItemName(rs.getString("item_name"));
        item.setRarity(rs.getString("rarity"));
        item.setOwnerId(rs.getInt("owner_id"));
        return item;
    }
}
