package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Thông số tạo phòng hoặc cập nhật cấu hình bàn chơi.
 *
 * @param roomName   tên phòng/bàn
 * @param minPlayers số người chơi tối thiểu để bắt đầu ván (thường là 2)
 * @param maxPlayers số người chơi tối đa (tối đa 8)
 * @param betAmount  mức cược cơ bản của bàn
 */
public record CreateRoomRequest(
        String roomName,
        int minPlayers,
        int maxPlayers,
        BigDecimal betAmount
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
