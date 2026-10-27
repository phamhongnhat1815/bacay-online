package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

public record AdminStatsSnapshot(
        long onlineUsers,
        int activeRooms,
        long totalRegisteredUsers,
        BigDecimal totalBalanceInCirculation,
        int totalRoundsPlayedToday
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
