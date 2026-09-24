package com.bacay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Trạng thái riêng của người chơi — gửi kèm {@code PLAYER_STATE} (chỉ riêng người nhận).
 * Chứa bài của người nhận — KHÔNG bao giờ broadcast.
 */
public record PlayerStateSnapshot(
        String roundId,
        /** Bài của người nhận (đúng 3 lá). */
        List<Card> myCards,
        /** Số dư hiện tại của người nhận. */
        BigDecimal myBalance,
        /** Tổng số tiền người nhận đã đặt trong ván này (bao gồm ante). */
        BigDecimal myCurrentBet,
        /** true nếu người nhận đã FOLD. */
        boolean folded,
        /** true nếu người nhận đã ALL_IN. */
        boolean allIn,
        long stateVersion
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
