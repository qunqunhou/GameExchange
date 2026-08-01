package com.game.integration;

import com.alibaba.fastjson.JSONObject;
import com.game.entity.Player;
import com.game.servlet.BattleServlet;
import com.game.util.DBUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BattleServletConcurrencyIT extends MySqlIntegrationSupport {

    private final List<TestPlayer> testPlayers = new ArrayList<>();

    @BeforeAll
    static void startContainerAndInitializeDatabase() throws Exception {
        startMySql();
    }

    @AfterEach
    void cleanTestData() throws Exception {
        for (int i = testPlayers.size() - 1; i >= 0; i--) {
            cleanupPlayer(testPlayers.get(i));
        }
        testPlayers.clear();
    }

    @Test
    void samePlayerAndRequestIdConcurrentBattleCommitsRewardOnlyOnce()
            throws Exception {
        TestPlayer player = createPlayer();
        String requestId = UUID.randomUUID().toString();
        String monsterName = legalMonsterName();
        Snapshot before = snapshot(player);

        List<ServletResult> results = postBattleConcurrently(player, requestId,
                monsterName);

        Snapshot after = snapshot(player);
        BattleRecordSnapshot battleRecord = findSingleBattleRecord(player.id(),
                requestId);

        assertAll(
                () -> assertEquals(2, results.size()),
                () -> assertTrue(results.stream()
                        .anyMatch(result -> result.jsonCode()
                                == HttpServletResponse.SC_OK)),
                () -> assertNotNull(battleRecord),
                () -> assertEquals(monsterName, battleRecord.monsterName()),
                () -> assertEquals(1, after.battleRecordCount()),
                () -> assertEquals(battleRecord.goldReward(),
                        after.gold() - before.gold()),
                () -> assertEquals(before.killMonsterEventCount() + 1,
                        after.killMonsterEventCount()),
                () -> assertLootSideEffectsCommittedOnlyOnce(before, after,
                        battleRecord)
        );
    }

    private List<ServletResult> postBattleConcurrently(TestPlayer player,
                                                       String requestId,
                                                       String monsterName)
            throws Exception {
        TestableBattleServlet servlet = new TestableBattleServlet();
        CountDownLatch readyGate = new CountDownLatch(2);
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<ServletResult> firstCall = concurrentBattleCall(servlet, player,
                requestId, monsterName, readyGate, startGate);
        Callable<ServletResult> secondCall = concurrentBattleCall(servlet, player,
                requestId, monsterName, readyGate, startGate);

        Future<ServletResult> firstResult = executor.submit(firstCall);
        Future<ServletResult> secondResult = executor.submit(secondCall);
        assertTrue(readyGate.await(5, TimeUnit.SECONDS));
        startGate.countDown();

        try {
            List<ServletResult> results = new ArrayList<>();
            results.add(firstResult.get(30, TimeUnit.SECONDS));
            results.add(secondResult.get(30, TimeUnit.SECONDS));
            return results;
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private Callable<ServletResult> concurrentBattleCall(
            TestableBattleServlet servlet,
            TestPlayer player,
            String requestId,
            String monsterName,
            CountDownLatch readyGate,
            CountDownLatch startGate) {
        return () -> {
            readyGate.countDown();
            assertTrue(startGate.await(5, TimeUnit.SECONDS));
            return postBattle(servlet, player, requestId, monsterName);
        };
    }

    private ServletResult postBattle(TestableBattleServlet servlet,
                                     TestPlayer testPlayer,
                                     String requestId,
                                     String monsterName)
            throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        StringWriter responseBody = new StringWriter();
        PrintWriter writer = new PrintWriter(responseBody);

        Player sessionPlayer = new Player();
        sessionPlayer.setId(testPlayer.id());
        sessionPlayer.setUsername(testPlayer.username());
        sessionPlayer.setGold(testPlayer.initialGold());

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("player")).thenReturn(sessionPlayer);
        when(request.getParameter("requestId")).thenReturn(requestId);
        when(request.getParameter("monsterName")).thenReturn(monsterName);
        when(request.getParameter("playerId"))
                .thenReturn(String.valueOf(testPlayer.id()));
        when(response.getWriter()).thenReturn(writer);

        servlet.post(request, response);
        writer.flush();

        JSONObject json = JSONObject.parseObject(responseBody.toString());
        return new ServletResult(json.getIntValue("code"), json.getString("msg"));
    }

    private void assertLootSideEffectsCommittedOnlyOnce(Snapshot before,
                                                        Snapshot after,
                                                        BattleRecordSnapshot record) {
        if (record.lootItemId() == null) {
            assertAll(
                    () -> assertEquals(before.itemCount(), after.itemCount()),
                    () -> assertEquals(before.itemDropEventCount(),
                            after.itemDropEventCount())
            );
            return;
        }

        assertAll(
                () -> assertEquals(before.itemCount() + 1, after.itemCount()),
                () -> assertEquals(before.itemDropEventCount() + 1,
                        after.itemDropEventCount()),
                () -> assertTrue(playerOwnsItem(record.lootItemId()))
        );
    }

    private TestPlayer createPlayer() throws Exception {
        String username = "bc" + shortToken();
        long gold = 1000L;
        String sql = "INSERT INTO player(username, password, gold) VALUES(?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, "it-password");
            ps.setLong(3, gold);
            assertEquals(1, ps.executeUpdate());
            try (ResultSet rs = ps.getGeneratedKeys()) {
                assertTrue(rs.next());
                TestPlayer player = new TestPlayer(rs.getInt(1), username, gold);
                testPlayers.add(player);
                return player;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private String legalMonsterName() throws Exception {
        Field rewardsField = BattleServlet.class.getDeclaredField("MONSTER_REWARDS");
        rewardsField.setAccessible(true);
        Map<String, ?> rewards = (Map<String, ?>) rewardsField.get(null);
        assertTrue(!rewards.isEmpty());

        Iterator<String> iterator = rewards.keySet().iterator();
        return iterator.next();
    }

    private Snapshot snapshot(TestPlayer player) throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            return new Snapshot(
                    queryLong(conn, "SELECT gold FROM player WHERE id = ?",
                            player.id()),
                    queryLong(conn, "SELECT COUNT(*) FROM battle_record "
                                    + "WHERE player_id = ?",
                            player.id()),
                    queryLong(conn, "SELECT COUNT(*) FROM game_event "
                                    + "WHERE player_name = ? AND event_type = ?",
                            player.username(), "KILL_MONSTER"),
                    queryLong(conn, "SELECT COUNT(*) FROM game_event "
                                    + "WHERE player_name = ? AND event_type = ?",
                            player.username(), "ITEM_DROP"),
                    queryLong(conn, "SELECT COUNT(*) FROM item WHERE owner_id = ?",
                            player.id())
            );
        }
    }

    private BattleRecordSnapshot findSingleBattleRecord(Integer playerId,
                                                        String requestId)
            throws Exception {
        String sql = "SELECT player_id, request_id, monster_name, gold_reward, "
                + "loot_item_id FROM battle_record "
                + "WHERE player_id = ? AND request_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, playerId);
            ps.setString(2, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                int lootItemId = rs.getInt("loot_item_id");
                boolean lootItemWasNull = rs.wasNull();
                BattleRecordSnapshot record = new BattleRecordSnapshot(
                        rs.getInt("player_id"),
                        rs.getString("request_id"),
                        rs.getString("monster_name"),
                        rs.getLong("gold_reward"),
                        lootItemWasNull ? null : lootItemId
                );
                assertTrue(!rs.next());
                return record;
            }
        }
    }

    private boolean playerOwnsItem(Integer itemId) {
        String sql = "SELECT COUNT(*) FROM item WHERE id = ? AND owner_id IN ("
                + "SELECT player_id FROM battle_record WHERE loot_item_id = ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            ps.setInt(2, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getLong(1) == 1L;
            }
        } catch (Exception e) {
            throw new AssertionError("Failed to verify loot item ownership", e);
        }
    }

    private long queryLong(Connection conn, String sql, Object... values)
            throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                Object value = values[i];
                if (value instanceof Integer) {
                    ps.setInt(i + 1, (Integer) value);
                } else if (value instanceof Long) {
                    ps.setLong(i + 1, (Long) value);
                } else {
                    ps.setString(i + 1, String.valueOf(value));
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getLong(1);
            }
        }
    }

    private void cleanupPlayer(TestPlayer player) throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                executeUpdate(conn, "DELETE FROM battle_record WHERE player_id = ?",
                        player.id());
                executeUpdate(conn, "DELETE FROM item WHERE owner_id = ?",
                        player.id());
                executeUpdate(conn, "DELETE FROM game_event WHERE player_name = ?",
                        player.username());
                executeUpdate(conn, "DELETE FROM player WHERE id = ?", player.id());
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

    private void executeUpdate(Connection conn, String sql, String value)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, value);
            ps.executeUpdate();
        }
    }

    private String shortToken() {
        String raw = UUID.randomUUID().toString().replace("-", "");
        return raw.substring(0, 10);
    }

    private static final class TestableBattleServlet extends BattleServlet {
        private void post(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            doPost(request, response);
        }
    }

    private record TestPlayer(Integer id, String username, Long initialGold) {
    }

    private record ServletResult(int jsonCode, String message) {
    }

    private record Snapshot(long gold,
                            long battleRecordCount,
                            long killMonsterEventCount,
                            long itemDropEventCount,
                            long itemCount) {
    }

    private record BattleRecordSnapshot(Integer playerId,
                                        String requestId,
                                        String monsterName,
                                        Long goldReward,
                                        Integer lootItemId) {
    }
}
