package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

public record PointTransferReceipt(
        String transferId, String operationId, String requestId, long senderId, String senderName,
        long recipientId, String recipientName, BigDecimal amount, String message, long committedAtMillis
) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
}

