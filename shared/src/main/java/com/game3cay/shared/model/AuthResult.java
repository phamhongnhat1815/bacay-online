package com.game3cay.shared.model;

import java.io.Serializable;
import java.math.BigDecimal;

import com.game3cay.shared.network.ErrorCode;

public record AuthResult(
        boolean success,
        Long userId,
        String username,
        String displayName,
        BigDecimal balance,
        ErrorCode errorCode,
        String message
) implements Serializable {
    private static final long serialVersionUID = 1L;
}