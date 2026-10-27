package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public record AdminLiveMatchDetail(
        long roomId,
        String roomName,
        String status,
        BigDecimal ante,
        BigDecimal currentPot,
        String roundId,
        Long currentTurnUserId,
        String currentTurnDisplayName,
        int stage,
        int playerCount,
        int maxPlayers,
        List<AdminLivePlayerHand> players,
        List<ChatMessage> recentChats
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
