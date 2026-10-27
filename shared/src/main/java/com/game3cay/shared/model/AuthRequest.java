package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * Yêu cầu đăng nhập từ phía Client.
 *
 * @param username tên tài khoản đăng nhập
 * @param password mật khẩu thô của người dùng
 */
public record AuthRequest(String username, String password) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
