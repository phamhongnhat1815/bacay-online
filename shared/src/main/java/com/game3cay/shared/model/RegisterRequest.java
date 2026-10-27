package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * Yêu cầu đăng ký tài khoản người chơi mới.
 *
 * @param username    tên tài khoản (3-50 ký tự chữ, số hoặc gạch dưới)
 * @param password    mật khẩu (8-128 ký tự)
 * @param displayName tên hiển thị (tùy chọn, tối đa 100 ký tự)
 * @param email       địa chỉ email (tùy chọn, tối đa 100 ký tự)
 */
public record RegisterRequest(
        String username,
        String password,
        String displayName,
        String email
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
