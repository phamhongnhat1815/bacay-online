package com.game3cay.server.bus;

import static org.junit.jupiter.api.Assertions.*;

import com.game3cay.server.dal.AdminBootstrap;
import com.game3cay.server.dal.ConnectionProvider;
import com.game3cay.server.dal.DatabaseConfig;
import com.game3cay.shared.security.PasswordUtil;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataRegressionTest {
    ConnectionProvider connections;
    @TempDir Path temporary;

    @BeforeEach void database() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        connections = () -> DriverManager.getConnection(url);
        try (var c = connections.open(); var s = c.createStatement()) {
            s.execute("CREATE TABLE users (id BIGINT AUTO_INCREMENT PRIMARY KEY,username VARCHAR(50) UNIQUE NOT NULL,"
                    + "password_hash VARCHAR(255) NOT NULL,display_name VARCHAR(100) NOT NULL,email VARCHAR(100),"
                    + "avatar_url VARCHAR(255),balance DECIMAL(15,2) NOT NULL DEFAULT 1000,status VARCHAR(20) DEFAULT 'ACTIVE',"
                    + "role VARCHAR(20) DEFAULT 'USER',total_games INT DEFAULT 0,total_wins INT DEFAULT 0,"
                    + "total_losses INT DEFAULT 0,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            s.execute("CREATE TABLE admin_adjustments(id BIGINT AUTO_INCREMENT PRIMARY KEY,adjustment_id VARCHAR(64) UNIQUE,"
                    + "admin_user_id BIGINT,target_user_id BIGINT,delta DECIMAL(15,2),balance_before DECIMAL(15,2),"
                    + "balance_after DECIMAL(15,2),reason VARCHAR(255),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            s.execute("CREATE TABLE play_rounds(round_id VARCHAR(64) PRIMARY KEY,ended_at BIGINT)");
            s.execute("CREATE TABLE play_ledger(round_id VARCHAR(64),user_id BIGINT,delta DECIMAL(15,2),"
                    + "balance_before DECIMAL(15,2),balance_after DECIMAL(15,2))");
            PointTestSchema.create(c);
        }
    }

    @Test void freshBootstrapUsesUniqueSecretAndNeverOverwritesCustomizedPassword() throws Exception {
        Path credential = temporary.resolve("admin-bootstrap.properties");
        assertTrue(AdminBootstrap.initialize(connections, credential, "installation-secret-12345"));
        JdbcUserBUS users = new JdbcUserBUS(connections);
        assertEquals("ADMIN", users.authenticate("admin", "installation-secret-12345").role());
        assertTrue(Files.readString(credential).contains("username=admin"));
        assertFalse(AdminBootstrap.initialize(connections, credential, "another-installation-secret"));
        assertThrows(BUSException.class, () -> users.changePassword(
                users.authenticate("admin", "installation-secret-12345").userId(), "installation-secret-12345", "admin@123"));
        users.changePassword(users.authenticate("admin", "installation-secret-12345").userId(),
                "installation-secret-12345", "customized-password-12345");
        assertFalse(AdminBootstrap.initialize(connections, credential, null));
        assertNotNull(users.authenticate("admin", "customized-password-12345"));
    }

    @Test void legacyPublicAdminPasswordIsRotatedAndRefusedByAuthentication() throws Exception {
        try (var c = connections.open(); var insert = c.prepareStatement(
                "INSERT INTO users(username,password_hash,display_name,balance,role) VALUES('admin',?,'Admin',0,'ADMIN')")) {
            insert.setString(1, PasswordUtil.hash("admin@123"));
            insert.executeUpdate();
        }
        JdbcUserBUS users = new JdbcUserBUS(connections);
        assertThrows(BUSException.class, () -> users.authenticate("admin", "admin@123"));
        Path credential = temporary.resolve("rotated.properties");
        assertTrue(AdminBootstrap.initialize(connections, credential, null));
        var saved = new java.util.Properties();
        try (var reader = Files.newBufferedReader(credential)) { saved.load(reader); }
        String replacement = saved.getProperty("password");
        assertTrue(replacement.length() >= 32);
        assertNotEquals("admin@123", replacement);
        assertNotNull(users.authenticate("admin", replacement));
    }

    @Test void ledgerContainsAdjustmentActorReasonAndGamesWithStablePagination() throws Exception {
        JdbcUserBUS users = new JdbcUserBUS(connections);
        long player = users.register("player1", "password123", "Player", null).userId();
        AdminBootstrap.initialize(connections, temporary.resolve("admin.properties"), "installation-secret-12345");
        long admin = users.authenticate("admin", "installation-secret-12345").userId();
        users.adjustBalanceForAdmin(admin, player, new BigDecimal("25.00"), "Thưởng kiểm thử");
        var adjustment = users.getLedgerForAdmin(0, 1);
        assertEquals(1, adjustment.totalItems());
        var item = adjustment.items().getFirst();
        assertEquals("ADMIN", item.transactionType());
        assertEquals("admin", item.adminUsername());
        assertEquals("Thưởng kiểm thử", item.reason());
        assertTrue(item.roundId().startsWith("ADJ-"));
        try (var c = connections.open(); var s = c.createStatement()) {
            s.execute("INSERT INTO play_rounds VALUES('round1',1)");
            s.execute("INSERT INTO play_ledger VALUES('round1'," + player + ",10,1025,1035)");
        }
        assertEquals(2, users.getLedgerForAdmin(0, 1).totalItems());
        assertEquals("GAME", users.getLedgerForAdmin(1, 1).items().getFirst().transactionType());
        assertThrows(BUSException.class, () -> users.adjustBalanceForAdmin(admin, player, BigDecimal.ONE, " "));
        assertThrows(BUSException.class, () -> users.findUsersForAdmin("", "ALL", 0, 0));
    }

    @Test void schemaValidationFailsBeforeGameplayWhenRequiredTablesAreMissing() {
        assertThrows(SQLException.class, () -> new JdbcGameBUS(connections).checkSchema());
    }

    @Test void jdbcClosesPhysicalConnectionsRollsBackAndBoundsWaitTime() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (var provider = DatabaseConfig.bounded(url, "sa", "", 1, 250)) {
            java.sql.Connection physical;
            try (var first = provider.open()) {
                physical = first.unwrap(org.h2.jdbc.JdbcConnection.class);
                try (var statement = first.createStatement()) { statement.execute("CREATE TABLE x(id INT)"); }
                first.setAutoCommit(false);
                try (var statement = first.createStatement()) { statement.execute("INSERT INTO x VALUES(1)"); }
                assertThrows(java.sql.SQLTimeoutException.class, provider::open);
            }
            assertTrue(physical.isClosed());
            try (var second = provider.open(); var statement = second.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM x")) {
                assertNotSame(physical, second.unwrap(org.h2.jdbc.JdbcConnection.class));
                assertTrue(second.getAutoCommit());
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1));
            }
        }
    }

    @Test void jdbcDoubleCloseCannotExceedLimitAndShutdownClosesActiveConnections() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        var provider = DatabaseConfig.bounded(url, "sa", "", 1, 25);
        try {
            var first = provider.open();
            assertSame(first, first.unwrap(java.sql.Connection.class));
            first.close();
            first.close();
            try (var second = provider.open()) {
                assertThrows(java.sql.SQLTimeoutException.class, provider::open);
                provider.close();
                assertTrue(second.isClosed());
                assertThrows(SQLException.class, provider::open);
            }
        } finally { provider.close(); }
    }

    @Test void jdbcDriverFailureReturnsPermitForNextAttempt() throws Exception {
        try (var provider = DatabaseConfig.bounded("jdbc:missing-driver:" + UUID.randomUUID(), "sa", "", 1, 25)) {
            assertFalse(assertThrows(SQLException.class, provider::open) instanceof java.sql.SQLTimeoutException);
            assertFalse(assertThrows(SQLException.class, provider::open) instanceof java.sql.SQLTimeoutException);
        }
    }
}
