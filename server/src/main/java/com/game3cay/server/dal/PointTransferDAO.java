package com.game3cay.server.dal;

import com.game3cay.server.bus.*;
import com.game3cay.shared.model.*;
import com.game3cay.shared.network.ErrorCode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import java.util.function.LongSupplier;

/** Durable peer transfers. Room/round reservations are held by RoomManager around money mutations. */
public final class PointTransferDAO {
    private static final BigDecimal MAX_BALANCE = new BigDecimal("9999999999999.99");
    private static final long DAY_MILLIS = 86_400_000L;
    private final ConnectionProvider connections;
    private final PointTransferPolicy policy;
    private final LongSupplier clock;
    private record Account(long id, String name, BigDecimal balance, String role, String status, int games) {}
    private record Saved(PointTransferReceipt receipt, String fingerprint, BigDecimal senderAfter, BigDecimal recipientAfter) {}
    private record SavedRequest(PointRequestItem item, String operationId, String fingerprint) {}

    public PointTransferDAO(ConnectionProvider connections, PointTransferPolicy policy, LongSupplier clock) {
        this.connections = Objects.requireNonNull(connections);
        this.policy = Objects.requireNonNull(policy);
        this.clock = Objects.requireNonNull(clock);
    }

    public PointTransferOutcome transfer(long senderId, PointTransferRequest request, long roomId) throws SQLException, BUSException {
        String fingerprint = fingerprint(senderId, request.recipientId(), request.amount(), request.message(), null);
        return transaction(conn -> {
            Saved previous = savedByOperation(conn, senderId, request.operationId());
            if (previous != null) return replay(previous, fingerprint);
            Map<Long, Account> users = lockUsers(conn, senderId, request.recipientId());
            previous = savedByOperation(conn, senderId, request.operationId());
            if (previous != null) return replay(previous, fingerprint);
            return commitTransfer(conn, users, senderId, request.recipientId(), request.amount(), request.message(),
                    request.operationId(), roomId, null, fingerprint, clock.getAsLong());
        });
    }

    public PointRequestItem createRequest(long requesterId, PointRequestCreate request, long roomId) throws SQLException, BUSException {
        String hash = fingerprint(requesterId, request.targetUserId(), request.amount(), request.message(), "REQUEST");
        return transaction(conn -> {
            SavedRequest existing = requestByOperation(conn, requesterId, request.operationId());
            if (existing != null) return replayRequest(existing, hash);
            Map<Long, Account> users = lockUsers(conn, requesterId, request.targetUserId());
            requirePlayer(users.get(requesterId)); requirePlayer(users.get(request.targetUserId()));
            existing = requestByOperation(conn, requesterId, request.operationId());
            if (existing != null) return replayRequest(existing, hash);
            long now = clock.getAsLong();
            try (PreparedStatement query = conn.prepareStatement("SELECT COUNT(*) FROM point_requests "
                    + "WHERE status='PENDING' AND expires_at>? AND ((requester_id=? AND target_id=?) OR (requester_id=? AND target_id=?))")) {
                query.setLong(1, now); query.setLong(2, requesterId); query.setLong(3, request.targetUserId());
                query.setLong(4, request.targetUserId()); query.setLong(5, requesterId);
                try (ResultSet rows = query.executeQuery()) {
                    rows.next();
                    if (rows.getLong(1) > 0) throw fail(ErrorCode.INVALID_STATE, "Hai người đã có một yêu cầu xin điểm đang chờ");
                }
            }
            try (PreparedStatement query = conn.prepareStatement("SELECT COUNT(*) FROM point_requests WHERE requester_id=? AND created_at>?")) {
                query.setLong(1, requesterId); query.setLong(2, now - 60_000);
                try (ResultSet rows = query.executeQuery()) {
                    rows.next();
                    if (rows.getLong(1) >= policy.requestsPerMinute()) throw fail(ErrorCode.RATE_LIMIT_EXCEEDED,
                            "Bạn chỉ được gửi " + policy.requestsPerMinute() + " yêu cầu xin điểm mỗi phút");
                }
            }
            PointRequestItem item = new PointRequestItem(UUID.randomUUID().toString(), requesterId,
                    users.get(requesterId).name(), request.targetUserId(), users.get(request.targetUserId()).name(),
                    roomId, request.amount().setScale(2), message(request.message()), PointRequestStatus.PENDING,
                    now, now + policy.requestExpirySeconds() * 1000L, null);
            try (PreparedStatement insert = conn.prepareStatement("INSERT INTO point_requests "
                    + "(request_id,operation_id,payload_hash,requester_id,requester_name,target_id,target_name,room_id,amount,message,status,created_at,expires_at,transfer_id) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,NULL)")) {
                int i = 1;
                insert.setString(i++, item.requestId()); insert.setString(i++, request.operationId()); insert.setString(i++, hash);
                insert.setLong(i++, requesterId); insert.setString(i++, item.requesterName()); insert.setLong(i++, item.targetUserId());
                insert.setString(i++, item.targetName()); insert.setLong(i++, roomId); insert.setBigDecimal(i++, item.amount());
                insert.setString(i++, item.message()); insert.setString(i++, "PENDING"); insert.setLong(i++, now); insert.setLong(i, item.expiresAtMillis());
                insert.executeUpdate();
            }
            return item;
        });
    }

