package com.game.integration;

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

class DatabaseUpgradeIT {

    private static final String DATABASE_NAME = "game_exchange";
    private static final DockerImageName MYSQL_IMAGE = DockerImageName
            .parse("mysql:8.4@sha256:8dbcf531a03aade657e181b9cf2f1d1803ce621a1d55610cb44cb531ab7d7db6")
            .asCompatibleSubstituteFor("mysql");

    private static final Path LEGACY_SCHEMA = requireScript(
            "src/test/resources/db/legacy/phase-1.5-schema.sql");
    private static final Path LEGACY_SEED = requireScript(
            "src/test/resources/db/legacy/phase-1.5-seed.sql");
    private static final Path PHASE_3A = requireScript(
            "database/migrations/phase-3a-add-player-last-seen-at.sql");
    private static final Path DB_REPAIR = requireScript(
            "database/migrations/phase-db-repair-item-rarity-check.sql");
    private static final Path PHASE_4C = requireScript(
            "database/migrations/phase-4c-drop-player-online-status.sql");
    private static final Path PHASE_5A = requireScript(
            "database/migrations/phase-5a-data-repair-charset-mojibake.sql");
    private static final Path PHASE_5C = requireScript(
            "database/migrations/phase-5c-add-battle-record.sql");

    private static final String LEGACY_ITEM_NAME_HEX = String.join("|",
            "1:C3A6E28093C2B0C3A6E280B0E280B9C3A9E2809CC281C3A5E280B0E28098",
            "2:C3A6CB9CC5B8C3A5C2B0CB9CC3A9E280A2C2BFC3A5E280B0E28098",
            "3:C3A9C2BEE284A2C3A9C2B3C5BEC3A6C5A0C2A4C3A7E2809DC2B2");
    private static final String LEGACY_RARITY_HEX = String.join("|",
            "1:C3A6E284A2C2AEC3A9E282ACC5A1",
            "2:C3A7C2A8E282ACC3A6C593E280B0",
            "3:C3A5C28FC2B2C3A8C2AFE28094");
    private static final String LEGACY_EVENT_HEX = String.join("|",
            "1:C3A8C5BDC2B7C3A5C2BEE28094C3A3E282ACC290C3A7C2A8E282ACC3A6C593E280B0C3A3E282ACE28098C3A8C2A3E280A6C3A5C2A4E280A1C3AFC2BCC5A1C3A6CB9CC5B8C3A5C2B0CB9CC3A9E280A2C2BFC3A5E280B0E28098",
            "2:C3A8C5BDC2B7C3A5C2BEE28094C3A3E282ACC290C3A5C28FC2B2C3A8C2AFE28094C3A3E282ACE28098C3A8C2A3E280A6C3A5C2A4E280A1C3AFC2BCC5A1C3A9C2BEE284A2C3A9C2B3C5BEC3A6C5A0C2A4C3A7E2809DC2B2");

    private static final String REPAIRED_ITEM_NAME_HEX = String.join("|",
            "1:E696B0E6898BE99381E58991",
            "2:E6989FE5B098E995BFE58991",
            "3:E9BE99E9B39EE68AA4E794B2");
    private static final String REPAIRED_RARITY_HEX = String.join("|",
            "1:E699AEE9809A",
            "2:E7A880E69C89",
            "3:E58FB2E8AF97");
    private static final String REPAIRED_EVENT_HEX = String.join("|",
            "1:E88EB7E5BE97E38090E7A880E69C89E38091E8A385E5A487EFBC9AE6989FE5B098E995BFE58991",
            "2:E88EB7E5BE97E38090E58FB2E8AF97E38091E8A385E5A487EFBC9AE9BE99E9B39EE68AA4E794B2");

    @Test
    void upgradesRealPhase15DatabaseThroughAllMigrations() throws Exception {
        try (MySQLContainer<?> mysql = createEmptyMySql("upgrade-happy")) {
            mysql.start();
            assertDatabaseDoesNotExist(mysql);
            applyLegacyFixture(mysql);
            assertLegacyPreflight(mysql);

            executeScript(mysql, PHASE_3A, "/tmp/phase-3a.sql", "utf8mb4");
            assertPhase3aState(mysql);

            executeScript(mysql, DB_REPAIR, "/tmp/phase-db-repair.sql",
                    "utf8mb4");
            assertRarityRepair(mysql);

            executeScript(mysql, PHASE_4C, "/tmp/phase-4c.sql", "utf8mb4");
            assertPhase4cState(mysql);

            MigrationStatus phase5aStatus = executePhase5a(mysql);
            assertEquals(MigrationStatus.COMMITTED, phase5aStatus);
            assertPhase5aRepair(mysql);

            executeScript(mysql, PHASE_5C, "/tmp/phase-5c.sql", "utf8mb4");
            assertPhase5cState(mysql);
        }
    }

