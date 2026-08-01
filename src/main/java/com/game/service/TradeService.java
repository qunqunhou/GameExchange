package com.game.service;

import com.game.dao.ItemDao;
import com.game.dao.MarketDao;
import com.game.dao.PlayerDao;
import com.game.dao.TradeRecordDao;
import com.game.entity.Market;
import com.game.entity.Player;
import com.game.util.DBUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TradeService {

    private static final Logger LOGGER = Logger.getLogger(TradeService.class.getName());
    private final MarketDao marketDao = new MarketDao();
    private final PlayerDao playerDao = new PlayerDao();
    private final ItemDao itemDao = new ItemDao();
    private final TradeRecordDao tradeRecordDao = new TradeRecordDao();

    public String buyItem(Integer marketId, Integer buyerId) {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            if (marketId == null || buyerId == null) {
                return rollbackAndReturn(conn, "参数错误");
            }

            Market market = marketDao.findByIdForUpdate(conn, marketId);
            if (market == null) {
                return rollbackAndReturn(conn, "商品不存在");
            }
            if (!"ON_SALE".equals(market.getStatus())) {
                return rollbackAndReturn(conn, "商品已售出");
            }
            if (buyerId.equals(market.getSellerId())) {
                return rollbackAndReturn(conn, "不能购买自己的商品");
            }

            Player buyer = playerDao.findById(conn, buyerId);
            Player seller = playerDao.findById(conn, market.getSellerId());
            if (buyer == null) {
                return rollbackAndReturn(conn, "买家不存在");
            }
            if (seller == null) {
                return rollbackAndReturn(conn, "卖家不存在");
            }

            Long price = market.getPrice();
            if (buyer.getGold() < price) {
                return rollbackAndReturn(conn, "金币不足");
            }

            requireSingleRow(playerDao.updateGold(conn, buyerId, -price), "扣减买家金币");
            requireSingleRow(playerDao.updateGold(conn, market.getSellerId(), price),
                    "增加卖家金币");
            requireSingleRow(itemDao.updateOwner(conn, market.getItemId(), buyerId),
                    "转移装备所有权");
            requireSingleRow(marketDao.updateStatus(conn, marketId, "SOLD"),
                    "更新市场状态");
            requireSingleRow(tradeRecordDao.addRecord(conn, buyerId, market.getSellerId(),
                    market.getItemId(), price), "创建交易记录");

            conn.commit();
            return "购买成功";
        } catch (Exception e) {
            rollback(conn);
            LOGGER.log(Level.SEVERE, "购买商品失败", e);
            return "交易失败，已回滚";
        } finally {
            DBUtil.close(conn);
        }
    }

    private String rollbackAndReturn(Connection conn, String message) throws SQLException {
        conn.rollback();
        return message;
    }

    private void rollback(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            conn.rollback();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "交易事务回滚失败", e);
        }
    }

    private void requireSingleRow(int rows, String operation) throws SQLException {
        if (rows != 1) {
            throw new SQLException(operation + "影响行数异常: " + rows);
        }
    }
}
