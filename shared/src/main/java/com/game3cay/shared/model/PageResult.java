package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public record PageResult<T extends Serializable>(
        List<T> items,
        int page,
        int pageSize,
        long totalItems,
        int totalPages
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public static int calcTotalPages(long totalItems, int pageSize) {
        if (pageSize <= 0) return 0;
        return (int) Math.ceil((double) totalItems / pageSize);
    }

    public static <T extends Serializable> PageResult<T> empty(int page, int pageSize) {
        return new PageResult<>(List.of(), page, pageSize, 0, 0);
    }
}