    @Test
    void stopsUpgradeWhenPhase5aHexGuardDoesNotMatch() throws Exception {
        try (MySQLContainer<?> mysql = createEmptyMySql("upgrade-guard-failure")) {
            mysql.start();
            assertDatabaseDoesNotExist(mysql);
            applyLegacyFixture(mysql);
            assertLegacyPreflight(mysql);

            executeScript(mysql, PHASE_3A, "/tmp/phase-3a.sql", "utf8mb4");
            executeScript(mysql, DB_REPAIR, "/tmp/phase-db-repair.sql",
                    "utf8mb4");
            executeScript(mysql, PHASE_4C, "/tmp/phase-4c.sql", "utf8mb4");

            runDatabaseStatement(mysql,
                    "UPDATE item SET item_name = _utf8mb4'guard-mismatch' "
                            + "WHERE id = 1");
            String beforePhase5a = targetDataSnapshot(mysql);

            MigrationStatus phase5aStatus = executePhase5a(mysql);
            if (phase5aStatus == MigrationStatus.COMMITTED) {
                executeScript(mysql, PHASE_5C, "/tmp/phase-5c.sql",
                        "utf8mb4");
            }

            assertAll(
                    () -> assertEquals(MigrationStatus.ROLLED_BACK,
                            phase5aStatus),
                    () -> assertEquals(beforePhase5a,
                            targetDataSnapshot(mysql)),
                    () -> assertFalse(tableExists(mysql, "battle_record"))
            );
        }
    }

    private static MySQLContainer<?> createEmptyMySql(String testScope) {
        return new MySQLContainer<>(MYSQL_IMAGE)
                .withDatabaseName("upgrade_probe")
                .withUsername("upgrade_user")
                .withPassword("upgrade-" + UUID.randomUUID())
                .withEnv("MYSQL_ROOT_PASSWORD", "upgrade-root-" + UUID.randomUUID())
                .withLabel("com.gameexchange.test-scope", testScope)
                .withReuse(false);
    }

    private static void applyLegacyFixture(MySQLContainer<?> mysql)
            throws Exception {
        // latin1 客户端复现该历史版本真实出现过的双重编码状态。
        executeScript(mysql, LEGACY_SCHEMA, "/tmp/phase-1.5-schema.sql",
                "latin1");
        executeScript(mysql, LEGACY_SEED, "/tmp/phase-1.5-seed.sql",
                "latin1");
    }

    private static void assertDatabaseDoesNotExist(MySQLContainer<?> mysql)
            throws Exception {
        assertEquals("0", queryScalar(mysql,
                "SELECT COUNT(*) FROM information_schema.schemata "
                        + "WHERE schema_name = '" + DATABASE_NAME + "'"));
    }

    private static void assertLegacyPreflight(MySQLContainer<?> mysql)
            throws Exception {
        String itemCreateTable = showCreateTable(mysql, "item");

        assertAll(
                () -> assertTrue(columnExists(mysql, "player",
                        "online_status")),
                () -> assertTrue(constraintExists(mysql, "player",
                        "chk_player_online_status")),
                () -> assertFalse(columnExists(mysql, "player",
                        "last_seen_at")),
                () -> assertFalse(tableExists(mysql, "battle_record")),
                () -> assertTrue(itemCreateTable.contains(
                        "chk_item_rarity")),
                () -> assertTrue(itemCreateTable.contains("_latin1")),
                () -> assertFalse(itemCreateTable.contains("_utf8mb4")),
                () -> assertEquals(LEGACY_ITEM_NAME_HEX,
                        itemNameHex(mysql)),
                () -> assertEquals(LEGACY_RARITY_HEX,
                        rarityHex(mysql)),
                () -> assertEquals(LEGACY_EVENT_HEX,
                        eventHex(mysql))
        );
    }

