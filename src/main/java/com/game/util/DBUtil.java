package com.game.util;

import com.alibaba.druid.pool.DruidDataSourceFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DBUtil {

    private static final Logger LOGGER = Logger.getLogger(DBUtil.class.getName());
    private static final String CONFIG_RESOURCE = "db.properties";
    private static final DataSource dataSource;

    static {
        try {
            Properties properties = loadProperties();
            applyOverrides(properties);
            validateRequiredProperties(properties);
            dataSource = DruidDataSourceFactory.createDataSource(properties);
            LOGGER.info("数据库连接池初始化成功");
        } catch (Exception e) {
            throw new RuntimeException("连接池初始化失败", e);
        }
    }

    private DBUtil() {
    }

    private static Properties loadProperties() throws IOException {
        Properties properties = new Properties();
        try (InputStream inputStream = DBUtil.class.getClassLoader()
                .getResourceAsStream(CONFIG_RESOURCE)) {
            if (inputStream == null) {
                throw new IOException("未找到数据库配置文件：" + CONFIG_RESOURCE);
            }
            properties.load(inputStream);
        }
        return properties;
    }

    private static void applyOverrides(Properties properties) {
        applyOverride(properties, "driverClassName",
                "db.driverClassName", "DB_DRIVER_CLASS_NAME");
        applyOverride(properties, "url", "db.url", "DB_URL");
        applyOverride(properties, "username", "db.username", "DB_USERNAME");
        applyOverride(properties, "password", "db.password", "DB_PASSWORD");
        applyOverride(properties, "initialSize",
                "db.pool.initialSize", "DB_POOL_INITIAL_SIZE");
        applyOverride(properties, "maxActive",
                "db.pool.maxActive", "DB_POOL_MAX_ACTIVE");
        applyOverride(properties, "minIdle",
                "db.pool.minIdle", "DB_POOL_MIN_IDLE");
        applyOverride(properties, "maxWait",
                "db.pool.maxWait", "DB_POOL_MAX_WAIT");
    }

    private static void applyOverride(Properties properties,
                                      String propertyName,
                                      String systemPropertyName,
                                      String environmentName) {
        String value = System.getProperty(systemPropertyName);
        if (value == null || value.isBlank()) {
            value = System.getenv(environmentName);
        }
        if (value != null && !value.isBlank()) {
            properties.setProperty(propertyName, value);
        }
    }

    private static void validateRequiredProperties(Properties properties) {
        validateRequiredProperty(properties, "driverClassName",
                "db.driverClassName", "DB_DRIVER_CLASS_NAME");
        validateRequiredProperty(properties, "url", "db.url", "DB_URL");
        validateRequiredProperty(properties, "username",
                "db.username", "DB_USERNAME");
        validateRequiredProperty(properties, "password",
                "db.password", "DB_PASSWORD");
    }

    private static void validateRequiredProperty(Properties properties,
                                                 String propertyName,
                                                 String systemPropertyName,
                                                 String environmentName) {
        String value = properties.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少数据库配置 " + propertyName
                    + "，请设置系统属性 " + systemPropertyName
                    + " 或环境变量 " + environmentName);
        }
    }

    public static Connection getConnection() throws Exception {
        return dataSource.getConnection();
    }

    public static void close(Connection conn) {
        if (conn != null) {
            try {
                conn.setAutoCommit(true);
                conn.close();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "关闭数据库连接失败", e);
            }
        }
    }

    public static DataSource getDataSource() {
        return dataSource;
    }
}
