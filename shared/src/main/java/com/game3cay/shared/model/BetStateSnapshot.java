package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public record BetStateSnapshot(
        String roundId,

        Long currentTurnUserId,

        BigDecimal currentHighBet,

        BigDecimal mainPot,

        List<SidePot> sidePots,

        List<PlayerBetInfo> playerBets,
        long stateVersion
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public record PlayerBetInfo(
            long userId,
            String displayName,
            BigDecimal currentBet,
            boolean folded,
            boolean allIn
    ) implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }
}
