package com.game3cay.server.game.exception;

import com.game3cay.shared.network.ErrorCode;

/**
 * Exception dành riêng cho Game Engine.
 *
 * <p>Bọc {@link ErrorCode} để Khánh (GameHandler) có thể map
 * ngay sang {@code Packet(ERROR, ErrorPayload)} gửi cho client.
 *
 * <p><b>Nguyên tắc quan trọng:</b> Khi {@code GameException} được ném,
 * trạng thái ván ({@code GameState}) phải <em>không thay đổi</em>.
 * Mọi validation phải hoàn thành <em>trước</em> khi bất kỳ field nào được cập nhật.
 */
public class GameException extends RuntimeException {

    private final ErrorCode errorCode;

    public GameException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /** Mã lỗi chuẩn dùng để gửi {@code ErrorPayload} cho client. */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
