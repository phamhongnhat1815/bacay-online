package com.game3cay.gui;

import com.game3cay.shared.model.*;
import java.math.BigDecimal;

/** UI eligibility is advisory; the server validates the same operation again. */
public final class PointUiRules {
    private PointUiRules() {}

    public static BigDecimal amount(String text) {
        BigDecimal amount = new BigDecimal(text.trim());
        if (amount.signum() <= 0 || amount.scale() < 0 || amount.scale() > 2 || amount.precision() > 15) {
            throw new IllegalArgumentException("Số điểm phải lớn hơn 0, tối đa 2 chữ số thập phân.");
        }
        return amount;
    }

    public static boolean pending(PointRequestItem request, long now) {
        return request != null && request.status() == PointRequestStatus.PENDING && request.expiresAtMillis() > now;
    }

    public static PointRequestItem newest(PointRequestItem previous, PointRequestItem incoming) {
        return previous != null && previous.status() != PointRequestStatus.PENDING
                && incoming.status() == PointRequestStatus.PENDING ? previous : incoming;
    }

    public static boolean canAccept(PointRequestItem request, long userId, RoomSnapshot room, long now, boolean blocked) {
        return !blocked && pending(request, now) && request.requesterId() != userId && request.targetUserId() == userId && room != null
                && room.roomId() == request.roomId() && "WAITING".equals(room.status())
                && room.players().stream().anyMatch(p -> p.userId() == request.requesterId())
                && room.players().stream().anyMatch(p -> p.userId() == userId);
    }

    public static String status(PointRequestItem request, long now) {
        if (request.status() == PointRequestStatus.PENDING && request.expiresAtMillis() <= now) return "Hết hạn";
        return switch (request.status()) {
            case PENDING -> "Đang chờ";
            case FULFILLED -> "Đã cho điểm";
            case DECLINED -> "Đã từ chối";
            case CANCELLED -> "Đã hủy";
            case EXPIRED -> "Hết hạn";
        };
    }

    public static String confirmation(String recipientName, long recipientId, BigDecimal amount, BigDecimal balance) {
        return "Cho " + recipientName + " (#" + recipientId + ") " + Theme.points(amount) + " điểm?\n"
                + "Số dư của bạn: " + Theme.points(balance) + " → " + Theme.points(balance.subtract(amount)) + " điểm.";
    }
}
