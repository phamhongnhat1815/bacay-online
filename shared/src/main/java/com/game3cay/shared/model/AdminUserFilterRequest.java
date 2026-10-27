package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;

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
