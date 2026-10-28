package com.game3cay.server.bus;

import static org.junit.jupiter.api.Assertions.*;

import com.game3cay.server.dal.AdminBootstrap;
import com.game3cay.server.dal.ConnectionProvider;
import com.game3cay.server.game.DefaultGameEngine;
import com.game3cay.server.game.CompletedRound;
import com.game3cay.server.game.RoundParticipant;
import com.game3cay.shared.model.BetAction;
import com.game3cay.shared.security.PasswordUtil;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/** Opt-in real MySQL test. Only creates/drops its own randomly named database. */
@EnabledIfEnvironmentVariable(named="BACAY_TEST_MYSQL_URL", matches=".+")
class MySqlSchemaTest {
    @TempDir Path temporary;

    @Test void productionScriptCreatesFreshSchemaWithoutSharedAdminCredential() throws Exception {
        String url = System.getenv("BACAY_TEST_MYSQL_URL");
        String user = System.getenv().getOrDefault("BACAY_TEST_MYSQL_USER", "root");
        String password = System.getenv().getOrDefault("BACAY_TEST_MYSQL_PASSWORD", "");
        String database = "bacay_ci_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = DriverManager.getConnection(url, user, password)) {
            try (var statement = admin.createStatement()) { statement.execute("CREATE DATABASE " + database + " CHARACTER SET utf8mb4"); }
            ConnectionProvider connections = () -> {
                Connection connection = DriverManager.getConnection(url, user, password);
                connection.setCatalog(database);
                return connection;
            };
            try {
                String sql = Files.readString(Path.of("../database/setup.sql"));
                try (var connection = connections.open()) {
                    executeScript(connection, sql);
                    executeScript(connection, sql);
                    try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM users")) {
                        assertTrue(rows.next());
                        assertEquals(0, rows.getLong(1));
                    }
                }
                new JdbcGameBUS(connections).checkSchema();
                assertTrue(AdminBootstrap.initialize(connections, temporary.resolve("fresh.properties"), "fresh-installation-secret-12345"));
                assertEquals("ADMIN", new JdbcUserBUS(connections).authenticate("admin", "fresh-installation-secret-12345").role());
            } finally {
                try (var statement = admin.createStatement()) { statement.execute("DROP DATABASE " + database); }
            }
        }
    }

    @Test void productionScriptUpgradesPreservesDataAndSupportsTransactions() throws Exception {
        String url = System.getenv("BACAY_TEST_MYSQL_URL");
        String user = System.getenv().getOrDefault("BACAY_TEST_MYSQL_USER", "root");
        String password = System.getenv().getOrDefault("BACAY_TEST_MYSQL_PASSWORD", "");
        String database = "bacay_ci_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = DriverManager.getConnection(url, user, password)) {
            try (var statement = admin.createStatement()) { statement.execute("CREATE DATABASE " + database + " CHARACTER SET utf8mb4"); }
            ConnectionProvider connections = () -> {
                Connection connection = DriverManager.getConnection(url, user, password);
                connection.setCatalog(database);
                return connection;
            };
            try {
                String sql = Files.readString(Path.of("../database/setup.sql"));
                String legacyUsers = sql.substring(sql.indexOf("CREATE TABLE IF NOT EXISTS users"),
                        sql.indexOf("CREATE TABLE IF NOT EXISTS app_migrations"));
                legacyUsers = legacyUsers.replaceAll("(?m)^.*\\brole\\b.*\\R", "");
                try (var connection = connections.open(); var statement = connection.createStatement()) {
                    statement.execute(legacyUsers.trim().replaceFirst(";\\s*$", ""));
                    try (var insert = connection.prepareStatement("INSERT INTO users(username,password_hash,display_name,balance) VALUES('legacy',?,'Legacy',1000)")) {
                        insert.setString(1, PasswordUtil.hash("legacy-password-123"));
                        insert.executeUpdate();
                    }
                    executeScript(connection, sql);
                    executeScript(connection, sql);
                }
                JdbcGameBUS games = new JdbcGameBUS(connections);
                games.checkSchema();
                JdbcUserBUS users = new JdbcUserBUS(connections);
                long legacy = users.authenticate("legacy", "legacy-password-123").userId();
                assertTrue(AdminBootstrap.initialize(connections, temporary.resolve("admin.properties"), "installation-secret-12345"));
                long adminId = users.authenticate("admin", "installation-secret-12345").userId();
                long other = users.register("other_player", "password123", "Other", null).userId();
                var engine = new DefaultGameEngine();
                String round = UUID.randomUUID().toString();
                engine.initRound(round, 1, List.of(
                        new RoundParticipant(legacy, "legacy", "Legacy", new BigDecimal("1000.00")),
                        new RoundParticipant(other, "other_player", "Other", new BigDecimal("1000.00"))), BigDecimal.TEN);
                var hands = List.of(new CompletedRound.PrivateHand(legacy, engine.getPlayerCards(round, legacy)),
                        new CompletedRound.PrivateHand(other, engine.getPlayerCards(round, other)));
                engine.handleAction(round, engine.getPublicSnapshot(round).currentTurnUserId(), BetAction.FOLD, null);
                var completed = new CompletedRound(engine.getRoundResult(round), 1, "Test", BigDecimal.TEN, hands);
                games.saveCompleted(completed);
                games.saveCompleted(completed);
                assertEquals(1, users.getProfile(legacy).totalGames());
                users.adjustBalanceForAdmin(adminId, legacy, BigDecimal.ONE, "MySQL integration");
                assertEquals(3, users.getLedgerForAdmin(0, 20).totalItems());
                assertEquals(2, games.getLeaderboard(0, 20).totalItems());
            } finally {
                try (var statement = admin.createStatement()) { statement.execute("DROP DATABASE " + database); }
            }
        }
    }

    private static void executeScript(Connection connection, String sql) throws Exception {
        String delimiter = ";";
        StringBuilder command = new StringBuilder();
        for (String line : sql.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--") || trimmed.isBlank()) continue;
            if (trimmed.startsWith("CREATE DATABASE") || trimmed.equals("USE bacay_online;")) continue;
            if (trimmed.startsWith("DELIMITER ")) { delimiter = trimmed.substring(10).trim(); continue; }
            command.append(line).append('\n');
            if (trimmed.endsWith(delimiter)) {
                String statement = command.toString().stripTrailing();
                statement = statement.substring(0, statement.length() - delimiter.length());
                try (var query = connection.createStatement()) { query.execute(statement); }
                command.setLength(0);
            }
        }
        if (!command.toString().isBlank()) throw new IllegalArgumentException("SQL script has an unterminated statement");
    }
}
