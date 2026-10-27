package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Payload chia bài gửi riêng cho từng người chơi khi ván bài bắt đầu.
 *
 * @param roundId     mã ván chơi
 * @param cards       danh sách 3 lá bài riêng của người chơi
 * @param dealOrderNo lượt chia bài
 */
public record DealCardPayload(
        String roundId,
        List<Card> cards,
        int dealOrderNo
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
