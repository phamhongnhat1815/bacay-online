package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Kết quả hoàn chỉnh của 1 ván Ba Cây.
 *
 * <p>Khánh nhận record này từ {@code GameEngine.finalizeRound()} rồi đóng gói thành
 * {@code Packet(ROUND_RESULT, result)} broadcast cả phòng.
 * Nhật đọc {@code playerResults} và {@code betHistory} để lưu vào DB.
 *
 * <p>Field mapping DB:
 * <ul>
 *   <li>{@code roundId} → {@code games.round_id}</li>
 *   <li>{@code roomId} → {@code games.room_id}</li>
 *   <li>{@code roundStatus} → {@code games.status} ("FINISHED" | "CANCELLED")</li>
 *   <li>{@code endedAtMillis} → {@code games.ended_at}</li>
 *   <li>{@code basePoint} → {@code games.base_point} (= betAmount / ante)</li>
 * </ul>
 */
public record RoundResult(
        String roundId,
        long roomId,
        String roundStatus,                 // "FINISHED" hoặc "CANCELLED"
        long endedAtMillis,
        BigDecimal basePoint,               // = betAmount (ante ban đầu)
        List<PlayerResult> playerResults,   // Kết quả từng người
        List<BetRecord> betHistory,         // Lịch sử toàn bộ action (cho Nhật lưu point_bets)
        List<SidePot> sidePots,             // Rỗng nếu không có ALL_IN
        String cancelReason                 // null nếu FINISHED
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
