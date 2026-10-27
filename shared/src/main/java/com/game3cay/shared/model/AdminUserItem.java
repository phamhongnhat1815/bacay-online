package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

public record AdminUserItem(
        long userId,
        String username,
        String displayName,
        String email,
        BigDecimal balance,
        String status,
        String role,
        int totalGames,
        int totalWins,
        int totalLosses,
        long createdAt
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
