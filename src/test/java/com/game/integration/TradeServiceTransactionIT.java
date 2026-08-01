package com.game.integration;

import com.game.service.TradeService;
import com.game.util.DBUtil;
import org.testcontainers.containers.Container;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeServiceTransactionIT extends MySqlIntegrationSupport {

    private static final String BUYER_GOLD_TRIGGER = "trg_it_buyer_gold_update";
    private static final String SELLER_GOLD_TRIGGER = "trg_it_seller_gold_update";
    private static final String ITEM_OWNER_TRIGGER = "trg_it_item_owner_update";
    private static final String MARKET_STATUS_TRIGGER = "trg_it_market_status_update";
    private static final String TRADE_RECORD_TRIGGER = "trg_it_trade_record_insert";
    private static final List<String> TEST_TRIGGERS = List.of(
            BUYER_GOLD_TRIGGER,
            SELLER_GOLD_TRIGGER,
            ITEM_OWNER_TRIGGER,
            MARKET_STATUS_TRIGGER,
            TRADE_RECORD_TRIGGER
    );

    private final List<Fixture> fixtures = new ArrayList<>();

    @BeforeAll
    static void startContainerAndInitializeDatabase() throws Exception {
        startMySql();
        enableTriggerCreationForTestUser();
        assertTradeTablesUseInnoDb();
    }

    @AfterEach
    void cleanTestDatabase() throws Exception {
        dropAllTestTriggers();
        cleanupFixtures();
    }

    @Test
    void buyItemCommitsAllTradeChanges() throws Exception {
        Fixture fixture = createFixture("normal");
        Snapshot before = snapshot(fixture);

        String result = new TradeService().buyItem(fixture.marketId(), fixture.buyerId());

        Snapshot after = snapshot(fixture);
        TradeRecordView record = findOnlyTradeRecord(fixture.itemId());

        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals(before.buyerGold() - fixture.price(), after.buyerGold()),
                () -> assertEquals(before.sellerGold() + fixture.price(), after.sellerGold()),
                () -> assertEquals(fixture.buyerId(), after.itemOwnerId()),
                () -> assertEquals("SOLD", after.marketStatus()),
                () -> assertEquals(before.tradeRecordCount() + 1, after.tradeRecordCount()),
                () -> assertEquals(fixture.buyerId(), record.buyerId()),
                () -> assertEquals(fixture.sellerId(), record.sellerId()),
                () -> assertEquals(fixture.itemId(), record.itemId()),
                () -> assertEquals(fixture.price(), record.price()),
                () -> assertNotNull(record.tradeTime())
        );
    }

    @Test
    void rollsBackWhenBuyerGoldUpdateFails() throws Exception {
        assertRollbackWhenTriggerFails("buyer", fixture ->
                createPlayerUpdateFailureTrigger(BUYER_GOLD_TRIGGER, fixture.buyerId(),
                        "buyer gold update failure"));
    }

    @Test
    void rollsBackWhenSellerGoldUpdateFails() throws Exception {
        assertRollbackWhenTriggerFails("seller", fixture ->
                createPlayerUpdateFailureTrigger(SELLER_GOLD_TRIGGER, fixture.sellerId(),
                        "seller gold update failure"));
    }

    @Test
    void rollsBackWhenItemOwnerUpdateFails() throws Exception {
        assertRollbackWhenTriggerFails("item", fixture ->
                createItemUpdateFailureTrigger(fixture.itemId()));
    }

    @Test
    void rollsBackWhenMarketStatusUpdateFails() throws Exception {
        assertRollbackWhenTriggerFails("market", fixture ->
                createMarketUpdateFailureTrigger(fixture.marketId()));
    }

    @Test
    void rollsBackWhenTradeRecordInsertFails() throws Exception {
        assertRollbackWhenTriggerFails("record", fixture ->
                createTradeRecordInsertFailureTrigger(fixture.itemId()));
    }

    private void assertRollbackWhenTriggerFails(String scenario,
                                                TriggerSetup triggerSetup) throws Exception {
        Fixture fixture = createFixture(scenario);
        Snapshot before = snapshot(fixture);
        triggerSetup.create(fixture);

        String result = new TradeService().buyItem(fixture.marketId(), fixture.buyerId());

        Snapshot after = snapshot(fixture);
        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals(before, after)
        );
    }

    private static void assertTradeTablesUseInnoDb() throws Exception {
        String sql = "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() "
                + "AND table_name IN ('player', 'item', 'market', 'trade_record') "
                + "AND engine = 'InnoDB'";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(4, rs.getInt(1));
        }
    }

    private static void enableTriggerCreationForTestUser() throws Exception {
        Container.ExecResult result = mysqlContainer().execInContainer(
                "sh",
                "-c",
                "MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" "
                        + "mysql -uroot -e "
                        + "\"SET GLOBAL log_bin_trust_function_creators = 1\""
        );
        assertEquals(0, result.getExitCode(), result.getStderr());
    }

    private Fixture createFixture(String scenario) throws Exception {
        String token = shortToken();
        long buyerGold = 1000L;
        long sellerGold = 500L;
        long price = 200L;

        try (Connection conn = DBUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int sellerId = insertPlayer(conn, "s" + token, sellerGold);
                int buyerId = insertPlayer(conn, "b" + token, buyerGold);
                int itemId = insertItem(conn, "it_" + scenario + "_" + token, sellerId);
                int marketId = insertMarket(conn, itemId, sellerId, price);
                conn.commit();

                Fixture fixture = new Fixture(buyerId, sellerId, itemId, marketId, price);
                fixtures.add(fixture);
                return fixture;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private int insertPlayer(Connection conn, String username, long gold) throws Exception {
        String sql = "INSERT INTO player(username, password, gold) VALUES(?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, "it-password");
            ps.setLong(3, gold);
            assertEquals(1, ps.executeUpdate());
            return generatedIntKey(ps);
        }
    }

    private int insertItem(Connection conn, String itemName, int ownerId) throws Exception {
        String sql = "INSERT INTO item(item_name, rarity, owner_id) VALUES(?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, itemName);
            ps.setString(2, "\u666E\u901A");
            ps.setInt(3, ownerId);
            assertEquals(1, ps.executeUpdate());
            return generatedIntKey(ps);
        }
    }

    private int insertMarket(Connection conn, int itemId, int sellerId, long price)
            throws Exception {
        String sql = "INSERT INTO market(item_id, seller_id, price, status) "
                + "VALUES(?, ?, ?, 'ON_SALE')";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, itemId);
            ps.setInt(2, sellerId);
            ps.setLong(3, price);
            assertEquals(1, ps.executeUpdate());
            return generatedIntKey(ps);
        }
    }

    private int generatedIntKey(PreparedStatement ps) throws Exception {
        try (ResultSet rs = ps.getGeneratedKeys()) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    private Snapshot snapshot(Fixture fixture) throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            return new Snapshot(
                    queryLong(conn, "SELECT gold FROM player WHERE id = ?", fixture.buyerId()),
                    queryLong(conn, "SELECT gold FROM player WHERE id = ?", fixture.sellerId()),
                    queryInt(conn, "SELECT owner_id FROM item WHERE id = ?", fixture.itemId()),
                    queryString(conn, "SELECT status FROM market WHERE id = ?", fixture.marketId()),
                    queryLong(conn, "SELECT COUNT(*) FROM trade_record WHERE item_id = ?",
                            fixture.itemId())
            );
        }
    }

    private TradeRecordView findOnlyTradeRecord(int itemId) throws Exception {
        String sql = "SELECT id, buyer_id, seller_id, item_id, price, trade_time "
                + "FROM trade_record WHERE item_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                TradeRecordView record = new TradeRecordView(
                        rs.getLong("id"),
                        rs.getInt("buyer_id"),
                        rs.getInt("seller_id"),
                        rs.getInt("item_id"),
                        rs.getLong("price"),
                        rs.getTimestamp("trade_time")
                );
                assertTrue(record.id() > 0);
                assertTrue(!rs.next());
                return record;
            }
        }
    }

    private long queryLong(Connection conn, String sql, int id) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getLong(1);
            }
        }
    }

    private int queryInt(Connection conn, String sql, int id) throws Exception {
        return Math.toIntExact(queryLong(conn, sql, id));
    }

    private String queryString(Connection conn, String sql, int id) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getString(1);
            }
        }
    }

    private void createPlayerUpdateFailureTrigger(String triggerName,
                                                  int playerId,
                                                  String message) throws Exception {
        createTrigger(triggerName, "CREATE TRIGGER " + triggerName
                + " BEFORE UPDATE ON player FOR EACH ROW "
                + "BEGIN "
                + "IF OLD.id = " + playerId + " THEN "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '" + message + "'; "
                + "END IF; "
                + "END");
    }

    private void createItemUpdateFailureTrigger(int itemId) throws Exception {
        createTrigger(ITEM_OWNER_TRIGGER, "CREATE TRIGGER " + ITEM_OWNER_TRIGGER
                + " BEFORE UPDATE ON item FOR EACH ROW "
                + "BEGIN "
                + "IF OLD.id = " + itemId + " THEN "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'item owner update failure'; "
                + "END IF; "
                + "END");
    }

    private void createMarketUpdateFailureTrigger(int marketId) throws Exception {
        createTrigger(MARKET_STATUS_TRIGGER, "CREATE TRIGGER " + MARKET_STATUS_TRIGGER
                + " BEFORE UPDATE ON market FOR EACH ROW "
                + "BEGIN "
                + "IF OLD.id = " + marketId + " THEN "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'market status update failure'; "
                + "END IF; "
                + "END");
    }

    private void createTradeRecordInsertFailureTrigger(int itemId) throws Exception {
        createTrigger(TRADE_RECORD_TRIGGER, "CREATE TRIGGER " + TRADE_RECORD_TRIGGER
                + " BEFORE INSERT ON trade_record FOR EACH ROW "
                + "BEGIN "
                + "IF NEW.item_id = " + itemId + " THEN "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'trade record insert failure'; "
                + "END IF; "
                + "END");
    }

    private void createTrigger(String triggerName, String sql) throws Exception {
        try (Connection conn = DBUtil.getConnection();
             Statement statement = conn.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS " + triggerName);
            statement.execute(sql);
        }
    }

    private static void dropAllTestTriggers() throws Exception {
        try (Connection conn = DBUtil.getConnection();
             Statement statement = conn.createStatement()) {
            for (String trigger : TEST_TRIGGERS) {
                statement.execute("DROP TRIGGER IF EXISTS " + trigger);
            }
        }
    }

    private void cleanupFixtures() throws Exception {
        for (int i = fixtures.size() - 1; i >= 0; i--) {
            cleanupFixture(fixtures.get(i));
        }
        fixtures.clear();
    }

    private void cleanupFixture(Fixture fixture) throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                executeUpdate(conn, "DELETE FROM trade_record WHERE item_id = ?",
                        fixture.itemId());
                executeUpdate(conn, "DELETE FROM market WHERE id = ?", fixture.marketId());
                executeUpdate(conn, "DELETE FROM item WHERE id = ?", fixture.itemId());
                executeUpdate(conn, "DELETE FROM player WHERE id = ?", fixture.buyerId());
                executeUpdate(conn, "DELETE FROM player WHERE id = ?", fixture.sellerId());
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private void executeUpdate(Connection conn, String sql, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private String shortToken() {
        String raw = UUID.randomUUID().toString().replace("-", "");
        return raw.substring(0, 10);
    }

    @FunctionalInterface
    private interface TriggerSetup {
        void create(Fixture fixture) throws Exception;
    }

    private record Fixture(int buyerId,
                           int sellerId,
                           int itemId,
                           int marketId,
                           long price) {
    }

    private record Snapshot(long buyerGold,
                            long sellerGold,
                            int itemOwnerId,
                            String marketStatus,
                            long tradeRecordCount) {
    }

    private record TradeRecordView(long id,
                                   int buyerId,
                                   int sellerId,
                                   int itemId,
                                   long price,
                                   Timestamp tradeTime) {
    }
}
