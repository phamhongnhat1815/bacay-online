package com.bacay.server.game;

import com.bacay.shared.network.ErrorCode;

/**
 * Exception từ {@link GameEngine} — chứa {@link ErrorCode} để handler
 * của Khánh chuyển thẳng thành {@code ERROR} packet gửi cho client.
 *
 * <p>Không bao giờ wrapping {@code SQLException} hay lỗi nội bộ hệ thống vào đây.
 * Dùng {@link ErrorCode#INTERNAL_ERROR} cho các trường hợp không lường trước.
 */
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
