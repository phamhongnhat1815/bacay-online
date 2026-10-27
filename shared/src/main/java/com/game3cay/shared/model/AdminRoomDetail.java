package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Thông tin chi tiết một bàn chơi phục vụ giám sát thời gian thực trên giao diện Admin.
 *
 * @param roomId      mã số bàn (1 đến 20)
 * @param roomName    tên hiển thị của bàn
 * @param status      trạng thái ("WAITING", "PLAYING", "RESULT")
 * @param ante        mức cược cơ bản của bàn
 * @param currentPot  tổng tiền cược đang có trên bàn
 * @param playerCount số lượng người chơi đang ngồi tại bàn
 * @param maxPlayers  số lượng ghế tối đa (8)
 * @param playerNames danh sách tên hiển thị của những người trong bàn
 */
public record AdminRoomDetail(
        long roomId,
        String roomName,
        String status,
        BigDecimal ante,
        BigDecimal currentPot,
        int playerCount,
        int maxPlayers,
        List<String> playerNames
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
