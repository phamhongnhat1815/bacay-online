package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Thông tin cược của 1 người chơi trong ván — dùng trong {@link BetStateSnapshot}.
 */
public record PlayerBetInfo(
        long userId,
        String displayName,
        BigDecimal currentBet,
        boolean folded,
        boolean allIn
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
