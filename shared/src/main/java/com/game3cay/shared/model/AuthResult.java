package com.game3cay.shared.model;

import com.game3cay.shared.network.ErrorCode;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Kết quả xử lý xác thực (đăng nhập hoặc đăng ký) từ Server gửi về Client.
 *
 * @param success     true nếu xác thực thành công, false nếu thất bại
 * @param userId      ID tài khoản (chỉ có khi đăng nhập thành công)
 * @param username    tên tài khoản
 * @param displayName tên hiển thị công khai
 * @param balance     số dư điểm hiện tại
 * @param role        vai trò người dùng (USER / ADMIN / MODERATOR)
 * @param errorCode   mã lỗi nếu thất bại
 * @param message     thông điệp mô tả kết quả
 */
public record AuthResult(
        boolean success,
        Long userId,
        String username,
        String displayName,
        BigDecimal balance,
        String role,
        ErrorCode errorCode,
        String message
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Constructor tương thích ngược không có tham số role (mặc định là "USER"). */
    public AuthResult(
            boolean success,
            Long userId,
            String username,
            String displayName,
            BigDecimal balance,
            ErrorCode errorCode,
            String message
    ) {
        this(success, userId, username, displayName, balance, "USER", errorCode, message);
    }
}
