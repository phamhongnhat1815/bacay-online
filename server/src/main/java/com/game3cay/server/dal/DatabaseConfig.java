package com.game3cay.server.dal;

import java.sql.DriverManager;
import java.util.Properties;

/**
 * Cấu hình kết nối MySQL runtime qua biến môi trường.
 * <p>
 * Tuyệt đối không ghi thông tin mật khẩu hoặc chuỗi kết nối nhạy cảm ra log.
 */
public final class DatabaseConfig {

    private static final String DEFAULT_DB_URL =
            "jdbc:mysql://localhost:3306/bacay_online?connectTimeout=5000&socketTimeout=10000&connectionTimeZone=UTC";

    private DatabaseConfig() {}

    /**
     * Tạo một {@link ConnectionProvider} dựa trên các biến môi trường:
     * <ul>
     *   <li>{@code BACAY_DB_URL}: JDBC URL (mặc định trỏ về localhost:3306/bacay_online)</li>
     *   <li>{@code BACAY_DB_USER}: Tài khoản người dùng MySQL (bắt buộc)</li>
     *   <li>{@code BACAY_DB_PASSWORD}: Mật khẩu MySQL (mặc định rỗng)</li>
     * </ul>
     *
     * @return ConnectionProvider cấu hình theo môi trường
     */
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
