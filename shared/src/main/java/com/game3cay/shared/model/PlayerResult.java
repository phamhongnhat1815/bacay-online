package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

public record PlayerResult(
        long userId,
        String username,
        String displayName,

        Hand hand,

        int rank,

        String result,

        BigDecimal profit
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
