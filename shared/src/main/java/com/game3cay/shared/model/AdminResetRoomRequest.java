package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * Yêu cầu cưỡng chế giải tán hoặc hủy ván bài bị kẹt trong bàn chơi.
 *
 * @param roomId mã số bàn
 * @param reason lý do giải tán bàn
 */
public record AdminResetRoomRequest(
        long roomId,
        String reason
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
