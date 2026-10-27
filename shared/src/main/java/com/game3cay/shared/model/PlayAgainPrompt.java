package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * Lời nhắc chuẩn bị ván mới từ server sau khi kết thúc một ván chơi.
 *
 * @param roundId mã ván vừa kết thúc
 * @param message thông điệp hướng dẫn
 */
public record PlayAgainPrompt(
        String roundId,
        String message
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
