package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;

public record UpdateProfileRequest(
        String displayName,
        String email,
        String avatarUrl
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
