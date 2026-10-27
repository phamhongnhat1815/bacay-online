package com.game3cay.server.dal;

import java.sql.DriverManager;
import java.util.Properties;

public final class DatabaseConfig {
    private static final String DEFAULT_DB_URL =
            "jdbc:mysql://localhost:3306/bacay_online?connectTimeout=5000&socketTimeout=10000&connectionTimeZone=UTC";

    private DatabaseConfig() {}

    public static ConnectionProvider fromEnvironment() {
        String url = System.getenv().getOrDefault("BACAY_DB_URL", DEFAULT_DB_URL);
        if (!url.startsWith("jdbc:mysql:")) {
            throw new IllegalArgumentException("BACAY_DB_URL phải là URL MySQL JDBC hợp lệ");
        }

        String user = System.getenv("BACAY_DB_USER");
        if (user == null || user.isBlank()) {
            user = "root";
        }

        String password = System.getenv().getOrDefault("BACAY_DB_PASSWORD", "");
        Properties properties = new Properties();
        properties.setProperty("user", user);
        properties.setProperty("password", password);

        return () -> DriverManager.getConnection(url, properties);
    }
}
