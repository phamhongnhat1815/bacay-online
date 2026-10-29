package com.game3cay.server.dal;

import com.game3cay.server.bus.BUSException;
import com.game3cay.server.bus.UserDTO;
import com.game3cay.shared.model.AdminLedgerItem;
import com.game3cay.shared.model.AdminStatsSnapshot;
import com.game3cay.shared.model.AdminUserItem;
import com.game3cay.shared.model.PageResult;
import com.game3cay.shared.network.ErrorCode;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class UserDAO {
    public record Account(UserDTO profile, String passwordHash) {}

    private final ConnectionProvider connections;

    public UserDAO(ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections, "connections không được null");
    }

    public Account findByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = connections.open();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? readAccount(rs) : null;
            }
        }
    }

    public Account findById(long id) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = connections.open();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? readAccount(rs) : null;
            }
        }
    }

    public boolean existsByUsername(String username) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (Connection conn = connections.open();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    public UserDTO insert(String username, String hash, String name, String email) throws SQLException {
        String sql = "INSERT INTO users (username, password_hash, display_name, email, balance) "
                + "VALUES (?, ?, ?, ?, 1000.00)";

        try (Connection conn = connections.open();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, username);
            stmt.setString(2, hash);
            stmt.setString(3, name);
            stmt.setString(4, email);
            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Không nhận được khóa sinh tự động cho tài khoản mới");
                }
                long generatedId = keys.getLong(1);
                return new UserDTO(
                        generatedId,
                        username,
                        name,
                        email,
                        null,
                        new BigDecimal("1000.00"),
                        "ACTIVE",
                        "USER",
                        0,
                        0,
                        0
                );
            }
        }
    }

    public UserDTO create(String username, String hash, String name, String email) throws SQLException {
        return insert(username, hash, name, email);
    }

    public void updateProfile(long id, String name, String email, String avatar) throws SQLException {
        String sql = "UPDATE users SET "
                + "display_name = COALESCE(?, display_name), "
                + "email = CASE WHEN ? IS NULL THEN email ELSE NULLIF(?, '') END, "
                + "avatar_url = COALESCE(?, avatar_url) "
                + "WHERE id = ?";

        try (Connection conn = connections.open();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setString(2, email);
            stmt.setString(3, email);
            stmt.setString(4, avatar);
            stmt.setLong(5, id);

            int affectedRows = stmt.executeUpdate();
            if (affectedRows != 1) {
                throw new SQLException("Không tìm thấy tài khoản với id: " + id);
            }
        }
    }

    public boolean changePassword(long id, String expected, String replacement) throws SQLException {
        String sql = "UPDATE users SET password_hash = ? WHERE id = ? AND password_hash = ?";

        try (Connection conn = connections.open();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, replacement);
            stmt.setLong(2, id);
            stmt.setString(3, expected);

            return stmt.executeUpdate() == 1;
        }
    }

    public PageResult<AdminUserItem> findUsersForAdmin(String query, String statusFilter, int page, int pageSize)
            throws SQLException {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (query != null && !query.isBlank()) {
            where.append(" AND (username LIKE ? OR display_name LIKE ? OR email LIKE ?) ");
            String pattern = "%" + query.trim() + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }

        if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter)) {
            where.append(" AND status = ? ");
            params.add(statusFilter.trim().toUpperCase());
        }

        String countSql = "SELECT COUNT(*) FROM users " + where;
        int totalItems = 0;
        try (Connection conn = connections.open();
             PreparedStatement stmt = conn.prepareStatement(countSql)) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    totalItems = rs.getInt(1);
                }
            }
        }

        int totalPages = (totalItems == 0) ? 0 : (totalItems + pageSize - 1) / pageSize;
        if (totalItems == 0) {
            return new PageResult<>(List.of(), page, pageSize, 0, 0);
        }

        String dataSql = "SELECT id, username, display_name, email, balance, status, role, "
                + "total_games, total_wins, total_losses, created_at FROM users "
                + where + " ORDER BY id DESC LIMIT ? OFFSET ?";

        List<AdminUserItem> items = new ArrayList<>();
        try (Connection conn = connections.open();
             PreparedStatement stmt = conn.prepareStatement(dataSql)) {
            int idx = 1;
            for (Object param : params) {
                stmt.setObject(idx++, param);
            }
            stmt.setInt(idx++, pageSize);
            stmt.setInt(idx, page * pageSize);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    items.add(new AdminUserItem(
                            rs.getLong("id"),
                            rs.getString("username"),
                            rs.getString("display_name"),
                            rs.getString("email"),
                            rs.getBigDecimal("balance"),
                            rs.getString("status"),
                            rs.getString("role"),
                            rs.getInt("total_games"),
                            rs.getInt("total_wins"),
                            rs.getInt("total_losses"),
                            rs.getTimestamp("created_at").getTime()
                    ));
                }
            }
        }

        return new PageResult<>(items, page, pageSize, totalItems, totalPages);
    }

    public boolean updateUserStatus(long userId, String status) throws SQLException {
        String sql = "UPDATE users SET status = ? WHERE id = ?";
        try (Connection conn = connections.open();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setLong(2, userId);
            return stmt.executeUpdate() == 1;
        }
    }

    public void adjustUserBalance(long adminId, long targetUserId, BigDecimal delta, String reason)
            throws SQLException, BUSException {
        try (Connection conn = connections.open()) {
            conn.setAutoCommit(false);
            try {
                BigDecimal currentBalance;
                try (PreparedStatement lockStmt = conn.prepareStatement(
                        "SELECT balance FROM users WHERE id = ? FOR UPDATE")) {
                    lockStmt.setLong(1, targetUserId);
                    try (ResultSet rs = lockStmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new BUSException(ErrorCode.INVALID_REQUEST, "Không tìm thấy người dùng");
                        }
                        currentBalance = rs.getBigDecimal("balance");
                    }
                }

                BigDecimal newBalance = currentBalance.add(delta);
                if (newBalance.signum() < 0) {
                    throw new BUSException(ErrorCode.INVALID_BET_AMOUNT, "Số dư sau điều chỉnh không thể âm");
                }

                try (PreparedStatement updateStmt = conn.prepareStatement(
                        "UPDATE users SET balance = ? WHERE id = ?")) {
                    updateStmt.setBigDecimal(1, newBalance);
                    updateStmt.setLong(2, targetUserId);
                    updateStmt.executeUpdate();
                }

                String adjustmentId = "ADJ-" + UUID.randomUUID();
                try (PreparedStatement insertStmt = conn.prepareStatement(
                        "INSERT INTO admin_adjustments (adjustment_id, admin_user_id, target_user_id, delta, balance_before, balance_after, reason) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    insertStmt.setString(1, adjustmentId);
                    insertStmt.setLong(2, adminId);
                    insertStmt.setLong(3, targetUserId);
                    insertStmt.setBigDecimal(4, delta);
                    insertStmt.setBigDecimal(5, currentBalance);
                    insertStmt.setBigDecimal(6, newBalance);
                    insertStmt.setString(7, reason != null ? reason : "Admin điều chỉnh điểm");
                    insertStmt.executeUpdate();
                }

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                if (e instanceof BUSException be) {
                    throw be;
                }
                throw e;
            }
        }
    }

    public AdminStatsSnapshot getAdminStats(long onlineUsers, int activeRooms) throws SQLException {
        long totalUsers = 0;
        BigDecimal totalBalance = BigDecimal.ZERO;
        int roundsToday = 0;

        try (Connection conn = connections.open()) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*), COALESCE(SUM(balance), 0) FROM users")) {
                if (rs.next()) {
                    totalUsers = rs.getLong(1);
                    totalBalance = rs.getBigDecimal(2);
                }
            }

            long startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM play_rounds WHERE ended_at >= ?")) {
                stmt.setLong(1, startOfDay);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        roundsToday = rs.getInt(1);
                    }
                }
            }
        }

        return new AdminStatsSnapshot(onlineUsers, activeRooms, totalUsers, totalBalance, roundsToday);
    }

    public PageResult<AdminLedgerItem> getLedgerEntries(int page, int pageSize) throws SQLException {
        String combined = "SELECT l.round_id, l.user_id, u.username, l.delta, l.balance_before, l.balance_after, "
                + "r.ended_at AS ts, 'GAME' AS transaction_type, CAST(NULL AS CHAR) AS admin_username, "
                + "CAST(NULL AS CHAR) AS reason FROM play_ledger l "
                + "JOIN users u ON l.user_id=u.id JOIN play_rounds r ON l.round_id=r.round_id "
                + "UNION ALL SELECT a.adjustment_id, a.target_user_id, u.username, a.delta, a.balance_before, "
                + "a.balance_after, CAST(UNIX_TIMESTAMP(a.created_at) AS DECIMAL(20,0))*1000 AS ts, 'ADMIN', au.username, a.reason "
                + "FROM admin_adjustments a JOIN users u ON a.target_user_id=u.id "
                + "LEFT JOIN users au ON a.admin_user_id=au.id "
                + "UNION ALL SELECT t.transfer_id,e.user_id,u.username,e.delta,e.balance_before,e.balance_after, "
                + "t.committed_at AS ts,CASE WHEN e.delta<0 THEN 'GIFT_SENT' ELSE 'GIFT_RECEIVED' END,actor.username, "
                + "CONCAT(CASE WHEN e.delta<0 THEN 'Cho ' ELSE 'Nhận từ ' END, "
                + "CASE WHEN e.delta<0 THEN t.recipient_name ELSE t.sender_name END,': ',t.message) "
                + "FROM point_transfer_entries e JOIN point_transfers t ON e.transfer_id=t.transfer_id "
                + "JOIN users u ON e.user_id=u.id JOIN users actor ON t.sender_id=actor.id";
        try (Connection conn = connections.open()) {
            long totalItems;
            try (Statement stmt = conn.createStatement();
                 ResultSet rows = stmt.executeQuery("SELECT COUNT(*) FROM (" + combined + ") entries")) {
                rows.next();
                totalItems = rows.getLong(1);
            }
            List<AdminLedgerItem> items = new ArrayList<>();
            String sql = "SELECT * FROM (" + combined + ") entries "
                    + "ORDER BY ts DESC, transaction_type, round_id, user_id LIMIT ? OFFSET ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, pageSize);
                stmt.setLong(2, (long) page * pageSize);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        items.add(new AdminLedgerItem(rs.getString("round_id"), rs.getLong("user_id"),
                                rs.getString("username"), rs.getBigDecimal("delta"),
                                rs.getBigDecimal("balance_before"), rs.getBigDecimal("balance_after"),
                                rs.getLong("ts"), rs.getString("transaction_type"),
                                rs.getString("admin_username"), rs.getString("reason")));
                    }
                }
            }
            return new PageResult<>(List.copyOf(items), page, pageSize, totalItems,
                    PageResult.calcTotalPages(totalItems, pageSize));
        }
    }

    private static Account readAccount(ResultSet rs) throws SQLException {
        String role = "USER";
        try {
            String r = rs.getString("role");
            if (r != null && !r.isBlank()) {
                role = r;
            }
        } catch (SQLException ignored) {
        }

        UserDTO dto = new UserDTO(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("email"),
                rs.getString("avatar_url"),
                rs.getBigDecimal("balance"),
                rs.getString("status"),
                role,
                rs.getInt("total_games"),
                rs.getInt("total_wins"),
                rs.getInt("total_losses")
        );
        return new Account(dto, rs.getString("password_hash"));
    }
}
