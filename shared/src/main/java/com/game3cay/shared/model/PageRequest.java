package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * Yêu cầu dữ liệu phân trang (lịch sử đấu, bảng xếp hạng).
 *
 * @param page     số thứ tự trang (bắt đầu từ 0)
 * @param pageSize số phần tử trên mỗi trang (1 đến 100)
 */
public record PageRequest(
        int page,
        int pageSize
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
