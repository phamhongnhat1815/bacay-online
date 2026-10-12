package com.bacay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Snapshot trạng thái đặt cược công khai — gửi kèm {@code BET_UPDATED}.
 * Không chứa bài của ai.
 */
public record BetStateSnapshot(
        String roundId,
        /** userId của người đến lượt đặt cược. */
        Long currentTurnUserId,
        /** Mức cược cao nhất hiện tại trong vòng. */
        BigDecimal currentHighBet,
        /** Tổng pot chính. */
        BigDecimal mainPot,
        /** Danh sách side pot (rỗng nếu chưa có ALL_IN). */
        List<SidePot> sidePots,
        /** Thông tin đặt cược từng người (công khai). */
        List<PlayerBetInfo> playerBets,
        long stateVersion
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Thông tin đặt cược công khai của một người trong vòng tự. */
    public record PlayerBetInfo(
            long userId,
            String displayName,
            BigDecimal currentBet,
            boolean folded,
            boolean allIn
    ) implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }
}
