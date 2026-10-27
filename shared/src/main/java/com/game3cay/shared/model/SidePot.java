package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public record SidePot(
        BigDecimal amount,
        List<Long> eligiblePlayerIds
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
