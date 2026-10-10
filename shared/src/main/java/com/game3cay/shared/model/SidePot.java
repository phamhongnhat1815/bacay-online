package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Đại diện cho một Pot phụ khi có người chơi ALL_IN.
 *
 * <p>Mỗi SidePot chứa số tiền tương ứng và danh sách người chơi đủ điều kiện tranh pot.
 */
public record SidePot(
        BigDecimal amount,
        List<Long> eligiblePlayerIds
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
