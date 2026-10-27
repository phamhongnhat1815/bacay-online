package com.game3cay.server.bus;

import java.math.BigDecimal;

public record UserDTO(
        long userId,
        String username,
        String displayName,
        String email,
        String avatarUrl,
        BigDecimal balance,
        String status,
        String role,
        int totalGames,
        int totalWins,
        int totalLosses
) {
    public UserDTO(
            long userId,
            String username,
            String displayName,
            String email,
            String avatarUrl,
            BigDecimal balance,
            String status,
            int totalGames,
            int totalWins,
            int totalLosses
    ) {
        this(userId, username, displayName, email, avatarUrl, balance, status, "USER", totalGames, totalWins, totalLosses);
    }
}
