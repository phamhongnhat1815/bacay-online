package com.game3cay.shared.model;

import java.io.Serializable;
import java.math.BigDecimal;

public record RoomSummary(
        long roomId,
        String roomCode,
        String roomName,
        String status,
        int playerCount,
        int maxPlayers,
        BigDecimal betAmount
) implements Serializable {
    private static final long serialVersionUID = 1L;
}