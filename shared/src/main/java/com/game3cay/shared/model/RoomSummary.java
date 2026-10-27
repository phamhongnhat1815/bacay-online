package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Tóm tắt trạng thái của một bàn chơi hiển thị ngoài sảnh (Lobby).
 *
 * @param roomId      ID bàn chơi
 * @param roomCode    mã định danh phòng (ví dụ: R1)
 * @param roomName    tên phòng (ví dụ: Bàn 1)
 * @param status      trạng thái bàn: WAITING / PLAYING / RESULT
 * @param playerCount số người chơi hiện tại trong bàn
 * @param maxPlayers  số người chơi tối đa
 * @param betAmount   mức cược cơ bản của bàn
 */
public record RoomSummary(
        long roomId,
        String roomCode,
        String roomName,
        String status,
        int playerCount,
        int maxPlayers,
        BigDecimal betAmount
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
