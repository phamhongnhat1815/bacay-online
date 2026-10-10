package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Ghi lại 1 hành động cược trong lịch sử ván (lưu vào bảng {@code point_bets} bởi Nhật).
 *
 * <p>Field mapping với DB (Nhật cần đọc):
 * <ul>
 *   <li>{@code userId} → {@code point_bets.user_id}</li>
 *   <li>{@code action} → {@code point_bets.action}</li>
 *   <li>{@code raisedPoint} → {@code point_bets.raised_point} — điểm gốc trước nhân</li>
 *   <li>{@code raiseAmount} → {@code point_bets.raise_amount} — số tiền thực tế bỏ thêm</li>
 *   <li>{@code multiplierBefore} → {@code point_bets.multiplier_before}</li>
 *   <li>{@code multiplierAfter} → {@code point_bets.multiplier_after}</li>
 *   <li>{@code sequenceNo} → {@code point_bets.sequence_no} — thứ tự trong ván (1, 2, 3...)</li>
 *   <li>{@code createdAtMillis} → {@code point_bets.created_at} (epoch ms → TIMESTAMP)</li>
 * </ul>
 */
public record BetRecord(
        long userId,
        Long targetUserId,          // null trong Ba Cây (dùng ở game khác có 'tố người')
        BetAction action,
        BigDecimal raisedPoint,     // Điểm cược tại thời điểm action (thường = currentBet sau action)
        BigDecimal raiseAmount,     // Tiền thực sự bỏ thêm trong action này
        BigDecimal multiplierBefore,// Hệ số nhân trước action (mặc định 1 nếu không dùng)
        BigDecimal multiplierAfter, // Hệ số nhân sau action (mặc định 1 nếu không dùng)
        int sequenceNo,             // Thứ tự hành động trong ván (bắt đầu từ 1)
        long createdAtMillis        // System.currentTimeMillis() tại thời điểm action
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
