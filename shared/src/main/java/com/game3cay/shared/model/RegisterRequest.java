package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;

public record RegisterRequest(
        String username,
        String password,
        String displayName,
        String email
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
