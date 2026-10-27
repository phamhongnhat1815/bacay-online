package com.game3cay.server.bus;

import java.math.BigDecimal;

/**
 * Data Transfer Object đại diện cho thông tin tài khoản người dùng tại tầng BUS.
 * <p>
 * Tuyệt đối không chứa mật khẩu thô, chuỗi hash hay salt.
 *
 * @param userId      mã định danh người dùng
 * @param username    tên đăng nhập
 * @param displayName tên hiển thị công khai
 * @param email       địa chỉ email
 * @param avatarUrl   đường dẫn ảnh đại diện
 * @param balance     số dư điểm hiện tại
 * @param status      trạng thái tài khoản (ACTIVE / BLOCKED)
 * @param role        vai trò tài khoản (USER / ADMIN / MODERATOR)
 * @param totalGames  tổng số ván bài đã tham gia
 * @param totalWins   tổng số ván thắng
 * @param totalLosses tổng số ván thua
 */
public record UserDTO(
        long userId,
        String username,
        String displayName,
        String email,
        String avatarUrl,
        BigDecimal balance,
        String status,
        String role,
        int totalGames,
        int totalWins,
        int totalLosses
) {
    /** Constructor tương thích ngược 10 tham số (mặc định role = "USER"). */
    public UserDTO(
            long userId,
            String username,
            String displayName,
            String email,
            String avatarUrl,
            BigDecimal balance,
            String status,
            int totalGames,
            int totalWins,
            int totalLosses
    ) {
        this(userId, username, displayName, email, avatarUrl, balance, status, "USER", totalGames, totalWins, totalLosses);
    }
}
