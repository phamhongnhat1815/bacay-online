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
        long timestamp
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
