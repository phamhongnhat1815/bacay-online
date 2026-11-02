package com.game3cay.server.bus;

import com.game3cay.shared.network.ErrorCode;
import java.io.Serial;

public class BUSException extends Exception {
    @Serial
    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    public BUSException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BUSException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
