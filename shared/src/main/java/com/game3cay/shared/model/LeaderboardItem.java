package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

public record LeaderboardItem(
        int rank,
        long userId,
        String displayName,
        BigDecimal totalPoints,
        int totalWins,
        int totalGames,
        int totalLosses
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
