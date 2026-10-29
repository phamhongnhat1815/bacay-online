package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

public record PointTransferRequest(
        String operationId, long recipientId, BigDecimal amount, String message
) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
}

