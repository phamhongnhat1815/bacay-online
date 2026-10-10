package com.game3cay.server.bus;

import com.game3cay.server.dal.ConnectionProvider;
import com.game3cay.shared.network.ErrorCode;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class JdbcUserBUSTest {
    private ConnectionProvider connections;
    private JdbcUserBUS bus;

    @BeforeEach
    void database() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        connections = () -> DriverManager.getConnection(url);
        try (Connection c = connections.open(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE users (id BIGINT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(50) UNIQUE NOT NULL, password_hash VARCHAR(255) NOT NULL, display_name VARCHAR(100) NOT NULL, email VARCHAR(100), avatar_url VARCHAR(255), balance DECIMAL(15,2) DEFAULT 0 NOT NULL, status VARCHAR(20) DEFAULT 'ACTIVE', total_games INT DEFAULT 0, total_wins INT DEFAULT 0, total_losses INT DEFAULT 0)");
        }
        bus = new JdbcUserBUS(connections);
    }

    @Test
    void registerLoginAndHash() throws Exception {
        UserDTO user = bus.register("Nhat_01", "password123", "Nhật", null);
        assertEquals("nhat_01", user.username());
        assertEquals(user.userId(), bus.authenticate("NHAT_01", "password123").userId());
        assertEquals("Nhật", bus.getProfile(user.userId()).displayName());
        try (Connection c = connections.open(); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT password_hash FROM users")) {
            assertTrue(r.next());
            assertNotEquals("password123", r.getString(1));
            assertTrue(r.getString(1).startsWith("PBKDF2WithHmacSHA256$"));
        }
        assertCode(ErrorCode.INVALID_CREDENTIALS, () -> bus.authenticate("nhat_01", "wrong"));
        assertCode(ErrorCode.INVALID_CREDENTIALS, () -> bus.authenticate("missing", "password123"));
        assertCode(ErrorCode.USERNAME_EXISTS, () -> bus.register("NHAT_01", "password123", null, null));
    }

    @Test
    void blockedAndValidationAndUnimplemented() throws Exception {
        UserDTO user = bus.register("blocked", "password123", null, null);
        try (Connection c = connections.open(); Statement s = c.createStatement()) {
            s.executeUpdate("UPDATE users SET status='BLOCKED'");
        }
        assertCode(ErrorCode.USER_BLOCKED, () -> bus.authenticate("blocked", "password123"));
        assertCode(ErrorCode.INVALID_CREDENTIALS, () -> bus.authenticate("blocked", "wrong"));
        assertCode(ErrorCode.INVALID_REQUEST, () -> bus.register("a' OR 1=1", "password123", null, null));
        assertCode(ErrorCode.INVALID_REQUEST, () -> bus.register("valid", "short", null, null));
        assertCode(ErrorCode.NOT_IMPLEMENTED, () -> bus.changePassword(user.userId(), "password123", "nextpassword"));
    }

    @Test
    void simultaneousRegistrationHasOneWinner() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<ErrorCode> register = () -> {
            start.await();
            try { bus.register("same_name", "password123", null, null); return null; }
            catch (BUSException e) { return e.getErrorCode(); }
        };
        try {
            Future<ErrorCode> a = workers.submit(register);
            Future<ErrorCode> b = workers.submit(register);
            start.countDown();
            ErrorCode first = a.get(10, TimeUnit.SECONDS);
            ErrorCode second = b.get(10, TimeUnit.SECONDS);
            assertTrue((first == null && second == ErrorCode.USERNAME_EXISTS)
                    || (second == null && first == ErrorCode.USERNAME_EXISTS));
        } finally { workers.shutdownNow(); }
    }

    @Test
    void unavailableDatabaseReturnsControlledError() {
        JdbcUserBUS unavailable = new JdbcUserBUS(() -> { throw new SQLException("DB down"); });
        assertCode(ErrorCode.INTERNAL_ERROR, () -> unavailable.authenticate("valid", "password123"));
        assertCode(ErrorCode.INTERNAL_ERROR, () -> unavailable.register("valid", "password123", null, null));
    }

    private static void assertCode(ErrorCode code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(BUSException.class, action).getErrorCode());
    }
}
