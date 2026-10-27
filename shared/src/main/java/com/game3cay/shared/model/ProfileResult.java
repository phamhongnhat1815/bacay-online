package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

public record ProfileResult(
        long userId,
        String username,
        String displayName,
        String email,
        String avatarUrl,
        BigDecimal balance,
        int totalGames,
        int totalWins,
        int totalLosses
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
