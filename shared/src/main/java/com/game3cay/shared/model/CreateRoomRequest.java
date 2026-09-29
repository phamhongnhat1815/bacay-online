package com.game3cay.shared.model;

import java.io.Serializable;
import java.math.BigDecimal;

public record CreateRoomRequest(
        String roomName,
        int minPlayers,
        int maxPlayers,
        BigDecimal betAmount
) implements Serializable {
    private static final long serialVersionUID = 1L;
}