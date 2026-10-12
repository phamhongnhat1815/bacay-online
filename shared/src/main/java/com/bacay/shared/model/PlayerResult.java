package com.bacay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Kết quả của một người chơi trong ván bài.
 * Là phần tử trong {@link RoundResult#playerResults()}.
 */
public record PlayerResult(
        long userId,
        String username,
        String displayName,
        /** Bài đã lật (null nếu người đó FOLD trước khi showdown). */
        Hand hand,
        /** Xếp hạng trong ván (1 = thắng, 0 = hòa hoặc không xếp). */
        int rank,
        /** WIN / LOSE / DRAW / FOLD */
        String result,
        /** Lãi/lỗ trong ván này (dương = thắng, âm = thua). */
        BigDecimal profit
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
