package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public record AdminLivePlayerHand(
        long userId,
        String username,
        String displayName,
        int seat,
        BigDecimal balance,
        BigDecimal currentBet,
        String status,
        List<Card> cards,
        String evaluation
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
