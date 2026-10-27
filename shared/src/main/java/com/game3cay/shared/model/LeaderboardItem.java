package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Một dòng xếp hạng trên Bảng Xếp Hạng người chơi.
 *
 * @param rank        thứ hạng hiện tại (1, 2, 3...)
 * @param userId      ID người chơi
 * @param displayName tên hiển thị công khai
 * @param totalPoints tổng điểm thắng kiếm được trong game
 * @param totalWins   tổng số ván thắng
 * @param totalGames  tổng số ván đã chơi
 * @param totalLosses tổng số ván thua
 */
public record LeaderboardItem(
        int rank,
        long userId,
        String displayName,
        BigDecimal totalPoints,
        int totalWins,
        int totalGames,
        int totalLosses
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