    public PointRequestItem getRequest(long actorId, String requestId) throws SQLException, BUSException {
        try (Connection conn = connections.open()) {
            expireRequests(conn, actorId);
            PointRequestItem item = requireRequest(conn, requestId, false).item();
            requireParticipant(actorId, item);
            return effective(item);
        }
    }

    public PointRequestResolution reply(long actorId, PointRequestReply reply) throws SQLException, BUSException {
        return transaction(conn -> {
            PointRequestItem item = requireRequest(conn, reply.requestId(), true).item();
            requireParticipant(actorId, item);
            boolean cancelling = reply.decision() == PointRequestDecision.CANCEL;
            if ((cancelling && actorId != item.requesterId()) || (!cancelling && actorId != item.targetUserId())) {
                throw fail(ErrorCode.FORBIDDEN, "Bạn không có quyền phản hồi yêu cầu này");
            }
            if (reply.decision() == PointRequestDecision.ACCEPT && item.status() == PointRequestStatus.FULFILLED) {
                Saved saved = savedById(conn, item.transferId());
                if (saved == null) throw new SQLException("Yêu cầu đã hoàn tất nhưng thiếu giao dịch");
                return new PointRequestResolution(item,
                        new PointTransferOutcome(saved.receipt(), saved.senderAfter(), saved.recipientAfter(), false));
            }
            PointRequestStatus desired = cancelling ? PointRequestStatus.CANCELLED : PointRequestStatus.DECLINED;
            if (reply.decision() != PointRequestDecision.ACCEPT && item.status() == desired) return new PointRequestResolution(item, null);
            long now = clock.getAsLong();
            if (item.status() != PointRequestStatus.PENDING || item.expiresAtMillis() <= now) {
                throw fail(ErrorCode.INVALID_STATE, "Yêu cầu đã kết thúc hoặc hết hạn");
            }
            if (reply.decision() == PointRequestDecision.ACCEPT) {
                Map<Long, Account> users = lockUsers(conn, item.targetUserId(), item.requesterId());
                now = clock.getAsLong();
                if (item.expiresAtMillis() <= now) throw fail(ErrorCode.INVALID_STATE, "Yêu cầu đã hết hạn");
                String hash = fingerprint(item.targetUserId(), item.requesterId(), item.amount(), item.message(), item.requestId());
                Saved existing = savedByOperation(conn, actorId, reply.operationId());
                if (existing != null) {
                    PointTransferOutcome replay = replay(existing, hash);
                    return new PointRequestResolution(item, replay);
                }
                PointTransferOutcome transfer = commitTransfer(conn, users, item.targetUserId(), item.requesterId(), item.amount(),
                        item.message(), reply.operationId(), item.roomId(), item.requestId(), hash, now);
                updateRequest(conn, item.requestId(), PointRequestStatus.FULFILLED, transfer.receipt().transferId());
                PointRequestItem fulfilled = withStatus(item, PointRequestStatus.FULFILLED, transfer.receipt().transferId());
                return new PointRequestResolution(fulfilled, transfer);
            }
            updateRequest(conn, item.requestId(), desired, null);
            return new PointRequestResolution(withStatus(item, desired, null), null);
        });
    }

