package com.bacay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Kết quả phân trang — payload của {@code HISTORY_RESULT} và {@code LEADERBOARD_RESULT}.
 *
 * <p>Dùng generic {@code T} để tái sử dụng cho mọi loại danh sách phân trang.
 * Cả Java Serialization và mọi thư viện JSON đều hỗ trợ generic record.
 *
 * @param <T> kiểu phần tử trong danh sách (phải Serializable)
 */
public record PageResult<T extends Serializable>(
        List<T> items,
        int page,
        int pageSize,
        long totalItems,
        int totalPages
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Tính totalPages từ totalItems và pageSize. */
    public static int calcTotalPages(long totalItems, int pageSize) {
        if (pageSize <= 0) return 0;
        return (int) Math.ceil((double) totalItems / pageSize);
    }

    /** Tạo kết quả trang rỗng. */
    public static <T extends Serializable> PageResult<T> empty(int page, int pageSize) {
        return new PageResult<>(List.of(), page, pageSize, 0, 0);
    }
}
