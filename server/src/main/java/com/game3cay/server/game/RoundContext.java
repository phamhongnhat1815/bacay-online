package com.game3cay.server.game;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;

public record RoundContext(
        String roundId,
        long roomId,
        List<Long> playerIds,
        BigDecimal betAmount
) {
    public RoundContext {
        playerIds = List.copyOf(playerIds);

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