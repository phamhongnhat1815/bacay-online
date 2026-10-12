package com.bacay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Một hàng trong bảng xếp hạng — phần tử của {@code PageResult<LeaderboardItem>}
 * trả về trong {@code LEADERBOARD_RESULT}.
 *
 * <p>Sắp xếp server-side: {@code totalPoints DESC} → {@code totalWins DESC} → {@code userId ASC}.
 */
public record LeaderboardItem(
        int rank,
        long userId,
        String displayName,
        /** Tổng điểm tích lũy (tổng profit dương, không trừ thua). */
        BigDecimal totalPoints,
        int totalWins,
        int totalGames,
        int totalLosses
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
