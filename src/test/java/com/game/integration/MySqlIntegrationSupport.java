package com.game.integration;

import org.testcontainers.containers.Container;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

abstract class MySqlIntegrationSupport {

    private static final String DATABASE_NAME = "game_exchange";
    private static final String DATABASE_USERNAME = "gameexchange_it";
    private static final String DATABASE_PASSWORD = "it-" + UUID.randomUUID();
    private static final String ROOT_PASSWORD = "root-it-" + UUID.randomUUID();
    private static final DockerImageName MYSQL_IMAGE = DockerImageName
            .parse("mysql:8.4@sha256:8dbcf531a03aade657e181b9cf2f1d1803ce621a1d55610cb44cb531ab7d7db6")
            .asCompatibleSubstituteFor("mysql");

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MYSQL_IMAGE)
            .withDatabaseName(DATABASE_NAME)
            .withUsername(DATABASE_USERNAME)
            .withPassword(DATABASE_PASSWORD)
            .withEnv("MYSQL_ROOT_PASSWORD", ROOT_PASSWORD)
            .withLabel("com.gameexchange.test-scope", "integration")
            .withReuse(false);

    private static boolean initialized;

    static synchronized void startMySql() throws Exception {
        if (initialized) {
            return;
        }

        Path schemaPath = requireScript("database/schema.sql");
        Path seedPath = requireScript("database/seed.sql");

        MYSQL.start();
        try {
            executeScript(schemaPath, "/tmp/gameexchange-schema.sql");
            executeScript(seedPath, "/tmp/gameexchange-seed.sql");
            configureDbUtilProperties();
            initialized = true;
        } catch (Exception e) {
            MYSQL.stop();
            throw e;
        }
    }

    static MySQLContainer<?> mysqlContainer() {
        return MYSQL;
    }

    private static Path requireScript(String relativePath) {
        Path scriptPath = Path.of(relativePath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(scriptPath)) {
            throw new IllegalStateException("Database script not found: " + scriptPath);
        }
        return scriptPath;
    }

    private static void executeScript(Path hostPath, String containerPath) throws Exception {
        MYSQL.copyFileToContainer(MountableFile.forHostPath(hostPath), containerPath);
        Container.ExecResult result = MYSQL.execInContainer(
                "sh",
                "-c",
                "MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" "
                        + "mysql --default-character-set=utf8mb4 -uroot < " + containerPath
        );
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("Database script failed: " + hostPath
                    + System.lineSeparator() + result.getStderr());
        }
    }

    private static void configureDbUtilProperties() {
        String jdbcUrl = MYSQL.getJdbcUrl();
        String separator = jdbcUrl.contains("?") ? "&" : "?";
        jdbcUrl += separator
                + "useSSL=false"
                + "&serverTimezone=Asia/Shanghai"
                + "&characterEncoding=UTF-8"
                + "&connectionCollation=utf8mb4_0900_ai_ci"
                + "&allowPublicKeyRetrieval=true";

        System.setProperty("db.url", jdbcUrl);
        System.setProperty("db.username", MYSQL.getUsername());
        System.setProperty("db.password", MYSQL.getPassword());
        System.setProperty("db.pool.initialSize", "0");
        System.setProperty("db.pool.maxActive", "4");
        System.setProperty("db.pool.minIdle", "0");
        System.setProperty("db.pool.maxWait", "5000");
    }
}
