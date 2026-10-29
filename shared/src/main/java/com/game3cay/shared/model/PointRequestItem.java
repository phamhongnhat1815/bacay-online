package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

public record PointRequestItem(
        String requestId, long requesterId, String requesterName, long targetUserId, String targetName,
        long roomId, BigDecimal amount, String message, PointRequestStatus status,
        long createdAtMillis, long expiresAtMillis, String transferId
) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
}

