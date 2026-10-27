package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * Yêu cầu tìm kiếm và phân trang danh sách người dùng cho Admin.
 *
 * @param query        từ khóa tìm kiếm (theo username, displayName, hoặc email)
 * @param statusFilter bộ lọc trạng thái ("ALL", "ACTIVE", "BLOCKED")
 * @param page         chỉ số trang (bắt đầu từ 0)
 * @param pageSize     kích thước mỗi trang (1 đến 100)
 */
public record AdminUserFilterRequest(
        String query,
        String statusFilter,
        int page,
        int pageSize
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public AdminUserFilterRequest {
        if (page < 0) {
            page = 0;
        }
        if (pageSize < 1 || pageSize > 100) {
            pageSize = 20;
        }
    }
}
