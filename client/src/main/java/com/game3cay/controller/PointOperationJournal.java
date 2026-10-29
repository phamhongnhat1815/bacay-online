package com.game3cay.controller;

import com.game3cay.shared.model.*;
import com.game3cay.shared.network.PacketType;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Properties;
import java.util.prefs.Preferences;

/** Persists one unresolved command so reconnect/relaunch reuses the business operation ID. */
final class PointOperationJournal {
    record Operation(PacketType type, Object data) {
        String operationId() {
            if (data instanceof PointTransferRequest transfer) return transfer.operationId();
            if (data instanceof PointRequestCreate create) return create.operationId();
            if (data instanceof PointRequestReply reply) return reply.operationId();
            throw new IllegalStateException("Không phải thao tác điểm");
        }
    }

    private final Preferences storage;
    PointOperationJournal(Preferences storage) { this.storage = storage; }

    static PointOperationJournal forAccount(String host, int port, long userId) {
        try {
            String scope = host.toLowerCase(java.util.Locale.ROOT) + ":" + port + ":" + userId;
            String key = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(scope.getBytes(StandardCharsets.UTF_8)));
            return new PointOperationJournal(Preferences.userNodeForPackage(PointOperationJournal.class).node("point-operations/" + key));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    void save(Operation operation) {
        Properties values = new Properties();
        values.setProperty("type", operation.type().name());
        values.setProperty("operation", operation.operationId());
        if (operation.data() instanceof PointTransferRequest transfer) {
            values.setProperty("peer", Long.toString(transfer.recipientId()));
            values.setProperty("amount", transfer.amount().toPlainString());
            values.setProperty("message", transfer.message() == null ? "" : transfer.message());
        } else if (operation.data() instanceof PointRequestCreate create) {
            values.setProperty("peer", Long.toString(create.targetUserId()));
            values.setProperty("amount", create.amount().toPlainString());
            values.setProperty("message", create.message() == null ? "" : create.message());
        } else if (operation.data() instanceof PointRequestReply reply) {
            values.setProperty("request", reply.requestId());
            values.setProperty("decision", reply.decision().name());
        }
        try {
            StringWriter text = new StringWriter();
            values.store(text, null);
            storage.put("pending", text.toString());
            storage.flush();
        } catch (Exception e) { throw new IllegalStateException("Không lưu được mã giao dịch. Chưa gửi yêu cầu.", e); }
    }

    Optional<Operation> load() {
        String saved = storage.get("pending", null);
        if (saved == null) return Optional.empty();
        try {
            Properties values = new Properties();
            values.load(new StringReader(saved));
            PacketType type = PacketType.valueOf(values.getProperty("type"));
            String id = values.getProperty("operation");
            java.util.UUID.fromString(id);
            Object payload = switch (type) {
                case POINT_TRANSFER -> new PointTransferRequest(id, Long.parseLong(values.getProperty("peer")),
                        new BigDecimal(values.getProperty("amount")), values.getProperty("message", ""));
                case POINT_REQUEST_CREATE -> new PointRequestCreate(id, Long.parseLong(values.getProperty("peer")),
                        new BigDecimal(values.getProperty("amount")), values.getProperty("message", ""));
                case POINT_REQUEST_REPLY -> new PointRequestReply(id, values.getProperty("request"),
                        PointRequestDecision.valueOf(values.getProperty("decision")));
                default -> throw new IllegalArgumentException("Loại giao dịch không hợp lệ");
            };
            return Optional.of(new Operation(type, payload));
        } catch (Exception e) {
            // Keep the journal for diagnosis; do not silently replace an ambiguous command.
            throw new IllegalStateException("Không đọc được giao dịch điểm đang chờ xác minh.", e);
        }
    }

    void clear() {
        storage.remove("pending");
        try { storage.flush(); }
        catch (java.util.prefs.BackingStoreException e) {
            java.util.logging.Logger.getLogger(getClass().getName()).warning("Không đồng bộ được trạng thái giao dịch điểm");
        }
    }

    java.util.Set<Long> ignoredUsers() {
        java.util.Set<Long> result = new java.util.HashSet<>();
        for (String value : storage.get("ignored", "").split(",")) {
            try { if (!value.isBlank()) result.add(Long.parseLong(value)); } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    void ignoredUsers(java.util.Set<Long> users) {
        storage.put("ignored", users.stream().sorted().limit(200).map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
    }
}
