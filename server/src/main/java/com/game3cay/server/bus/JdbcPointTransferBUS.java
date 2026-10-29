package com.game3cay.server.bus;

import com.game3cay.server.dal.ConnectionProvider;
import com.game3cay.server.dal.PointTransferDAO;
import com.game3cay.shared.model.*;
import com.game3cay.shared.network.ErrorCode;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class JdbcPointTransferBUS implements PointTransferBUS {
    private final PointTransferDAO points;
    private final PointTransferPolicy policy;

    public JdbcPointTransferBUS(ConnectionProvider connections) {
        this(connections, PointTransferPolicy.configured(), System::currentTimeMillis);
    }

    public JdbcPointTransferBUS(ConnectionProvider connections, PointTransferPolicy policy, LongSupplier clock) {
        this.policy = java.util.Objects.requireNonNull(policy);
        points = new PointTransferDAO(connections, policy, clock);
    }

    @Override public PointTransferOutcome transfer(long senderId, PointTransferRequest request, long roomId) throws BUSException {
        requireUser(senderId);
        if (request == null || roomId <= 0) throw invalid("Yêu cầu cho điểm không hợp lệ");
        requireUser(request.recipientId());
        if (senderId == request.recipientId()) throw invalid("Không thể tự cho điểm chính mình");
        requireUuid(request.operationId()); requireAmount(request.amount()); requireMessage(request.message());
        return database(() -> points.transfer(senderId, request, roomId));
    }

    @Override public PointRequestItem createRequest(long requesterId, PointRequestCreate request, long roomId) throws BUSException {
        requireUser(requesterId);
        if (request == null || roomId <= 0) throw invalid("Yêu cầu xin điểm không hợp lệ");
        requireUser(request.targetUserId());
        if (requesterId == request.targetUserId()) throw invalid("Không thể xin điểm chính mình");
        requireUuid(request.operationId()); requireAmount(request.amount()); requireMessage(request.message());
        return database(() -> points.createRequest(requesterId, request, roomId));
    }

    @Override public PointRequestItem getRequest(long actorId, String requestId) throws BUSException {
        requireUser(actorId); requireUuid(requestId);
        return database(() -> points.getRequest(actorId, requestId));
    }

    @Override public PointRequestResolution reply(long actorId, PointRequestReply reply) throws BUSException {
        requireUser(actorId);
        if (reply == null || reply.decision() == null) throw invalid("Phản hồi yêu cầu không hợp lệ");
        requireUuid(reply.operationId()); requireUuid(reply.requestId());
        return database(() -> points.reply(actorId, reply));
    }

    @Override public PageResult<PointRequestItem> listRequests(long actorId, PageRequest page) throws BUSException {
        requireUser(actorId); requirePage(page);
        return database(() -> points.listRequests(actorId, page));
    }

    @Override public PageResult<PointTransferReceipt> history(long actorId, PageRequest page) throws BUSException {
        requireUser(actorId); requirePage(page);
        return database(() -> points.history(actorId, page));
    }

    @Override public Optional<PointTransferReceipt> findTransfer(long senderId, String operationId) throws BUSException {
        requireUser(senderId); requireUuid(operationId);
        return database(() -> points.findTransfer(senderId, operationId));
    }

    @Override public PointTransferReceipt getTransfer(long actorId, String transferId) throws BUSException {
        requireUser(actorId); requireUuid(transferId);
        return database(() -> points.getTransfer(actorId, transferId));
    }

    @Override public Optional<PointRequestItem> findCreatedRequest(long requesterId, String operationId) throws BUSException {
        requireUser(requesterId); requireUuid(operationId);
        return database(() -> points.findCreatedRequest(requesterId, operationId));
    }

    private void requireAmount(BigDecimal amount) throws BUSException {
        if (amount == null || amount.signum() <= 0 || amount.scale() < 0 || amount.scale() > 2
                || amount.precision() > 15 || amount.compareTo(policy.maxTransfer()) > 0) {
            throw new BUSException(ErrorCode.INVALID_BET_AMOUNT, "Số điểm cần lớn hơn 0, tối đa "
                    + policy.maxTransfer().toPlainString() + " và tối đa 2 chữ số thập phân");
        }
    }

    private static void requireMessage(String message) throws BUSException {
        if (message != null && message.length() > 120) throw invalid("Lời nhắn tối đa 120 ký tự");
    }
    private static void requireUser(long id) throws BUSException { if (id <= 0) throw invalid("Người chơi không hợp lệ"); }
    private static void requireUuid(String value) throws BUSException {
        try { if (value == null || !UUID.fromString(value).toString().equals(value)) throw new IllegalArgumentException(); }
        catch (IllegalArgumentException e) { throw invalid("Mã thao tác không hợp lệ"); }
    }
    private static void requirePage(PageRequest page) throws BUSException {
        if (page == null || page.page() < 0 || page.page() > 1_000_000 || page.pageSize() < 1 || page.pageSize() > 100) {
            throw new BUSException(ErrorCode.INVALID_PAGE, "Tham số phân trang không hợp lệ");
        }
    }
    private static BUSException invalid(String message) { return new BUSException(ErrorCode.INVALID_REQUEST, message); }
    @FunctionalInterface private interface Work<T> { T run() throws SQLException, BUSException; }
    private static <T> T database(Work<T> work) throws BUSException {
        try { return work.run(); }
        catch (SQLException e) { throw new BUSException(ErrorCode.INTERNAL_ERROR, "Không thể xử lý dữ liệu xin/cho điểm", e); }
    }
}
