package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * Yêu cầu cập nhật thông tin hồ sơ người chơi.
 *
 * @param displayName tên hiển thị mới (tùy chọn)
 * @param email       email mới (tùy chọn)
 * @param avatarUrl   đường dẫn ảnh đại diện mới (tùy chọn)
 */
public record UpdateProfileRequest(
        String displayName,
        String email,
        String avatarUrl
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
