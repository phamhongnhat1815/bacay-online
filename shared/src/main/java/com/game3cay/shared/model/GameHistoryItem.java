package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Một dòng lịch sử ván bài của người chơi hiển thị trên bảng lịch sử đấu.
 *
 * @param roundDbId   ID tự tăng trong CSDL
 * @param roundId     mã UUID của ván bài
 * @param roomName    tên bàn chơi
 * @param result      kết quả cá nhân (WIN, LOSE, DRAW, FOLD, CANCELLED)
 * @param profit      số điểm thay đổi (+ hoặc -)
 * @param handType    loại bài sở hữu (SAP, LIENG, BO_DOI, DIEM)
 * @param playedAt    thời điểm kết thúc ván (epoch milliseconds)
 * @param playerCount số người tham gia trong ván đó
 */
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