    public PageResult<PointRequestItem> listRequests(long actorId, PageRequest page) throws SQLException {
        try (Connection conn = connections.open()) {
            expireRequests(conn, actorId);
            long total = count(conn, "SELECT COUNT(*) FROM point_requests WHERE requester_id=? OR target_id=?", actorId, actorId);
            List<PointRequestItem> items = new ArrayList<>();
            try (PreparedStatement query = conn.prepareStatement("SELECT * FROM point_requests WHERE requester_id=? OR target_id=? "
                    + "ORDER BY CASE WHEN status='PENDING' THEN 0 ELSE 1 END,created_at DESC,request_id DESC LIMIT ? OFFSET ?")) {
                query.setLong(1, actorId); query.setLong(2, actorId); query.setInt(3, page.pageSize()); query.setLong(4, (long) page.page() * page.pageSize());
                try (ResultSet rows = query.executeQuery()) { while (rows.next()) items.add(effective(readRequest(rows).item())); }
            }
            return new PageResult<>(List.copyOf(items), page.page(), page.pageSize(), total, PageResult.calcTotalPages(total, page.pageSize()));
        }
    }

    public PageResult<PointTransferReceipt> history(long actorId, PageRequest page) throws SQLException {
        try (Connection conn = connections.open()) {
            long total = count(conn, "SELECT COUNT(*) FROM point_transfers WHERE sender_id=? OR recipient_id=?", actorId, actorId);
            List<PointTransferReceipt> items = new ArrayList<>();
            try (PreparedStatement query = conn.prepareStatement("SELECT * FROM point_transfers WHERE sender_id=? OR recipient_id=? "
                    + "ORDER BY committed_at DESC,transfer_id DESC LIMIT ? OFFSET ?")) {
                query.setLong(1, actorId); query.setLong(2, actorId); query.setInt(3, page.pageSize()); query.setLong(4, (long) page.page() * page.pageSize());
                try (ResultSet rows = query.executeQuery()) { while (rows.next()) items.add(readReceipt(rows)); }
            }
            return new PageResult<>(List.copyOf(items), page.page(), page.pageSize(), total, PageResult.calcTotalPages(total, page.pageSize()));
        }
    }

    public Optional<PointTransferReceipt> findTransfer(long senderId, String operationId) throws SQLException {
        try (Connection conn = connections.open()) {
            Saved saved = savedByOperation(conn, senderId, operationId);
            return saved == null ? Optional.empty() : Optional.of(saved.receipt());
        }
    }

    public Optional<PointRequestItem> findCreatedRequest(long requesterId, String operationId) throws SQLException {
        try (Connection conn = connections.open()) {
            expireRequests(conn, requesterId);
            SavedRequest saved = requestByOperation(conn, requesterId, operationId);
            return saved == null ? Optional.empty() : Optional.of(effective(saved.item()));
        }
    }

