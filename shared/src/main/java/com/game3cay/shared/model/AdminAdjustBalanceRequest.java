package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Yêu cầu điều chỉnh số dư điểm (cộng hoặc trừ) của người dùng từ Admin.
 *
 * @param userId mã ID người dùng
 * @param delta  số điểm cần thay đổi (dương để cộng, âm để trừ)
 * @param reason lý do can thiệp số dư (lưu vào sổ cái kiểm toán)
 */
public record AdminAdjustBalanceRequest(
        long userId,
        BigDecimal delta,
        String reason
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
