package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

public record GameHistoryItem(
        long roundDbId,
        String roundId,
        String roomName,
        String result,
        BigDecimal profit,
        String handType,
        long playedAt,
        int playerCount
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
