package com.bacay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Trạng thái công khai của ván chơi — gửi kèm {@code GAME_UPDATED}.
 * Không chứa bài của bất kỳ ai.
 *
 * <p><b>Vòng đời ván (phase):</b>
 * <pre>
 * DEALING  → sau khi START_GAME, server chia bài (DEAL_CARD riêng từng người)
 * BETTING  → vòng tố: mỗi người lần lượt RAISE/CALL/FOLD/ALL_IN
 * SHOWDOWN → tất cả còn lại đặt bằng nhau → so bài
 * FINISHED → ROUND_RESULT đã gửi
 * CANCELLED→ hủy ván (không đủ người, lỗi nghiêm trọng)
 * </pre>
 */
public record GameStateSnapshot(
        String roundId,
        /** Phase hiện tại: DEALING / BETTING / SHOWDOWN / FINISHED / CANCELLED */
        String phase,
        /** userId đến lượt đặt cược; null nếu không phải phase BETTING. */
        Long currentTurnUserId,
        /** userId của những người chưa FOLD và chưa rời phòng. */
        List<Long> activePlayers,
        /** Số người đã FOLD trong vòng này. */
        int foldedCount,
        /** Tăng mỗi khi trạng thái thay đổi — client bỏ qua bản cũ hơn. */
        long stateVersion
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
