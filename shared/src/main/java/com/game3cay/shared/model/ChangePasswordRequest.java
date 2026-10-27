package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;

public record ChangePasswordRequest(
        String currentPassword,
        String newPassword
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
