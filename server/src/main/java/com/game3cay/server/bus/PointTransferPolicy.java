package com.game3cay.server.bus;

import com.game3cay.shared.network.RuntimeSettings;
import java.math.BigDecimal;

public record PointTransferPolicy(int minCompletedGames, BigDecimal dailyLimit, BigDecimal maxTransfer,
                                  int requestExpirySeconds, int requestsPerMinute, int transfersPerMinute) {
    public PointTransferPolicy {
        if (minCompletedGames < 0 || dailyLimit == null || dailyLimit.signum() <= 0
                || dailyLimit.scale() < 0 || dailyLimit.scale() > 2 || dailyLimit.precision() > 15
                || maxTransfer == null || maxTransfer.signum() <= 0 || maxTransfer.scale() < 0
                || maxTransfer.scale() > 2 || maxTransfer.precision() > 15
                || requestExpirySeconds < 1 || requestsPerMinute < 1
                || transfersPerMinute < 1 || transfersPerMinute > 300) {
            throw new IllegalArgumentException("Cấu hình cho điểm không hợp lệ");
        }
    }

    public PointTransferPolicy(int minCompletedGames, BigDecimal dailyLimit, BigDecimal maxTransfer,
                               int requestExpirySeconds, int requestsPerMinute) {
        this(minCompletedGames, dailyLimit, maxTransfer, requestExpirySeconds, requestsPerMinute, 30);
    }

    public static PointTransferPolicy configured() {
        return new PointTransferPolicy(
                RuntimeSettings.integer("server.properties", "gift.minCompletedGames", 3, 0, 10000),
                new BigDecimal(RuntimeSettings.text("server.properties", "gift.dailyLimit", "2000.00")),
                new BigDecimal(RuntimeSettings.text("server.properties", "gift.maxTransfer", "2000.00")),
                RuntimeSettings.integer("server.properties", "gift.requestExpirySeconds", 120, 1, 3600),
                RuntimeSettings.integer("server.properties", "gift.requestsPerMinute", 3, 1, 60),
                RuntimeSettings.integer("server.properties", "gift.transfersPerMinute", 30, 1, 300));
    }
}
