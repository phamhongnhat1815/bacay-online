package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;

public record PageRequest(
        int page,
        int pageSize
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
