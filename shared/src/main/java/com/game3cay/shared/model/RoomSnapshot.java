package com.bacay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Snapshot trạng thái phòng chờ — gửi kèm {@code ROOM_UPDATED}.
 * Chỉ chứa thông tin công khai; không chứa bài, điểm cược riêng.
 */
public record RoomSnapshot(
        long roomId,
        String roomCode,
        String roomName,
        /** Trạng thái phòng: WAITING / PLAYING / FINISHED */
        String status,
        long ownerUserId,
        int minPlayers,
        int maxPlayers,
        BigDecimal betAmount,
        List<PlayerSlot> players,
        /**
         * Tăng mỗi khi trạng thái phòng thay đổi.
         * Client dùng để bỏ qua bản cũ đến trễ.
         */
        long stateVersion
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Thông tin một người chơi trong slot của phòng. */
    public record PlayerSlot(
            long userId,
            String displayName,
            int seatNumber,
            boolean ready,
            /** PLAYING / SPECTATOR / LEFT */
            String status
    ) implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }
}
