package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Một dòng ghi nhận trong sổ cái kép phục vụ kiểm toán tài chính và biến động số dư.
 *
 * @param roundId       mã ván bài hoặc mã giao dịch can thiệp
 * @param userId        mã định danh người dùng
 * @param username      tên đăng nhập
 * @param delta         biến động số dư (+ hoặc -)
 * @param balanceBefore số dư trước khi biến động
 * @param balanceAfter  số dư sau khi biến động
 * @param timestamp     thời điểm giao dịch (epoch milliseconds)
 */
public record AdminLedgerItem(
        String roundId,
        long userId,
        String username,
        BigDecimal delta,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        long timestamp
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
