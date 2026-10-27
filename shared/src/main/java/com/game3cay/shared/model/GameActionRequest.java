package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Yêu cầu thực hiện hành động đặt cược trong lượt của người chơi.
 *
 * @param roundId mã ván chơi hiện tại
 * @param action  hành động đặt cược (CALL, RAISE, ALL_IN, FOLD)
 * @param raiseTo tổng mức điểm muốn cược tới (chỉ áp dụng khi action là RAISE)
 */
public record GameActionRequest(
        String roundId,
        BetAction action,
        BigDecimal raiseTo
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
