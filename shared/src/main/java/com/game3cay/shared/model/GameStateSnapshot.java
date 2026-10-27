package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public record GameStateSnapshot(
        String roundId,

        String phase,

        Long currentTurnUserId,

        List<Long> activePlayers,

        int foldedCount,

        long stateVersion
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
