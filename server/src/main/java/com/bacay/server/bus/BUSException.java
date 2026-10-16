package com.bacay.server.bus;

import com.bacay.shared.network.ErrorCode;

/**
 * Exception từ BUS layer — chứa {@link ErrorCode} để handler
 * của Khánh chuyển thẳng thành {@code ERROR} packet gửi cho client.
 */
public class BUSException extends Exception {

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
