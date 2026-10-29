package com.game3cay.server.bus;

import com.game3cay.server.dal.ConnectionProvider;
import com.game3cay.server.game.CompletedRound;
import com.game3cay.server.game.HandEvaluator;
import com.game3cay.shared.model.Card;
import com.game3cay.shared.model.GameHistoryItem;
import com.game3cay.shared.model.Hand;
import com.game3cay.shared.model.LeaderboardItem;
import com.game3cay.shared.model.PageResult;
import com.game3cay.shared.model.PlayerResult;
import com.game3cay.shared.model.RoundResult;
import com.game3cay.shared.network.ErrorCode;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class JdbcGameBUS implements GameBUS {
    private final ConnectionProvider connections;
    private volatile Boolean roleColumn;
    private record CachedLeaderboard(PageResult<LeaderboardItem> result, long expiresAtNanos) {}
    private final java.util.concurrent.ConcurrentHashMap<String, CachedLeaderboard> leaderboardCache = new java.util.concurrent.ConcurrentHashMap<>();

    public JdbcGameBUS(ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections, "connections không được null");
    }

    public void checkSchema() throws SQLException {
        Map<String, String> required = new LinkedHashMap<>(Map.of(
                "users", "id,username,password_hash,display_name,email,avatar_url,balance,status,role,total_games,total_wins,total_losses,created_at",
                "play_rounds", "id,round_id,content_hash,runtime_room_id,room_name,ante,status,ended_at",
                "play_results", "round_id,user_id,display_name,placement,outcome,profit,hand_type,score",
                "play_cards", "round_id,user_id,position_no,rank_name,suit_name",
                "play_bets", "round_id,sequence_no,user_id,action,total_bet,added_amount,created_at",
                "play_pots", "round_id,pot_no,amount",
                "play_pot_players", "round_id,pot_no,user_id",
                "play_ledger", "round_id,user_id,delta,balance_before,balance_after",
                "admin_adjustments", "id,adjustment_id,admin_user_id,target_user_id,delta,balance_before,balance_after,reason,created_at"));
        required.put("point_requests", "request_id,operation_id,payload_hash,requester_id,requester_name,target_id,target_name,room_id,amount,message,status,created_at,expires_at,transfer_id");
        required.put("point_transfers", "transfer_id,operation_id,payload_hash,request_id,room_id,sender_id,sender_name,recipient_id,recipient_name,amount,message,committed_at");
        required.put("point_transfer_entries", "transfer_id,user_id,delta,balance_before,balance_after");
        try (Connection conn = connections.open()) {
            for (var table : required.entrySet()) {
                try (Statement stmt = conn.createStatement();
                     ResultSet ignored = stmt.executeQuery("SELECT " + table.getValue() + " FROM " + table.getKey() + " WHERE 1=0")) {}
            }
            try (Statement stmt = conn.createStatement();
                 ResultSet migration = stmt.executeQuery("SELECT name FROM app_migrations WHERE name='schema_v2_secure_admin'")) {
                if (!migration.next()) throw new SQLException("Schema chưa cập nhật; hãy chạy database/setup.sql trước khi mở server");
            }
            roleColumn = true;
            try (Statement stmt = conn.createStatement();
                 ResultSet migration = stmt.executeQuery("SELECT name FROM app_migrations WHERE name='schema_v3_point_transfers'")) {
                if (!migration.next()) throw new SQLException("Schema xin/cho điểm chưa cập nhật; hãy chạy database/setup.sql");
            }
        }
    }

    @Override
    public void saveCompleted(CompletedRound completed) throws BUSException {
        RoundResult result = completed.result();
        String contentHash = computeFingerprint(completed);

        if (!Set.of("FINISHED", "CANCELLED").contains(result.roundStatus())
                || result.playerResults().size() < 2
                || result.playerResults().stream()
                        .map(PlayerResult::profit)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .signum() != 0) {
            throw new BUSException(ErrorCode.INVALID_REQUEST, "Kết quả ván bài không hợp lệ");
        }

        try (Connection conn = connections.open()) {
            conn.setAutoCommit(false);
            try {
                Map<Long, BigDecimal> currentBalances = lockUsersInOrder(conn, result.playerResults());

                if (isAlreadySaved(conn, result, contentHash)) {
                    verifyDuplicateMatch(conn, result);
                    conn.rollback();
                    return;
                }

                insertPlayRound(conn, completed, contentHash);

                insertPlayerResultsAndCards(conn, completed, currentBalances);

                insertBetHistory(conn, result);

                insertSidePots(conn, result);

                conn.commit();
                leaderboardCache.clear();
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
                throw e;
            }
        } catch (Exception e) {
            throw new BUSException(ErrorCode.INTERNAL_ERROR, "Chưa lưu được kết quả ván bài; server sẽ tự động thử lại", e);
        }
    }

    private Map<Long, BigDecimal> lockUsersInOrder(Connection conn, List<PlayerResult> players) throws SQLException {
        Map<Long, BigDecimal> balances = new LinkedHashMap<>();
        List<PlayerResult> sortedPlayers = players.stream()
                .sorted(Comparator.comparingLong(PlayerResult::userId))
                .toList();

        String sql = "SELECT balance FROM users WHERE id = ? FOR UPDATE";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (PlayerResult p : sortedPlayers) {
                stmt.setLong(1, p.userId());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Không tìm thấy người chơi với ID: " + p.userId());
                    }
                    balances.put(p.userId(), rs.getBigDecimal(1));
                }
            }
        }
        return balances;
    }

    private boolean isAlreadySaved(Connection conn, RoundResult result, String contentHash) throws SQLException {
        String sql = "SELECT status, ended_at, content_hash FROM play_rounds WHERE round_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, result.roundId());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String existingStatus = rs.getString(1);
                    long existingEndedAt = rs.getLong(2);
                    String existingHash = rs.getString(3);

                    if (!contentHash.equals(existingHash)
                            || !result.roundStatus().equals(existingStatus)
                            || result.endedAtMillis() != existingEndedAt) {
                        throw new SQLException("roundId đã tồn tại nhưng nội dung kết quả không khớp");
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private void insertPlayRound(Connection conn, CompletedRound completed, String hash) throws SQLException {
        String sql = "INSERT INTO play_rounds "
                + "(round_id, content_hash, runtime_room_id, room_name, ante, status, ended_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        RoundResult r = completed.result();
        executeUpdate(conn, sql,
                r.roundId(),
                hash,
                completed.roomId(),
                completed.roomName(),
                completed.ante(),
                r.roundStatus(),
                r.endedAtMillis()
        );
    }

    private void insertPlayerResultsAndCards(Connection conn, CompletedRound completed, Map<Long, BigDecimal> balances)
            throws SQLException {
        RoundResult r = completed.result();
        HandEvaluator evaluator = new HandEvaluator();

        for (PlayerResult p : r.playerResults()) {
            CompletedRound.PrivateHand privateHand = completed.hands().stream()
                    .filter(h -> h.userId() == p.userId())
                    .findFirst()
                    .orElseThrow(() -> new SQLException("Thiếu thông tin bài của người chơi: " + p.userId()));

            Hand evaluatedHand = evaluator.evaluate(privateHand.cards());

            String insertResultSql = "INSERT INTO play_results "
                    + "(round_id, user_id, display_name, placement, outcome, profit, hand_type, score) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            executeUpdate(conn, insertResultSql,
                    r.roundId(),
                    p.userId(),
                    p.displayName(),
                    p.rank(),
                    p.result(),
                    p.profit(),
                    evaluatedHand.handType().name(),
                    evaluatedHand.score()
            );

            String insertCardSql = "INSERT INTO play_cards (round_id, user_id, position_no, rank_name, suit_name) "
                    + "VALUES (?, ?, ?, ?, ?)";
            for (int i = 0; i < privateHand.cards().size(); i++) {
                Card card = privateHand.cards().get(i);
                executeUpdate(conn, insertCardSql,
                        r.roundId(),
                        p.userId(),
                        i + 1,
                        card.rank().name(),
                        card.suit().name()
                );
            }

            if ("FINISHED".equals(r.roundStatus())) {
                BigDecimal balanceBefore = balances.get(p.userId());
                BigDecimal balanceAfter = balanceBefore.add(p.profit());
                if (balanceAfter.signum() < 0) {
                    throw new SQLException("Số dư người chơi không đủ để chốt ván: " + p.userId());
                }

                String insertLedgerSql = "INSERT INTO play_ledger "
                        + "(round_id, user_id, delta, balance_before, balance_after) "
                        + "VALUES (?, ?, ?, ?, ?)";
                executeUpdate(conn, insertLedgerSql,
                        r.roundId(),
                        p.userId(),
                        p.profit(),
                        balanceBefore,
                        balanceAfter
                );

                int winDelta = "WIN".equals(p.result()) ? 1 : 0;
                int lossDelta = Set.of("LOSE", "FOLD").contains(p.result()) ? 1 : 0;
                String updateUserSql = "UPDATE users SET "
                        + "balance = ?, "
                        + "total_games = total_games + 1, "
                        + "total_wins = total_wins + ?, "
                        + "total_losses = total_losses + ? "
                        + "WHERE id = ?";
                executeUpdate(conn, updateUserSql, balanceAfter, winDelta, lossDelta, p.userId());
            }
        }
    }

    private void insertBetHistory(Connection conn, RoundResult result) throws SQLException {
        String sql = "INSERT INTO play_bets "
                + "(round_id, sequence_no, user_id, action, total_bet, added_amount, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        for (var b : result.betHistory()) {
            executeUpdate(conn, sql,
                    result.roundId(),
                    b.sequenceNo(),
                    b.userId(),
                    b.action().name(),
                    b.raisedPoint(),
                    b.raiseAmount(),
                    b.createdAtMillis()
            );
        }
    }

    private void insertSidePots(Connection conn, RoundResult result) throws SQLException {
        String potSql = "INSERT INTO play_pots (round_id, pot_no, amount) VALUES (?, ?, ?)";
        String playerSql = "INSERT INTO play_pot_players (round_id, pot_no, user_id) VALUES (?, ?, ?)";

        for (int i = 0; i < result.sidePots().size(); i++) {
            var pot = result.sidePots().get(i);
            int potNo = i + 1;
            executeUpdate(conn, potSql, result.roundId(), potNo, pot.amount());

            for (long eligibleUserId : pot.eligiblePlayerIds()) {
                executeUpdate(conn, playerSql, result.roundId(), potNo, eligibleUserId);
            }
        }
    }

    private static String computeFingerprint(CompletedRound completed) throws BUSException {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
                out.writeObject(completed);
            }
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new BUSException(ErrorCode.INTERNAL_ERROR, "Không tạo được fingerprint định danh cho kết quả", e);
        }
    }

    private void verifyDuplicateMatch(Connection conn, RoundResult result) throws SQLException {
        String sql = "SELECT user_id, outcome, profit FROM play_results WHERE round_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, result.roundId());
            int matchedCount = 0;
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    matchedCount++;
                    long userId = rs.getLong(1);
                    String outcome = rs.getString(2);
                    BigDecimal profit = rs.getBigDecimal(3);

                    PlayerResult expected = result.playerResults().stream()
                            .filter(p -> p.userId() == userId)
                            .findFirst()
                            .orElseThrow(() -> new SQLException("Không tìm thấy người chơi trong kết quả trùng lặp: " + userId));

                    if (!expected.result().equals(outcome) || expected.profit().compareTo(profit) != 0) {
                        throw new SQLException("Kết quả ghi lặp không khớp với dữ liệu đã lưu trước đó");
                    }
                }
            }
            if (matchedCount != result.playerResults().size()) {
                throw new SQLException("Kết quả ghi lặp không đủ số lượng người chơi");
            }
        }
    }

    private static void executeUpdate(Connection conn, String sql, Object... args) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                stmt.setObject(i + 1, args[i]);
            }
            stmt.executeUpdate();
        }
    }

    private static void validatePagination(int page, int pageSize) throws BUSException {
        if (page < 0 || page > 1_000_000 || pageSize < 1 || pageSize > 100) {
            throw new BUSException(ErrorCode.INVALID_PAGE, "Trang phải >= 0 và kích thước trang từ 1 đến 100");
        }
    }

    @Override
    public PageResult<GameHistoryItem> getHistory(long userId, int page, int pageSize) throws BUSException {
        validatePagination(page, pageSize);

        try (Connection conn = connections.open()) {
            long totalItems;
            String countSql = "SELECT COUNT(*) FROM play_results WHERE user_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(countSql)) {
                stmt.setLong(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    rs.next();
                    totalItems = rs.getLong(1);
                }
            }

            List<GameHistoryItem> items = new ArrayList<>();
            String querySql = "SELECT r.id, r.round_id, r.room_name, p.outcome, p.profit, p.hand_type, r.ended_at, "
                    + "(SELECT COUNT(*) FROM play_results x WHERE x.round_id = r.round_id) AS players "
                    + "FROM play_results p "
                    + "JOIN play_rounds r ON r.round_id = p.round_id "
                    + "WHERE p.user_id = ? "
                    + "ORDER BY r.ended_at DESC, r.id DESC "
                    + "LIMIT ? OFFSET ?";

            try (PreparedStatement stmt = conn.prepareStatement(querySql)) {
                stmt.setLong(1, userId);
                stmt.setInt(2, pageSize);
                stmt.setLong(3, (long) page * pageSize);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        items.add(new GameHistoryItem(
                                rs.getLong(1),
                                rs.getString(2),
                                rs.getString(3),
                                rs.getString(4),
                                rs.getBigDecimal(5),
                                rs.getString(6),
                                rs.getLong(7),
                                rs.getInt(8)
                        ));
                    }
                }
            }

            int totalPages = PageResult.calcTotalPages(totalItems, pageSize);
            return new PageResult<>(List.copyOf(items), page, pageSize, totalItems, totalPages);
        } catch (SQLException e) {
            throw new BUSException(ErrorCode.INTERNAL_ERROR, "Không thể tải lịch sử ván đấu", e);
        }
    }

    @Override
    public PageResult<LeaderboardItem> getLeaderboard(int page, int pageSize) throws BUSException {
        validatePagination(page, pageSize);
        String cacheKey = page + ":" + pageSize;
        CachedLeaderboard cached = leaderboardCache.get(cacheKey);
        if (cached != null && System.nanoTime() < cached.expiresAtNanos()) return cached.result();

        try (Connection conn = connections.open()) {
            boolean hasRole = hasRoleColumn(conn);

            String countSql = hasRole ? "SELECT COUNT(*) FROM users WHERE role = 'USER'" : "SELECT COUNT(*) FROM users";
            long totalItems;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(countSql)) {
                rs.next();
                totalItems = rs.getLong(1);
            }

            List<LeaderboardItem> items = new ArrayList<>();
            String whereClause = hasRole ? "WHERE u.role = 'USER' " : "";
            String querySql = "SELECT u.id, u.display_name, COALESCE(t.points, 0) AS points, "
                    + "u.total_wins, u.total_games, u.total_losses "
                    + "FROM users u "
                    + "LEFT JOIN ("
                    + "   SELECT user_id, SUM(CASE WHEN profit > 0 THEN profit ELSE 0 END) AS points "
                    + "   FROM play_results "
                    + "   WHERE outcome <> 'CANCELLED' "
                    + "   GROUP BY user_id"
                    + ") t ON t.user_id = u.id "
                    + whereClause
                    + "ORDER BY points DESC, u.total_wins DESC, u.id ASC "
                    + "LIMIT ? OFFSET ?";

            try (PreparedStatement stmt = conn.prepareStatement(querySql)) {
                stmt.setInt(1, pageSize);
                stmt.setLong(2, (long) page * pageSize);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int rank = page * pageSize + items.size() + 1;
                        items.add(new LeaderboardItem(
                                rank,
                                rs.getLong(1),
                                rs.getString(2),
                                rs.getBigDecimal(3),
                                rs.getInt(4),
                                rs.getInt(5),
                                rs.getInt(6)
                        ));
                    }
                }
            }

            int totalPages = PageResult.calcTotalPages(totalItems, pageSize);
            PageResult<LeaderboardItem> result = new PageResult<>(List.copyOf(items), page, pageSize, totalItems, totalPages);
            if (leaderboardCache.size() >= 64) leaderboardCache.clear();
            leaderboardCache.put(cacheKey, new CachedLeaderboard(result, System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3)));
            return result;
        } catch (SQLException e) {
            throw new BUSException(ErrorCode.INTERNAL_ERROR, "Không thể tải bảng xếp hạng", e);
        }
    }

    private boolean hasRoleColumn(Connection connection) throws SQLException {
        Boolean cached = roleColumn;
        if (cached != null) return cached;
        synchronized (this) {
            if (roleColumn == null) {
                boolean found = false;
                // Compatibility for isolated test fixtures; production checkSchema requires role.
                try (var columns = connection.getMetaData().getColumns(connection.getCatalog(), null, null, null)) {
                    while (columns.next()) {
                        if ("users".equalsIgnoreCase(columns.getString("TABLE_NAME"))
                                && "role".equalsIgnoreCase(columns.getString("COLUMN_NAME"))) { found = true; break; }
                    }
                }
                roleColumn = found;
            }
            return roleColumn;
        }
    }
}
