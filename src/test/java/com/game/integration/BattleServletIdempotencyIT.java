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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BattleServletIdempotencyIT extends MySqlIntegrationSupport {

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
    void sameRequestIdReplayDoesNotDuplicateRewardsAndMonsterConflictIsRejected()
            throws Exception {
        TestPlayer player = createPlayer();
        String requestId = UUID.randomUUID().toString();
        List<String> monsterNames = legalMonsterNames();
        String monsterName = monsterNames.get(0);
        String differentMonsterName = monsterNames.get(1);

        Snapshot beforeFirstBattle = snapshot(player);

        ServletResult firstResult = postBattle(player, requestId, monsterName);
        BattleRecordSnapshot recordAfterFirstBattle = findBattleRecord(player.id(),
                requestId);
        Snapshot afterFirstBattle = snapshot(player);

        assertAll(
                () -> assertEquals(HttpServletResponse.SC_OK,
                        firstResult.jsonCode()),
                () -> assertNotNull(recordAfterFirstBattle),
                () -> assertEquals(monsterName, recordAfterFirstBattle.monsterName()),
                () -> assertEquals(1, afterFirstBattle.battleRecordCount()),
                () -> assertEquals(recordAfterFirstBattle.goldReward(),
                        afterFirstBattle.gold() - beforeFirstBattle.gold()),
                () -> assertRewardSideEffects(beforeFirstBattle, afterFirstBattle,
                        recordAfterFirstBattle)
        );

        ServletResult replayResult = postBattle(player, requestId, monsterName);
        Snapshot afterReplay = snapshot(player);

        assertAll(
                () -> assertEquals(HttpServletResponse.SC_OK,
                        replayResult.jsonCode()),
                () -> assertEquals(afterFirstBattle, afterReplay)
        );

        ServletResult conflictResult = postBattle(player, requestId,
                differentMonsterName);
        Snapshot afterConflict = snapshot(player);

        assertAll(
                () -> assertEquals(HttpServletResponse.SC_CONFLICT,
                        conflictResult.jsonCode()),
                () -> assertEquals(afterReplay, afterConflict)
        );
    }

    private void assertRewardSideEffects(Snapshot before,
                                         Snapshot after,
                                         BattleRecordSnapshot record) {
        if (record.lootItemId() == null) {
            assertAll(
                    () -> assertEquals(before.itemCount(), after.itemCount()),
                    () -> assertEquals(before.eventCount() + 1, after.eventCount())
            );
            return;
        }

        assertAll(
                () -> assertEquals(before.itemCount() + 1, after.itemCount()),
                () -> assertEquals(before.eventCount() + 2, after.eventCount()),
                () -> assertTrue(playerOwnsItem(record.lootItemId()))
        );
    }

    private TestPlayer createPlayer() throws Exception {
        String username = "bt" + shortToken();
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

    private ServletResult postBattle(TestPlayer testPlayer,
                                     String requestId,
                                     String monsterName)
            throws ServletException, IOException {
        TestableBattleServlet servlet = new TestableBattleServlet();
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
        when(request.getParameter("playerId")).thenReturn(String.valueOf(testPlayer.id()));
        when(response.getWriter()).thenReturn(writer);

        servlet.post(request, response);
        writer.flush();

        JSONObject json = JSONObject.parseObject(responseBody.toString());
        return new ServletResult(json.getIntValue("code"), json.getString("msg"));
    }

    @SuppressWarnings("unchecked")
    private List<String> legalMonsterNames() throws Exception {
        Field rewardsField = BattleServlet.class.getDeclaredField("MONSTER_REWARDS");
        rewardsField.setAccessible(true);
        Map<String, ?> rewards = (Map<String, ?>) rewardsField.get(null);
        assertTrue(rewards.size() >= 2);

        Iterator<String> iterator = rewards.keySet().iterator();
        String first = iterator.next();
        String second = iterator.next();
        assertNotEquals(first, second);
        return List.of(first, second);
    }

    private Snapshot snapshot(TestPlayer player) throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            return new Snapshot(
                    queryLong(conn, "SELECT gold FROM player WHERE id = ?", player.id()),
                    queryLong(conn, "SELECT COUNT(*) FROM battle_record WHERE player_id = ?",
                            player.id()),
                    queryLong(conn, "SELECT COUNT(*) FROM game_event WHERE player_name = ?",
                            player.username()),
                    queryLong(conn, "SELECT COUNT(*) FROM item WHERE owner_id = ?",
                            player.id())
            );
        }
    }

    private BattleRecordSnapshot findBattleRecord(Integer playerId, String requestId)
            throws Exception {
        String sql = "SELECT player_id, request_id, monster_name, gold_reward, loot_item_id "
                + "FROM battle_record WHERE player_id = ? AND request_id = ?";
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

    private long queryLong(Connection conn, String sql, int id) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getLong(1);
            }
        }
    }

    private long queryLong(Connection conn, String sql, String value) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, value);
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
                executeUpdate(conn, "DELETE FROM item WHERE owner_id = ?", player.id());
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

    private void executeUpdate(Connection conn, String sql, String value) throws SQLException {
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
                            long eventCount,
                            long itemCount) {
    }

    private record BattleRecordSnapshot(Integer playerId,
                                        String requestId,
                                        String monsterName,
                                        Long goldReward,
                                        Integer lootItemId) {
    }
}
