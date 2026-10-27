package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

public record CreateRoomRequest(
        String roomName,
        int minPlayers,
        int maxPlayers,
        BigDecimal betAmount
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
