package com.bacay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Một mục lịch sử ván chơi — phần tử của {@code PageResult<GameHistoryItem>}
 * trả về trong {@code HISTORY_RESULT}.
 */
public record GameHistoryItem(
        long gameId,
        String roundId,
        String roomName,
        /** WIN / LOSE / DRAW / FOLD / CANCELLED */
        String result,
        /** Dương = thắng, âm = thua (sau khi trừ ante). */
        BigDecimal profit,
        /** SAP / LIENG / BO_DOI / DIEM / null nếu FOLD/CANCELLED */
        String handType,
        /** Epoch milli từ đồng hồ server. */
        long playedAt,
        /** Số người tham gia ván đó. */
        int playerCount
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
