package com.game3cay.server.bus;

import com.game3cay.shared.network.ErrorCode;

/**
 * Ngoại lệ nghiệp vụ ở tầng BUS (Business Service Layer).
 * <p>
 * Mang theo mã lỗi nghiệp vụ {@link ErrorCode} để tầng Network
 * có thể đóng gói trực tiếp thành gói tin phản hồi {@code ERROR} gửi về cho Client.
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
