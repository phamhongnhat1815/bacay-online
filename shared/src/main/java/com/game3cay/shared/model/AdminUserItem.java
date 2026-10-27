package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Thông tin tóm tắt của một người dùng phục vụ hiển thị trên giao diện quản trị.
 *
 * @param userId      mã định danh người dùng
 * @param username    tên đăng nhập
 * @param displayName tên hiển thị công khai
 * @param email       địa chỉ email
 * @param balance     số dư điểm hiện tại
 * @param status      trạng thái tài khoản (ACTIVE / BLOCKED)
 * @param role        vai trò người dùng (USER / ADMIN / MODERATOR)
 * @param totalGames  tổng số ván đã chơi
 * @param totalWins   tổng số ván thắng
 * @param totalLosses tổng số ván thua
 * @param createdAt   thời điểm tạo tài khoản (epoch milliseconds)
 */
public record AdminUserItem(
        long userId,
        String username,
        String displayName,
        String email,
        BigDecimal balance,
        String status,
        String role,
        int totalGames,
        int totalWins,
        int totalLosses,
        long createdAt
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
