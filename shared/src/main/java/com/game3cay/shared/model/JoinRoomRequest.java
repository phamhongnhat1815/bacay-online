package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * Yêu cầu tham gia một bàn chơi cụ thể.
 *
 * @param roomId ID của bàn chơi (từ 1 đến 20)
 */
public record JoinRoomRequest(long roomId) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
