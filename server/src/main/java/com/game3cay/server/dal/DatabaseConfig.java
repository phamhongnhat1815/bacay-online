package com.game3cay.server.dal;

import java.sql.DriverManager;
import java.util.Properties;

/** Cấu hình runtime qua biến môi trường; không ghi thông tin kết nối vào log. */
public final class DatabaseConfig {
    private DatabaseConfig() {}

    public static ConnectionProvider fromEnvironment() {
        String url = System.getenv().getOrDefault("BACAY_DB_URL",
                "jdbc:mysql://localhost:3306/bacay_online?connectTimeout=5000&socketTimeout=10000&connectionTimeZone=UTC");
        if (!url.startsWith("jdbc:mysql:")) {
            throw new IllegalArgumentException("BACAY_DB_URL phải là URL MySQL JDBC");
        }
        String user = System.getenv("BACAY_DB_USER");
        String password = System.getenv("BACAY_DB_PASSWORD");
        if (user == null || user.isBlank() || password == null) {
            throw new IllegalStateException("Cần cấu hình BACAY_DB_USER và BACAY_DB_PASSWORD");
        }
        Properties properties = new Properties();
        properties.setProperty("user", user);
        properties.setProperty("password", password);
        return () -> DriverManager.getConnection(url, properties);
    }
}
