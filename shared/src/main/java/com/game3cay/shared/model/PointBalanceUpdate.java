package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

public record PointBalanceUpdate(
        long userId, BigDecimal balance, String transferId
) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
}

