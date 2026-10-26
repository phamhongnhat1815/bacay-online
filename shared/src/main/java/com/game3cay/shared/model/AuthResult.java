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
    String message,
    String role) implements Serializable {

  private static final long serialVersionUID = 1L;

  public AuthResult(
      boolean success,
      Long userId,
      String username,
      String displayName,
      BigDecimal balance,
      ErrorCode errorCode,
      String message) {
    this(
        success, userId, username, displayName,
        balance, errorCode, message, "PLAYER");
  }
}