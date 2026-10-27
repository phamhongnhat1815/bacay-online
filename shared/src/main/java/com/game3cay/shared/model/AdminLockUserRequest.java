package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * Yêu cầu khóa hoặc mở khóa tài khoản người dùng từ Admin.
 *
 * @param userId  mã ID người dùng cần can thiệp
 * @param blocked true nếu muốn khóa (BLOCKED), false nếu muốn mở khóa (ACTIVE)
 */
public record AdminLockUserRequest(
        long userId,
        boolean blocked
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
