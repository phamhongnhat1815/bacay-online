package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Một pot phụ (side pot) sinh ra khi có người chơi chọn ALL_IN với mức điểm thấp hơn người khác.
 *
 * @param amount            tổng số điểm trong pot này
 * @param eligiblePlayerIds danh sách ID người chơi có quyền tranh pot này (đã góp đủ)
 */
public record SidePot(
        BigDecimal amount,
        List<Long> eligiblePlayerIds
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