    private static void assertPhase3aState(MySQLContainer<?> mysql)
            throws Exception {
        assertAll(
                () -> assertTrue(columnExists(mysql, "player",
                        "last_seen_at")),
                () -> assertTrue(indexExists(mysql, "player",
                        "idx_player_last_seen_at")),
                () -> assertTrue(columnExists(mysql, "player",
                        "online_status"))
        );
    }

    private static void assertRarityRepair(MySQLContainer<?> mysql)
            throws Exception {
        String itemCreateTable = showCreateTable(mysql, "item");

        assertAll(
                () -> assertTrue(itemCreateTable.contains(
                        "_utf8mb4'普通'")),
                () -> assertTrue(itemCreateTable.contains(
                        "_utf8mb4'稀有'")),
                () -> assertTrue(itemCreateTable.contains(
                        "_utf8mb4'史诗'")),
                () -> assertTrue(itemCreateTable.contains(
                        "_utf8mb4'传说'")),
                () -> assertEquals(REPAIRED_RARITY_HEX,
                        rarityHex(mysql))
        );
    }

    private static void assertPhase4cState(MySQLContainer<?> mysql)
            throws Exception {
        assertAll(
                () -> assertFalse(columnExists(mysql, "player",
                        "online_status")),
                () -> assertFalse(constraintExists(mysql, "player",
                        "chk_player_online_status"))
        );
    }

    private static void assertPhase5aRepair(MySQLContainer<?> mysql)
            throws Exception {
        String mojibakeCount = queryDatabaseScalar(mysql,
                "SELECT ("
                        + "SELECT COUNT(*) FROM item "
                        + "WHERE id IN (1, 2, 3) AND ("
                        + "HEX(item_name) REGEXP '^(C3A6|C3A8|C3A9)' "
                        + "OR HEX(rarity) REGEXP '^(C3A6|C3A8|C3A9)')"
                        + ") + ("
                        + "SELECT COUNT(*) FROM game_event "
                        + "WHERE id IN (1, 2) "
                        + "AND HEX(event_desc) REGEXP '^(C3A6|C3A8|C3A9)'"
                        + ")");

        assertAll(
                () -> assertEquals(REPAIRED_ITEM_NAME_HEX,
                        itemNameHex(mysql)),
                () -> assertEquals(REPAIRED_RARITY_HEX,
                        rarityHex(mysql)),
                () -> assertEquals(REPAIRED_EVENT_HEX,
                        eventHex(mysql)),
                () -> assertEquals("0", mojibakeCount)
        );
    }

    private static void assertPhase5cState(MySQLContainer<?> mysql)
            throws Exception {
        String createTable = showCreateTable(mysql, "battle_record");

        assertAll(
                () -> assertTrue(tableExists(mysql, "battle_record")),
                () -> assertTrue(createTable.contains(
                        "uk_battle_record_player_request")),
                () -> assertTrue(createTable.contains(
                        "idx_battle_record_player_created_at")),
                () -> assertTrue(createTable.contains(
                        "fk_battle_record_player")),
                () -> assertTrue(createTable.contains(
                        "fk_battle_record_loot_item")),
                () -> assertTrue(createTable.contains(
                        "chk_battle_record_gold_reward")),
                () -> assertEquals("0", queryDatabaseScalar(mysql,
                        "SELECT COUNT(*) FROM battle_record"))
        );
    }

