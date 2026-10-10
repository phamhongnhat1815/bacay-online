package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Kết quả của một người chơi trong ván Ba Cây.
 *
 * <p>Được đưa vào {@link RoundResult} và map với bảng {@code game_players} trong database.
 */
public record PlayerResult(
        long userId,
        String username,
        String displayName,
        Hand hand,              // null nếu FOLD trước showdown
        String handType,        // "SAP" / "LIENG" / "BO_DOI" / "DIEM" / null nếu FOLD
        int rank,               // 1 = nhất, 0 = không xếp hạng (fold/cancelled)
        String result,          // "WIN" / "LOSE" / "DRAW" / "FOLD" / "CANCELLED"
        BigDecimal profit,      // Dương = thắng, âm = thua, 0 = hoàn tiền (CANCELLED)
        int seatNumber
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
