package com.game3cay.shared.model;

import com.game3cay.shared.network.ErrorCode;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

public record AuthResult(
        boolean success,
        Long userId,
        String username,
        String displayName,
        BigDecimal balance,
        String role,
        ErrorCode errorCode,
        String message
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public AuthResult(
            boolean success,
            Long userId,
            String username,
            String displayName,
            BigDecimal balance,
            ErrorCode errorCode,
            String message
    ) {
        this(success, userId, username, displayName, balance, "USER", errorCode, message);
    }
}
