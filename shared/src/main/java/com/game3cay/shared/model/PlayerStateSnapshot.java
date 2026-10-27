package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public record PlayerStateSnapshot(
        String roundId,

        List<Card> myCards,

        BigDecimal myBalance,

        BigDecimal myCurrentBet,

        boolean folded,

        boolean allIn,
        long stateVersion
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
