package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

public record AdminAdjustBalanceRequest(
        long userId,
        BigDecimal delta,
        String reason
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
