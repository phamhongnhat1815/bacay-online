package com.game3cay.shared.model;

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
        /** Trạng thái phòng: WAITING / PLAYING / FINISHED / RESULT */
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
        long stateVersion,
        /** Số giây đếm ngược tự động bắt đầu ván mới (0 nếu không có). */
        int countdownSeconds
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Constructor tương thích khi không truyền countdownSeconds. */
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
