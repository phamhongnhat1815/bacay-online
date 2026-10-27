package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * Yêu cầu đổi mật khẩu tài khoản người dùng.
 *
 * @param currentPassword mật khẩu hiện tại
 * @param newPassword     mật khẩu mới
 */
public record ChangePasswordRequest(
        String currentPassword,
        String newPassword
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
