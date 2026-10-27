package com.game3cay.server.game;

import com.game3cay.shared.network.ErrorCode;

public class GameEngineException extends Exception {
  private final ErrorCode errorCode;

  public GameEngineException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public GameEngineException(ErrorCode errorCode, String message, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }
}
