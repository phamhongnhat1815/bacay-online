package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

public record BetRecord(
        long userId,

        Long targetUserId,

        BetAction action,

        BigDecimal raisedPoint,

        BigDecimal raiseAmount,

        BigDecimal multiplierBefore,

        BigDecimal multiplierAfter,

        int sequenceNo,

        long createdAtMillis
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
