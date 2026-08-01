package com.game.integration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseBaselineIT {

    private static final String DATABASE_NAME = "game_exchange";
    private static final String ROOT_PASSWORD = "baseline-root-" + UUID.randomUUID();
    private static final DockerImageName MYSQL_IMAGE = DockerImageName
            .parse("mysql:8.4@sha256:8dbcf531a03aade657e181b9cf2f1d1803ce621a1d55610cb44cb531ab7d7db6")
            .asCompatibleSubstituteFor("mysql");

    private static final Set<String> CORE_TABLES = Set.of(
            "player",
            "item",
            "battle_record",
            "market",
            "trade_record",
            "game_event"
    );

    private static final Set<String> EXPECTED_FOREIGN_KEYS = Set.of(
            "fk_item_owner",
            "fk_market_item",
            "fk_market_seller",
            "fk_trade_record_buyer",
            "fk_trade_record_seller",
            "fk_trade_record_item",
            "fk_battle_record_player",
            "fk_battle_record_loot_item"
    );

    private static final Set<String> EXPECTED_CHECKS = Set.of(
            "chk_player_gold",
            "chk_item_rarity",
            "chk_market_price",
            "chk_market_status",
            "chk_trade_record_price",
            "chk_trade_record_participants",
            "chk_battle_record_gold_reward"
    );

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MYSQL_IMAGE)
            .withDatabaseName("baseline_probe")
            .withUsername("baseline_user")
            .withPassword("baseline-" + UUID.randomUUID())
            .withEnv("MYSQL_ROOT_PASSWORD", ROOT_PASSWORD)
            .withLabel("com.gameexchange.test-scope", "baseline-migration")
            .withReuse(false);

    @BeforeAll
    static void startEmptyMySqlAndApplyBaselineWithCli() throws Exception {
        MYSQL.start();
        assertEquals("0", queryScalar(
                "SELECT COUNT(*) FROM information_schema.schemata "
                        + "WHERE schema_name = '" + DATABASE_NAME + "'"));

        executeScript(requireScript("database/schema.sql"),
                "/tmp/gameexchange-baseline-schema.sql");
        executeScript(requireScript("database/seed.sql"),
                "/tmp/gameexchange-baseline-seed.sql");
    }

    @AfterAll
    static void stopMySql() {
        MYSQL.stop();
    }

    @Test
    void baselineCreatesAllCoreTablesWithExpectedConstraints() throws Exception {
        Set<String> tables = querySet(
                "SELECT table_name "
                        + "FROM information_schema.tables "
                        + "WHERE table_schema = '" + DATABASE_NAME + "' "
                        + "AND table_type = 'BASE TABLE'");
        Set<String> foreignKeys = querySet(
                "SELECT constraint_name "
                        + "FROM information_schema.table_constraints "
                        + "WHERE table_schema = '" + DATABASE_NAME + "' "
                        + "AND constraint_type = 'FOREIGN KEY'");
        Set<String> checks = querySet(
                "SELECT constraint_name "
                        + "FROM information_schema.table_constraints "
                        + "WHERE table_schema = '" + DATABASE_NAME + "' "
                        + "AND constraint_type = 'CHECK'");

        assertAll(
                () -> assertTrue(tables.containsAll(CORE_TABLES)),
                () -> assertTrue(foreignKeys.containsAll(EXPECTED_FOREIGN_KEYS)),
                () -> assertTrue(checks.containsAll(EXPECTED_CHECKS))
        );
    }

    @Test
    void baselineUsesUtf8mb4DatabaseTablesAndAsciiRequestId() throws Exception {
        String schemaCharset = queryScalar(
                "SELECT CONCAT(default_character_set_name, '|', "
                        + "default_collation_name) "
                        + "FROM information_schema.schemata "
                        + "WHERE schema_name = '" + DATABASE_NAME + "'");
        String utf8mb4TableCount = queryScalar(
                "SELECT COUNT(*) "
                        + "FROM information_schema.tables "
                        + "WHERE table_schema = '" + DATABASE_NAME + "' "
                        + "AND table_name IN ('player', 'item', 'battle_record', "
                        + "'market', 'trade_record', 'game_event') "
                        + "AND table_collation = 'utf8mb4_0900_ai_ci'");
        String requestIdCharset = queryScalar(
                "SELECT CONCAT(character_set_name, '|', collation_name) "
                        + "FROM information_schema.columns "
                        + "WHERE table_schema = '" + DATABASE_NAME + "' "
                        + "AND table_name = 'battle_record' "
                        + "AND column_name = 'request_id'");

        assertAll(
                () -> assertEquals("utf8mb4|utf8mb4_0900_ai_ci", schemaCharset),
                () -> assertEquals(String.valueOf(CORE_TABLES.size()),
                        utf8mb4TableCount),
                () -> assertEquals("ascii|ascii_bin", requestIdCharset)
        );
    }

    @Test
    void showCreateTableKeepsUtf8mb4RarityCheckAndBattleRecordContract()
            throws Exception {
        String itemCreateTable = showCreateTable("item");
        String battleRecordCreateTable = showCreateTable("battle_record");

        assertAll(
                () -> assertTrue(itemCreateTable.contains("_utf8mb4'普通'")),
                () -> assertTrue(itemCreateTable.contains("_utf8mb4'稀有'")),
                () -> assertTrue(itemCreateTable.contains("_utf8mb4'史诗'")),
                () -> assertTrue(itemCreateTable.contains("_utf8mb4'传说'")),
                () -> assertTrue(battleRecordCreateTable.contains(
                        "uk_battle_record_player_request")),
                () -> assertTrue(battleRecordCreateTable.contains(
                        "idx_battle_record_player_created_at")),
                () -> assertTrue(battleRecordCreateTable.contains(
                        "fk_battle_record_player")),
                () -> assertTrue(battleRecordCreateTable.contains(
                        "fk_battle_record_loot_item")),
                () -> assertTrue(battleRecordCreateTable.contains(
                        "chk_battle_record_gold_reward"))
        );
    }

    @Test
    void seedDataKeepsChineseUtf8HexWithoutMojibake() throws Exception {
        assertAll(
                () -> assertEquals("1", queryDatabaseScalar(
                        "SELECT COUNT(*) FROM player "
                                + "WHERE username = 'seed_seller'")),
                () -> assertEquals("3", queryDatabaseScalar(
                        "SELECT COUNT(*) FROM item i "
                                + "JOIN player p ON i.owner_id = p.id "
                                + "WHERE p.username = 'seed_seller'")),
                () -> assertEquals("1", queryDatabaseScalar(
                        "SELECT COUNT(*) FROM market m "
                                + "JOIN player p ON m.seller_id = p.id "
                                + "WHERE p.username = 'seed_seller' "
                                + "AND m.status = 'ON_SALE'")),
                () -> assertEquals("2", queryDatabaseScalar(
                        "SELECT COUNT(*) FROM game_event "
                                + "WHERE player_name = 'seed_seller'"))
        );

        assertSeedHex("item", "item_name", "新手铁剑",
                "E696B0E6898BE99381E58991");
        assertSeedHex("item", "item_name", "星尘长剑",
                "E6989FE5B098E995BFE58991");
        assertSeedHex("item", "item_name", "龙鳞护甲",
                "E9BE99E9B39EE68AA4E794B2");
        assertSeedHex("item", "rarity", "普通", "E699AEE9809A");
        assertSeedHex("item", "rarity", "稀有", "E7A880E69C89");
        assertSeedHex("item", "rarity", "史诗", "E58FB2E8AF97");
        assertSeedHex("game_event", "event_desc", "获得【稀有】装备：星尘长剑",
                "E88EB7E5BE97E38090E7A880E69C89E38091E8A385E5A487EFBC9AE6989FE5B098E995BFE58991");
        assertSeedHex("game_event", "event_desc", "获得【史诗】装备：龙鳞护甲",
                "E88EB7E5BE97E38090E58FB2E8AF97E38091E8A385E5A487EFBC9AE9BE99E9B39EE68AA4E794B2");

        String mojibakeCount = queryDatabaseScalar(
                "SELECT ("
                        + "SELECT COUNT(*) FROM item "
                        + "WHERE HEX(item_name) REGEXP '^(C3A6|C3A8|C3A9)' "
                        + "OR HEX(rarity) REGEXP '^(C3A6|C3A8|C3A9)'"
                        + ") + ("
                        + "SELECT COUNT(*) FROM game_event "
                        + "WHERE HEX(event_desc) REGEXP '^(C3A6|C3A8|C3A9)'"
                        + ")");
        assertEquals("0", mojibakeCount);
    }

    private static void assertSeedHex(String tableName,
                                      String columnName,
                                      String value,
                                      String expectedHex) throws Exception {
        String actualHex = queryDatabaseScalar(
                "SELECT HEX(" + columnName + ") "
                        + "FROM " + tableName + " "
                        + "WHERE " + columnName + " = '" + value + "' "
                        + "ORDER BY id "
                        + "LIMIT 1");
        assertEquals(expectedHex, actualHex);
    }

    private static Path requireScript(String relativePath) {
        Path scriptPath = Path.of(relativePath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(scriptPath)) {
            throw new IllegalStateException("Database script not found: "
                    + scriptPath);
        }
        return scriptPath;
    }

    private static void executeScript(Path hostPath, String containerPath)
            throws Exception {
        MYSQL.copyFileToContainer(MountableFile.forHostPath(hostPath),
                containerPath);
        Container.ExecResult result = MYSQL.execInContainer(
                "sh",
                "-c",
                "MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" "
                        + "mysql --default-character-set=utf8mb4 -uroot < "
                        + containerPath
        );
        assertSuccessfulMysql(result, "Database script failed: " + hostPath);
    }

    private static String queryScalar(String sql) throws Exception {
        return runMysqlQuery(sql).trim();
    }

    private static String queryDatabaseScalar(String sql) throws Exception {
        return runMysqlQuery("USE " + DATABASE_NAME + "; " + sql).trim();
    }

    private static Set<String> querySet(String sql) throws Exception {
        String output = runMysqlQuery(sql).trim();
        if (output.isEmpty()) {
            return Set.of();
        }
        return Arrays.stream(output.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
    }

    private static String showCreateTable(String tableName) throws Exception {
        return runMysqlQuery("USE " + DATABASE_NAME + "; SHOW CREATE TABLE "
                + tableName);
    }

    private static String runMysqlQuery(String sql) throws Exception {
        Container.ExecResult result = MYSQL.execInContainer(
                "sh",
                "-c",
                "MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" "
                        + "mysql --default-character-set=utf8mb4 "
                        + "-uroot --batch --skip-column-names -e "
                        + shellQuote(sql)
        );
        assertSuccessfulMysql(result, "MySQL query failed: " + sql);
        return result.getStdout();
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static void assertSuccessfulMysql(Container.ExecResult result,
                                              String message) {
        assertEquals(0, result.getExitCode(),
                message + System.lineSeparator() + result.getStderr());
        assertFalse(result.getStdout().contains("ERROR"), message);
    }
}