    public PointTransferReceipt getTransfer(long actorId, String transferId) throws SQLException, BUSException {
        try (Connection conn = connections.open(); PreparedStatement query = conn.prepareStatement("SELECT * FROM point_transfers WHERE transfer_id=?")) {
            query.setString(1, transferId);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) throw fail(ErrorCode.INVALID_REQUEST, "Giao dịch không tồn tại");
                PointTransferReceipt receipt = readReceipt(rows);
                if (actorId != receipt.senderId() && actorId != receipt.recipientId()) throw fail(ErrorCode.FORBIDDEN, "Không có quyền xem giao dịch");
                return receipt;
            }
        }
    }

    private PointTransferOutcome commitTransfer(Connection conn, Map<Long, Account> users, long senderId, long recipientId,
            BigDecimal rawAmount, String rawMessage, String operationId, long roomId, String requestId, String hash, long now)
            throws SQLException, BUSException {
        Account sender = users.get(senderId), recipient = users.get(recipientId);
        requirePlayer(sender); requirePlayer(recipient);
        if (rawAmount == null || rawAmount.signum() <= 0 || rawAmount.scale() < 0 || rawAmount.scale() > 2
                || rawAmount.precision() > 15 || rawAmount.compareTo(policy.maxTransfer()) > 0) {
            throw fail(ErrorCode.INVALID_BET_AMOUNT, "Số điểm vượt giới hạn cho mỗi lần: " + policy.maxTransfer().toPlainString());
        }
        if (sender.games() < policy.minCompletedGames()) throw fail(ErrorCode.INVALID_STATE,
                "Cần hoàn tất ít nhất " + policy.minCompletedGames() + " ván để cho điểm");
        BigDecimal amount = rawAmount.setScale(2);
        if (sender.balance().compareTo(amount) < 0) throw fail(ErrorCode.INVALID_BET_AMOUNT, "Số dư không đủ để cho điểm");
        BigDecimal recipientAfter = recipient.balance().add(amount);
        if (recipientAfter.compareTo(MAX_BALANCE) > 0) throw fail(ErrorCode.INVALID_BET_AMOUNT, "Số dư người nhận vượt giới hạn");
        try (PreparedStatement query = conn.prepareStatement("SELECT COUNT(*) FROM point_transfers WHERE sender_id=? AND committed_at>?")) {
            query.setLong(1, senderId); query.setLong(2, now - 60_000L);
            try (ResultSet rows = query.executeQuery()) {
                rows.next();
                if (rows.getLong(1) >= policy.transfersPerMinute()) throw fail(ErrorCode.RATE_LIMIT_EXCEEDED,
                        "Bạn chỉ được cho điểm tối đa " + policy.transfersPerMinute() + " lần mỗi phút");
            }
        }
        BigDecimal alreadySent;
        try (PreparedStatement query = conn.prepareStatement("SELECT COALESCE(SUM(amount),0) FROM point_transfers WHERE sender_id=? AND committed_at>?")) {
            query.setLong(1, senderId); query.setLong(2, now - DAY_MILLIS);
            try (ResultSet rows = query.executeQuery()) { rows.next(); alreadySent = rows.getBigDecimal(1); }
        }
        if (alreadySent.add(amount).compareTo(policy.dailyLimit()) > 0) throw fail(ErrorCode.RATE_LIMIT_EXCEEDED,
                "Tổng điểm cho trong 24 giờ không được vượt " + policy.dailyLimit().toPlainString());
        BigDecimal senderAfter = sender.balance().subtract(amount);
        PointTransferReceipt receipt = new PointTransferReceipt(UUID.randomUUID().toString(), operationId, requestId,
                senderId, sender.name(), recipientId, recipient.name(), amount, message(rawMessage), now);
        try (PreparedStatement insert = conn.prepareStatement("INSERT INTO point_transfers "
                + "(transfer_id,operation_id,payload_hash,request_id,room_id,sender_id,sender_name,recipient_id,recipient_name,amount,message,committed_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) {
            insert.setString(1, receipt.transferId()); insert.setString(2, operationId); insert.setString(3, hash);
            insert.setString(4, requestId); insert.setLong(5, roomId); insert.setLong(6, senderId); insert.setString(7, sender.name());
            insert.setLong(8, recipientId); insert.setString(9, recipient.name()); insert.setBigDecimal(10, amount);
            insert.setString(11, receipt.message()); insert.setLong(12, now); insert.executeUpdate();
        }
        changeBalance(conn, senderId, senderAfter); changeBalance(conn, recipientId, recipientAfter);
        ledger(conn, receipt.transferId(), senderId, amount.negate(), sender.balance(), senderAfter);
        ledger(conn, receipt.transferId(), recipientId, amount, recipient.balance(), recipientAfter);
        return new PointTransferOutcome(receipt, senderAfter, recipientAfter, true);
    }

    private Map<Long, Account> lockUsers(Connection conn, long first, long second) throws SQLException, BUSException {
        if (first == second) throw fail(ErrorCode.INVALID_REQUEST, "Không thể chuyển điểm chính mình");
        Map<Long, Account> users = new HashMap<>();
        for (long id : new long[]{Math.min(first, second), Math.max(first, second)}) {
            try (PreparedStatement query = conn.prepareStatement("SELECT id,display_name,balance,role,status,total_games FROM users WHERE id=? FOR UPDATE")) {
                query.setLong(1, id);
                try (ResultSet rows = query.executeQuery()) {
                    if (!rows.next()) throw fail(ErrorCode.INVALID_REQUEST, "Người chơi không tồn tại");
                    users.put(id, new Account(id, rows.getString("display_name"), rows.getBigDecimal("balance"),
                            rows.getString("role"), rows.getString("status"), rows.getInt("total_games")));
                }
            }
        }
        return users;
    }

    private static void requirePlayer(Account user) throws BUSException {
        if (user == null || !"USER".equalsIgnoreCase(user.role())) throw fail(ErrorCode.FORBIDDEN, "Chỉ người chơi được xin/cho điểm");
        if (!"ACTIVE".equalsIgnoreCase(user.status())) throw fail(ErrorCode.USER_BLOCKED, "Tài khoản đang bị khóa");
    }
    private static void requireParticipant(long actorId, PointRequestItem request) throws BUSException {
        if (actorId != request.requesterId() && actorId != request.targetUserId()) throw fail(ErrorCode.FORBIDDEN, "Không có quyền xem yêu cầu");
    }
    private PointRequestItem effective(PointRequestItem item) {
        return item.status() == PointRequestStatus.PENDING && item.expiresAtMillis() <= clock.getAsLong()
                ? withStatus(item, PointRequestStatus.EXPIRED, null) : item;
    }
    private void expireRequests(Connection conn, long actorId) throws SQLException {
        try (PreparedStatement update = conn.prepareStatement("UPDATE point_requests SET status='EXPIRED' WHERE status='PENDING' AND expires_at<=? AND (requester_id=? OR target_id=?)")) {
            update.setLong(1, clock.getAsLong()); update.setLong(2, actorId); update.setLong(3, actorId); update.executeUpdate();
        }
    }
    private static void updateRequest(Connection conn, String id, PointRequestStatus state, String transferId) throws SQLException {
        try (PreparedStatement update = conn.prepareStatement("UPDATE point_requests SET status=?,transfer_id=? WHERE request_id=?")) {
            update.setString(1, state.name()); update.setString(2, transferId); update.setString(3, id);
            if (update.executeUpdate() != 1) throw new SQLException("Yêu cầu vừa bị xóa");
        }
    }
    private static PointRequestItem withStatus(PointRequestItem item, PointRequestStatus state, String transfer) {
        return new PointRequestItem(item.requestId(), item.requesterId(), item.requesterName(), item.targetUserId(), item.targetName(),
                item.roomId(), item.amount(), item.message(), state, item.createdAtMillis(), item.expiresAtMillis(), transfer);
    }
    private static void changeBalance(Connection conn, long id, BigDecimal amount) throws SQLException {
        try (PreparedStatement update = conn.prepareStatement("UPDATE users SET balance=? WHERE id=?")) {
            update.setBigDecimal(1, amount); update.setLong(2, id);
            if (update.executeUpdate() != 1) throw new SQLException("Người chơi vừa bị xóa");
        }
    }
    private static void ledger(Connection conn, String transferId, long user, BigDecimal delta, BigDecimal before, BigDecimal after) throws SQLException {
        try (PreparedStatement insert = conn.prepareStatement("INSERT INTO point_transfer_entries(transfer_id,user_id,delta,balance_before,balance_after) VALUES(?,?,?,?,?)")) {
            insert.setString(1, transferId); insert.setLong(2, user); insert.setBigDecimal(3, delta);
            insert.setBigDecimal(4, before); insert.setBigDecimal(5, after); insert.executeUpdate();
        }
    }
    private Saved savedByOperation(Connection conn, long sender, String operation) throws SQLException {
        return saved(conn, "t.sender_id=? AND t.operation_id=?", sender, operation);
    }
    private Saved savedById(Connection conn, String transferId) throws SQLException {
        return saved(conn, "t.transfer_id=?", transferId);
    }
    private Saved saved(Connection conn, String condition, Object... values) throws SQLException {
        String sql = "SELECT t.*,s.balance_after AS sender_after,r.balance_after AS recipient_after FROM point_transfers t "
                + "JOIN point_transfer_entries s ON s.transfer_id=t.transfer_id AND s.user_id=t.sender_id "
                + "JOIN point_transfer_entries r ON r.transfer_id=t.transfer_id AND r.user_id=t.recipient_id WHERE " + condition;
        try (PreparedStatement query = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) query.setObject(i + 1, values[i]);
            try (ResultSet rows = query.executeQuery()) {
                return rows.next() ? new Saved(readReceipt(rows), rows.getString("payload_hash"),
                        rows.getBigDecimal("sender_after"), rows.getBigDecimal("recipient_after")) : null;
            }
        }
    }
    private static PointTransferOutcome replay(Saved saved, String fingerprint) throws BUSException {
        if (!saved.fingerprint().equals(fingerprint)) throw fail(ErrorCode.INVALID_REQUEST, "Mã thao tác đã được dùng với dữ liệu khác");
        return new PointTransferOutcome(saved.receipt(), saved.senderAfter(), saved.recipientAfter(), false);
    }
    private PointRequestItem replayRequest(SavedRequest saved, String fingerprint) throws BUSException {
        if (!saved.fingerprint().equals(fingerprint)) throw fail(ErrorCode.INVALID_REQUEST, "Mã thao tác đã được dùng với dữ liệu khác");
        return effective(saved.item());
    }
    private SavedRequest requestByOperation(Connection conn, long requester, String operation) throws SQLException {
        try (PreparedStatement query = conn.prepareStatement("SELECT * FROM point_requests WHERE requester_id=? AND operation_id=?")) {
            query.setLong(1, requester); query.setString(2, operation);
            try (ResultSet rows = query.executeQuery()) { return rows.next() ? readRequest(rows) : null; }
        }
    }
    private SavedRequest requireRequest(Connection conn, String id, boolean lock) throws SQLException, BUSException {
        try (PreparedStatement query = conn.prepareStatement("SELECT * FROM point_requests WHERE request_id=?" + (lock ? " FOR UPDATE" : ""))) {
            query.setString(1, id);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) throw fail(ErrorCode.INVALID_REQUEST, "Yêu cầu xin điểm không tồn tại");
                return readRequest(rows);
            }
        }
    }
    private static SavedRequest readRequest(ResultSet rows) throws SQLException {
        return new SavedRequest(new PointRequestItem(rows.getString("request_id"), rows.getLong("requester_id"), rows.getString("requester_name"),
                rows.getLong("target_id"), rows.getString("target_name"), rows.getLong("room_id"), rows.getBigDecimal("amount"), rows.getString("message"),
                PointRequestStatus.valueOf(rows.getString("status")), rows.getLong("created_at"), rows.getLong("expires_at"), rows.getString("transfer_id")),
                rows.getString("operation_id"), rows.getString("payload_hash"));
    }
    private static PointTransferReceipt readReceipt(ResultSet rows) throws SQLException {
        return new PointTransferReceipt(rows.getString("transfer_id"), rows.getString("operation_id"), rows.getString("request_id"),
                rows.getLong("sender_id"), rows.getString("sender_name"), rows.getLong("recipient_id"), rows.getString("recipient_name"),
                rows.getBigDecimal("amount"), rows.getString("message"), rows.getLong("committed_at"));
    }
    private static long count(Connection conn, String sql, long first, long second) throws SQLException {
        try (PreparedStatement query = conn.prepareStatement(sql)) {
            query.setLong(1, first); query.setLong(2, second);
            try (ResultSet rows = query.executeQuery()) { rows.next(); return rows.getLong(1); }
        }
    }
    private static String message(String raw) { return raw == null ? "" : raw.strip(); }
    private static String fingerprint(long sender, long recipient, BigDecimal amount, String text, String requestId) {
        String content = sender + "\n" + recipient + "\n" + amount.setScale(2).toPlainString() + "\n"
                + Objects.toString(requestId, "") + "\n" + message(text);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static BUSException fail(ErrorCode code, String message) { return new BUSException(code, message); }
    @FunctionalInterface private interface Work<T> { T run(Connection conn) throws SQLException, BUSException; }
    private <T> T transaction(Work<T> work) throws SQLException, BUSException {
        try (Connection conn = connections.open()) {
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            conn.setAutoCommit(false);
            try { T value = work.run(conn); conn.commit(); return value; }
            catch (SQLException | BUSException | RuntimeException e) {
                try { conn.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        }
    }
}
