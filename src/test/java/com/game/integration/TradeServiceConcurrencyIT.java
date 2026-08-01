package com.game.integration;

import com.game.service.TradeService;
import com.game.util.DBUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeServiceConcurrencyIT extends MySqlIntegrationSupport {

    private static final String LOCK_DELAY_TRIGGER = "trg_it_market_lock_delay";

    private final List<Fixture> fixtures = new ArrayList<>();

    @BeforeAll
    static void startContainerAndInitializeDatabase() throws Exception {
        startMySql();
        enableTriggerCreationForTestUser();
    }

    @AfterEach
    void cleanTestDatabase() throws Exception {
        dropLockDelayTrigger();
        cleanupFixtures();
    }

    @Test
    void concurrentBuyersCanOnlyPurchaseSameMarketOnce() throws Exception {
        Fixture fixture = createFixture();
        Snapshot before = snapshot(fixture);
        createMarketLockDelayTrigger(fixture.marketId());

        List<BuyResult> results = runConcurrentBuys(fixture.marketId(),
                fixture.buyerOneId(), fixture.buyerTwoId());

        Snapshot after = snapshot(fixture);
        TradeRecordView record = findOnlyTradeRecord(fixture.itemId());
        BuyResult successResult = resultForBuyer(results, record.buyerId());
        BuyResult failedResult = resultForOtherBuyer(results, record.buyerId());
        int failedBuyerId = failedResult.buyerId();

        assertAll(
                () -> assertEquals(2, results.size()),
                () -> assertNotNull(successResult.result()),
                () -> assertNotNull(failedResult.result()),
                () -> assertNotEquals(successResult.result(), failedResult.result()),
                () -> assertEquals("SOLD", after.marketStatus()),
                () -> assertEquals(before.sellerGold() + fixture.price(), after.sellerGold()),
                () -> assertEquals(record.buyerId(), after.itemOwnerId()),
                () -> assertEquals(1, after.tradeRecordCount()),
                () -> assertEquals(fixture.price(), record.price()),
                () -> assertTrue(record.buyerId() == fixture.buyerOneId()
                        || record.buyerId() == fixture.buyerTwoId()),
                () -> assertEquals(before.goldFor(record.buyerId(), fixture) - fixture.price(),
                        after.goldFor(record.buyerId(), fixture)),
                () -> assertEquals(before.goldFor(failedBuyerId, fixture),
                        after.goldFor(failedBuyerId, fixture))
        );
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

    private Fixture createFixture() throws Exception {
        String token = shortToken();
        long buyerGold = 1000L;
        long sellerGold = 500L;
        long price = 200L;

        try (Connection conn = DBUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int sellerId = insertPlayer(conn, "s" + token, sellerGold);
                int buyerOneId = insertPlayer(conn, "b1" + token, buyerGold);
                int buyerTwoId = insertPlayer(conn, "b2" + token, buyerGold);
                int itemId = insertItem(conn, "it_conc_" + token, sellerId);
                int marketId = insertMarket(conn, itemId, sellerId, price);
                conn.commit();

                Fixture fixture = new Fixture(sellerId, buyerOneId, buyerTwoId,
                        itemId, marketId, price);
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

    private void createMarketLockDelayTrigger(int marketId) throws Exception {
        String sql = "CREATE TRIGGER " + LOCK_DELAY_TRIGGER
                + " BEFORE UPDATE ON market FOR EACH ROW "
                + "BEGIN "
                + "IF OLD.id = " + marketId + " THEN "
                + "DO SLEEP(1); "
                + "END IF; "
                + "END";
        try (Connection conn = DBUtil.getConnection();
             Statement statement = conn.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS " + LOCK_DELAY_TRIGGER);
            statement.execute(sql);
        }
    }

    private List<BuyResult> runConcurrentBuys(int marketId,
                                              int buyerOneId,
                                              int buyerTwoId) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<BuyResult> first = executor.submit(buyTask(marketId, buyerOneId,
                    ready, start));
            Future<BuyResult> second = executor.submit(buyTask(marketId, buyerTwoId,
                    ready, start));

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            BuyResult firstResult = first.get(10, TimeUnit.SECONDS);
            BuyResult secondResult = second.get(10, TimeUnit.SECONDS);
            return List.of(firstResult, secondResult);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private Callable<BuyResult> buyTask(int marketId,
                                        int buyerId,
                                        CountDownLatch ready,
                                        CountDownLatch start) {
        return () -> {
            ready.countDown();
            assertTrue(start.await(5, TimeUnit.SECONDS));
            String result = new TradeService().buyItem(marketId, buyerId);
            return new BuyResult(buyerId, result);
        };
    }

    private Snapshot snapshot(Fixture fixture) throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            return new Snapshot(
                    queryLong(conn, "SELECT gold FROM player WHERE id = ?",
                            fixture.sellerId()),
                    queryLong(conn, "SELECT gold FROM player WHERE id = ?",
                            fixture.buyerOneId()),
                    queryLong(conn, "SELECT gold FROM player WHERE id = ?",
                            fixture.buyerTwoId()),
                    queryInt(conn, "SELECT owner_id FROM item WHERE id = ?",
                            fixture.itemId()),
                    queryString(conn, "SELECT status FROM market WHERE id = ?",
                            fixture.marketId()),
                    queryLong(conn, "SELECT COUNT(*) FROM trade_record WHERE item_id = ?",
                            fixture.itemId())
            );
        }
    }

    private TradeRecordView findOnlyTradeRecord(int itemId) throws Exception {
        String sql = "SELECT buyer_id, price FROM trade_record WHERE item_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                TradeRecordView record = new TradeRecordView(
                        rs.getInt("buyer_id"),
                        rs.getLong("price")
                );
                assertTrue(!rs.next());
                return record;
            }
        }
    }

    private BuyResult resultForBuyer(List<BuyResult> results, int buyerId) {
        return results.stream()
                .filter(result -> result.buyerId() == buyerId)
                .findFirst()
                .orElseThrow();
    }

    private BuyResult resultForOtherBuyer(List<BuyResult> results, int buyerId) {
        return results.stream()
                .filter(result -> result.buyerId() != buyerId)
                .findFirst()
                .orElseThrow();
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

    private static void dropLockDelayTrigger() throws Exception {
        try (Connection conn = DBUtil.getConnection();
             Statement statement = conn.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS " + LOCK_DELAY_TRIGGER);
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
                executeUpdate(conn, "DELETE FROM player WHERE id = ?", fixture.buyerOneId());
                executeUpdate(conn, "DELETE FROM player WHERE id = ?", fixture.buyerTwoId());
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

    private record Fixture(int sellerId,
                           int buyerOneId,
                           int buyerTwoId,
                           int itemId,
                           int marketId,
                           long price) {
    }

    private record BuyResult(int buyerId, String result) {
    }

    private record Snapshot(long sellerGold,
                            long buyerOneGold,
                            long buyerTwoGold,
                            int itemOwnerId,
                            String marketStatus,
                            long tradeRecordCount) {

        long goldFor(int buyerId, Fixture fixture) {
            if (buyerId == fixture.buyerOneId()) {
                return buyerOneGold;
            }
            if (buyerId == fixture.buyerTwoId()) {
                return buyerTwoGold;
            }
            throw new IllegalArgumentException("Unknown buyer id: " + buyerId);
        }
    }

    private record TradeRecordView(int buyerId, long price) {
    }
}
