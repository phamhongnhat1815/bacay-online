package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Trạng thái riêng tư của một người chơi — payload của {@code PLAYER_STATE} chỉ gửi riêng cho người đó.
 *
 * <p>Chứa 3 lá bài bí mật và số dư cá nhân.
 */
public record PlayerStateSnapshot(
        String roundId,
        List<Card> myCards,
        BigDecimal myBalance,
        BigDecimal myCurrentBet,
        long stateVersion
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
