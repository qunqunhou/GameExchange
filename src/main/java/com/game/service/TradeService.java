package com.game.service;

import com.game.dao.*;
import com.game.entity.Market;
import com.game.entity.Player;
import com.game.util.DBUtil;

import java.sql.Connection;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TradeService {

    private static final Logger LOGGER = Logger.getLogger(TradeService.class.getName());
    private MarketDao marketDao = new MarketDao();
    private PlayerDao playerDao = new PlayerDao();
    private ItemDao itemDao = new ItemDao();
    private TradeRecordDao tradeRecordDao = new TradeRecordDao();

    public String buyItem(Integer marketId, Integer buyerId) {

        Connection conn = null;

        try {
            // ✅ 第一步：从连接池取连接，关闭自动提交，开启事务
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            // ✅ 第二步：FOR UPDATE锁定商品行，防止并发超卖
            Market market = marketDao.findByIdForUpdate(conn, marketId);

            if (market == null) {
                return "商品不存在";
            }
            if (!"ON_SALE".equals(market.getStatus())) {
                return "商品已售出";
            }

            // ✅ 第三步：不能自己买自己
            if (buyerId.equals(market.getSellerId())) {
                return "不能购买自己的商品";
            }

            // ✅ 第四步：查买家和卖家，都用同一个conn
            Player buyer = playerDao.findById(conn, buyerId);
            Player seller = playerDao.findById(conn, market.getSellerId());

            if (buyer == null) return "买家不存在";
            if (seller == null) return "卖家不存在";

            Long price = market.getPrice();

            if (buyer.getGold() < price) {
                return "金币不足";
            }

            // ✅ 第五步：五个操作全部用同一个conn，在同一事务内
            playerDao.updateGold(conn, buyerId, -price);              // 扣买家金币
            playerDao.updateGold(conn, market.getSellerId(), price);  // 加卖家金币
            itemDao.updateOwner(conn, market.getItemId(), buyerId);   // 转移道具
            marketDao.updateStatus(conn, marketId, "SOLD");           // 更新状态
            tradeRecordDao.addRecord(conn, buyerId, market.getSellerId(),
                    market.getItemId(), price);                        // 记录日志

            // ✅ 第六步：全部成功，提交事务
            conn.commit();
            return "购买成功";

        } catch (Exception e) {
            // ✅ 第七步：任何异常，回滚所有操作
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (Exception rollbackEx) {
                    LOGGER.log(Level.SEVERE, "交易事务回滚失败", rollbackEx);
                }
            }
            LOGGER.log(Level.SEVERE, "购买商品失败", e);
            return "交易失败，已回滚";

        } finally {
            // ✅ 第八步：归还连接到连接池
            DBUtil.close(conn);
        }
    }
}
