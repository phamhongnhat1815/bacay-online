package com.game3cay.server.game;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Ngữ cảnh khởi tạo một ván chơi Ba Cây.
 *
 * @param roundId         UUID duy nhất của ván (do Server/Khánh sinh trước)
 * @param roomId          ID phòng chơi
 * @param playerIds       Danh sách userId người chơi theo thứ tự ghế (2-8 người)
 * @param betAmount       Mức cược cơ sở (ante)
 * @param initialBalances Bản đồ số dư ban đầu của từng người chơi (tuỳ chọn)
 */
public record RoundContext(
        String roundId,
        long roomId,
        List<Long> playerIds,
        BigDecimal betAmount,
        Map<Long, BigDecimal> initialBalances
) {
    public RoundContext(String roundId, long roomId, List<Long> playerIds, BigDecimal betAmount) {
        this(roundId, roomId, playerIds, betAmount, Map.of());
    }

    public RoundContext {
        playerIds = List.copyOf(playerIds);
        initialBalances = initialBalances == null ? Map.of() : Map.copyOf(initialBalances);

        if (roundId == null || roundId.isBlank()
                || roomId <= 0
                || playerIds.size() < 2
                || playerIds.size() > 8
                || playerIds.stream().anyMatch(id -> id <= 0)
                || new HashSet<>(playerIds).size() != playerIds.size()
                || betAmount == null
                || betAmount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Dữ liệu khởi tạo ván không hợp lệ"
            );
        }
    }
}