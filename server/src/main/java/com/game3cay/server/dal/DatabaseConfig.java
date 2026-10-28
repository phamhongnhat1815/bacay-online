package com.game3cay.server.dal;


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
        return bounded(url, user, password,
                environmentInteger("BACAY_DB_MAX_CONNECTIONS", 12, 1, 64),
                environmentInteger("BACAY_DB_ACQUIRE_TIMEOUT_MS", 5000, 250, 60000));
    }

    public static ConnectionProvider bounded(String url, String user, String password,
                                              int maximumConnections, long acquireTimeoutMs) {
        return new JdbcConnectionProvider(url, user, password, maximumConnections, acquireTimeoutMs);
    }

    private static int environmentInteger(String name, int fallback, int min, int max) {
        String configured = System.getenv(name);
        int value = configured == null || configured.isBlank() ? fallback : Integer.parseInt(configured);
        if (value < min || value > max) throw new IllegalArgumentException(name + " phải từ " + min + " đến " + max);
        return value;
    }
}
