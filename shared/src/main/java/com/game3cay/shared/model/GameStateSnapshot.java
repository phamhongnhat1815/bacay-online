package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Trạng thái công khai của ván — payload của {@code GAME_UPDATED} broadcast cho cả phòng.
 *
 * <p><b>TUYỆT ĐỐI không chứa bài bí mật hoặc số dư cá nhân.</b>
 */
public record GameStateSnapshot(
        String roundId,
        String phase,
        Long currentTurnUserId,
        List<Long> activePlayers,
        long stateVersion
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
