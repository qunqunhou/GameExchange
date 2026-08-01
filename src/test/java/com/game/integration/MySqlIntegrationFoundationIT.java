package com.game.integration;

import com.game.util.DBUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlIntegrationFoundationIT extends MySqlIntegrationSupport {

    private static final Set<String> CORE_TABLES = Set.of(
            "player",
            "item",
            "battle_record",
            "market",
            "trade_record",
            "game_event"
    );

    @BeforeAll
    static void startContainerAndInitializeDatabase() throws Exception {
        startMySql();
    }

    @Test
    void startsTemporaryMySqlOnRandomPort() {
        int mappedPort = mysqlContainer().getMappedPort(MySQLContainer.MYSQL_PORT);

        assertAll(
                () -> assertTrue(mysqlContainer().isRunning()),
                () -> assertTrue(mappedPort > 0),
                () -> assertNotEquals(MySQLContainer.MYSQL_PORT, mappedPort),
                () -> assertFalse(mysqlContainer().getJdbcUrl().contains(":3306/"))
        );
    }

    @Test
    void initializesSchemaAndCoreConstraints() throws Exception {
        Set<String> tables = new HashSet<>();
        Set<String> checks = new HashSet<>();
        Set<String> foreignKeys = new HashSet<>();

        try (Connection conn = DBUtil.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT table_name FROM information_schema.tables "
                            + "WHERE table_schema = DATABASE()")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        tables.add(rs.getString(1));
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT constraint_name, constraint_type "
                            + "FROM information_schema.table_constraints "
                            + "WHERE table_schema = DATABASE() "
                            + "AND constraint_type IN ('CHECK', 'FOREIGN KEY')")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        if ("CHECK".equals(rs.getString("constraint_type"))) {
                            checks.add(rs.getString("constraint_name"));
                        } else {
                            foreignKeys.add(rs.getString("constraint_name"));
                        }
                    }
                }
            }
        }

        assertAll(
                () -> assertTrue(tables.containsAll(CORE_TABLES)),
                () -> assertTrue(checks.contains("chk_item_rarity")),
                () -> assertTrue(checks.contains("chk_battle_record_gold_reward")),
                () -> assertTrue(foreignKeys.contains("fk_item_owner")),
                () -> assertTrue(foreignKeys.contains("fk_market_item")),
                () -> assertTrue(foreignKeys.contains("fk_trade_record_item")),
                () -> assertTrue(foreignKeys.contains("fk_battle_record_player")),
                () -> assertTrue(foreignKeys.contains("fk_battle_record_loot_item"))
        );
    }

    @Test
    void initializesUtf8mb4DatabaseAndTables() throws Exception {
        try (Connection conn = DBUtil.getConnection();
             Statement statement = conn.createStatement()) {
            try (ResultSet rs = statement.executeQuery(
                    "SELECT default_character_set_name, default_collation_name "
                            + "FROM information_schema.schemata "
                            + "WHERE schema_name = DATABASE()")) {
                assertTrue(rs.next());
                assertEquals("utf8mb4", rs.getString("default_character_set_name"));
                assertEquals("utf8mb4_0900_ai_ci", rs.getString("default_collation_name"));
            }

            try (ResultSet rs = statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema = DATABASE() "
                            + "AND table_name IN ('player', 'item', 'battle_record', "
                            + "'market', 'trade_record', 'game_event') "
                            + "AND table_collation = 'utf8mb4_0900_ai_ci'")) {
                assertTrue(rs.next());
                assertEquals(CORE_TABLES.size(), rs.getInt(1));
            }
        }
    }

    @Test
    void initializesSeedData() throws Exception {
        String sql = "SELECT "
                + "(SELECT COUNT(*) FROM player WHERE username = 'seed_seller') "
                + "AS seed_players, "
                + "(SELECT COUNT(*) FROM item i JOIN player p ON i.owner_id = p.id "
                + "WHERE p.username = 'seed_seller') AS seed_items, "
                + "(SELECT COUNT(*) FROM market m JOIN player p ON m.seller_id = p.id "
                + "WHERE p.username = 'seed_seller' AND m.status = 'ON_SALE') "
                + "AS seed_markets, "
                + "(SELECT COUNT(*) FROM game_event "
                + "WHERE player_name = 'seed_seller') AS seed_events";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertAll(
                    () -> assertEquals(1, rs.getInt("seed_players")),
                    () -> assertEquals(3, rs.getInt("seed_items")),
                    () -> assertEquals(1, rs.getInt("seed_markets")),
                    () -> assertEquals(2, rs.getInt("seed_events"))
            );
        }
    }

    @Test
    void dbUtilConnectsToTestcontainerDatabase() throws Exception {
        try (Connection conn = DBUtil.getConnection();
             Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT DATABASE(), @@character_set_connection, @@collation_connection")) {
            assertTrue(rs.next());
            assertAll(
                    () -> assertEquals("game_exchange", rs.getString(1)),
                    () -> assertEquals("utf8mb4", rs.getString(2)),
                    () -> assertEquals("utf8mb4_0900_ai_ci", rs.getString(3))
            );
        }
    }
}
