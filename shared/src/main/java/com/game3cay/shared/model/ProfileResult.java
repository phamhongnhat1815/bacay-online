package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Dữ liệu hồ sơ người chơi chi tiết.
 *
 * @param userId      ID người chơi
 * @param username    tên đăng nhập
 * @param displayName tên hiển thị
 * @param email       email
 * @param avatarUrl   đường dẫn ảnh đại diện
 * @param balance     số dư điểm hiện tại
 * @param totalGames  tổng số ván đã tham gia
 * @param totalWins   tổng số ván thắng
 * @param totalLosses tổng số ván thua
 */
public record ProfileResult(
        long userId,
        String username,
        String displayName,
        String email,
        String avatarUrl,
        BigDecimal balance,
        int totalGames,
        int totalWins,
        int totalLosses
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
