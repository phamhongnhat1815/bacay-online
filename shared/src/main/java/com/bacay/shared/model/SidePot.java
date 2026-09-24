package com.bacay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Side pot khi có nhiều người ALL_IN ở các mức khác nhau.
 * Được tính bởi {@code SidePotCalculator} ở server — gửi trong {@code BetStateSnapshot}
 * và {@code RoundResult}.
 */
public record SidePot(
        /** Tổng điểm trong pot này. */
        BigDecimal amount,
        /** Danh sách userId được phép tranh pot này (đã góp đủ). */
        List<Long> eligiblePlayerIds
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
