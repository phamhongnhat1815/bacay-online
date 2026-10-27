package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public record RoomSnapshot(
        long roomId,
        String roomCode,
        String roomName,

        String status,
        long ownerUserId,
        int minPlayers,
        int maxPlayers,
        BigDecimal betAmount,
        List<PlayerSlot> players,

        long stateVersion,

        int countdownSeconds
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public RoomSnapshot(
            long roomId,
            String roomCode,
            String roomName,
            String status,
            long ownerUserId,
            int minPlayers,
            int maxPlayers,
            BigDecimal betAmount,
            List<PlayerSlot> players,
            long stateVersion
    ) {
        this(roomId, roomCode, roomName, status, ownerUserId, minPlayers, maxPlayers, betAmount, players, stateVersion, 0);
    }

    public record PlayerSlot(
            long userId,
            String displayName,
            int seatNumber,
            boolean ready,

            String status
    ) implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }
}