    private static MigrationStatus executePhase5a(MySQLContainer<?> mysql)
            throws Exception {
        Container.ExecResult result = executeScript(mysql, PHASE_5A,
                "/tmp/phase-5a.sql", "utf8mb4");
        Set<String> outputLines = Arrays.stream(result.getStdout().split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
        boolean committed = outputLines.contains("COMMITTED");
        boolean rolledBack = outputLines.contains("ROLLED_BACK");

        assertTrue(committed ^ rolledBack,
                "Phase 5A must report exactly one migration status. Output: "
                        + result.getStdout());
        return committed ? MigrationStatus.COMMITTED
                : MigrationStatus.ROLLED_BACK;
    }

    private static String targetDataSnapshot(MySQLContainer<?> mysql)
            throws Exception {
        return itemNameHex(mysql) + System.lineSeparator()
                + rarityHex(mysql) + System.lineSeparator()
                + eventHex(mysql);
    }

    private static String itemNameHex(MySQLContainer<?> mysql)
            throws Exception {
        return queryDatabaseScalar(mysql,
                "SELECT GROUP_CONCAT(CONCAT(id, ':', HEX(item_name)) "
                        + "ORDER BY id SEPARATOR '|') FROM item "
                        + "WHERE id IN (1, 2, 3)");
    }

    private static String rarityHex(MySQLContainer<?> mysql)
            throws Exception {
        return queryDatabaseScalar(mysql,
                "SELECT GROUP_CONCAT(CONCAT(id, ':', HEX(rarity)) "
                        + "ORDER BY id SEPARATOR '|') FROM item "
                        + "WHERE id IN (1, 2, 3)");
    }

    private static String eventHex(MySQLContainer<?> mysql)
            throws Exception {
        return queryDatabaseScalar(mysql,
                "SELECT GROUP_CONCAT(CONCAT(id, ':', HEX(event_desc)) "
                        + "ORDER BY id SEPARATOR '|') FROM game_event "
                        + "WHERE id IN (1, 2)");
    }

    private static boolean tableExists(MySQLContainer<?> mysql,
                                       String tableName) throws Exception {
        return "1".equals(queryScalar(mysql,
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = '" + DATABASE_NAME + "' "
                        + "AND table_name = '" + tableName + "'"));
    }

    private static boolean columnExists(MySQLContainer<?> mysql,
                                        String tableName,
                                        String columnName) throws Exception {
        return "1".equals(queryScalar(mysql,
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = '" + DATABASE_NAME + "' "
                        + "AND table_name = '" + tableName + "' "
                        + "AND column_name = '" + columnName + "'"));
    }

    private static boolean constraintExists(MySQLContainer<?> mysql,
                                            String tableName,
                                            String constraintName)
            throws Exception {
        return "1".equals(queryScalar(mysql,
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE table_schema = '" + DATABASE_NAME + "' "
                        + "AND table_name = '" + tableName + "' "
                        + "AND constraint_name = '" + constraintName + "'"));
    }

    private static boolean indexExists(MySQLContainer<?> mysql,
                                       String tableName,
                                       String indexName) throws Exception {
        return "1".equals(queryScalar(mysql,
                "SELECT COUNT(DISTINCT index_name) "
                        + "FROM information_schema.statistics "
                        + "WHERE table_schema = '" + DATABASE_NAME + "' "
                        + "AND table_name = '" + tableName + "' "
                        + "AND index_name = '" + indexName + "'"));
    }

    private static String showCreateTable(MySQLContainer<?> mysql,
                                          String tableName) throws Exception {
        return runMysqlQuery(mysql, "USE " + DATABASE_NAME
                + "; SHOW CREATE TABLE " + tableName);
    }

    private static void runDatabaseStatement(MySQLContainer<?> mysql,
                                             String sql) throws Exception {
        runMysqlQuery(mysql, "USE " + DATABASE_NAME + "; " + sql);
    }

    private static String queryDatabaseScalar(MySQLContainer<?> mysql,
                                              String sql) throws Exception {
        return runMysqlQuery(mysql, "USE " + DATABASE_NAME + "; " + sql)
                .trim();
    }

    private static String queryScalar(MySQLContainer<?> mysql, String sql)
            throws Exception {
        return runMysqlQuery(mysql, sql).trim();
    }

    private static String runMysqlQuery(MySQLContainer<?> mysql, String sql)
            throws Exception {
        Container.ExecResult result = mysql.execInContainer(
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

    private static Container.ExecResult executeScript(
            MySQLContainer<?> mysql,
            Path hostPath,
            String containerPath,
            String clientCharset) throws Exception {
        mysql.copyFileToContainer(MountableFile.forHostPath(hostPath),
                containerPath);
        Container.ExecResult result = mysql.execInContainer(
                "sh",
                "-c",
                "MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" "
                        + "mysql --default-character-set=" + clientCharset
                        + " -uroot --batch --skip-column-names < "
                        + containerPath
        );
        assertSuccessfulMysql(result, "Database script failed: " + hostPath);
        return result;
    }

    private static Path requireScript(String relativePath) {
        Path scriptPath = Path.of(relativePath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(scriptPath)) {
            throw new IllegalStateException("Database script not found: "
                    + scriptPath);
        }
        return scriptPath;
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static void assertSuccessfulMysql(Container.ExecResult result,
                                              String message) {
        assertEquals(0, result.getExitCode(),
                message + System.lineSeparator() + result.getStderr());
        assertFalse(result.getStderr().contains("ERROR"), message);
    }

    private enum MigrationStatus {
        COMMITTED,
        ROLLED_BACK
    }
}
