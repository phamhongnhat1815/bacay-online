package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

public record AdminLedgerItem(
        String roundId,
        long userId,
        String username,
        BigDecimal delta,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        long timestamp,
        String transactionType,
        String adminUsername,
        String reason
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public AdminLedgerItem(String roundId, long userId, String username, BigDecimal delta,
                           BigDecimal balanceBefore, BigDecimal balanceAfter, long timestamp) {
        this(roundId, userId, username, delta, balanceBefore, balanceAfter, timestamp, "GAME", null, null);
    }
}
