package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Chi tiết trạng thái và tay bài 3 lá của một người chơi trong bàn phục vụ chế độ soi bài (God-View) của Admin.
 *
 * @param userId      mã định danh người chơi
 * @param username    tên đăng nhập
 * @param displayName tên hiển thị
 * @param seat        số ghế trong bàn (1 - 8)
 * @param balance     số dư điểm hiện tại
 * @param currentBet  tổng tiền đã cược trong ván hiện tại
 * @param status      trạng thái ("WAITING", "READY", "ACTIVE", "FOLDED", "ALL_IN")
 * @param cards       danh sách 3 lá bài tẩy trên tay (rỗng nếu chưa chia bài)
 * @param evaluation  đánh giá bài ("Sáp A", "Liêng Q-K-A", "9 Điểm", "Đã úp bài", "Chờ ván mới")
 */
public record AdminLivePlayerHand(
        long userId,
        String username,
        String displayName,
        int seat,
        BigDecimal balance,
        BigDecimal currentBet,
        String status,
        List<Card> cards,
        String evaluation
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
