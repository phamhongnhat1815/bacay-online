package com.bacay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Một lượt hành động đặt cược trong ván — phần tử của {@link RoundResult#betHistory()}.
 *
 * <p>Được {@link com.bacay.server.bus.GameBUS#saveResult} ghi vào bảng {@code point_bets}.
 * GameEngine tự sinh danh sách này theo thứ tự thao tác (sequenceNo bắt đầu từ 1).
 */
public record BetRecord(
        /** userId thực hiện action. */
        long userId,

        /**
         * userId đối thủ cụ thể nếu luật yêu cầu (nullable).
         * Trong Ba Cây cơ bản, tố là toàn bàn nên thường null.
         */
        Long targetUserId,

        /** RAISE / CALL / FOLD / ALL_IN / ANTE */
        BetAction action,

        /** Mức cược sau action (tổng tích lũy người này đã bỏ vào ván). */
        BigDecimal raisedPoint,

        /** Số điểm tăng thêm so với lượt trước (chỉ có nghĩa với RAISE / ALL_IN). */
        BigDecimal raiseAmount,

        /** Hệ số nhân trước action (lúc bắt đầu lượt này). */
        BigDecimal multiplierBefore,

        /** Hệ số nhân sau action. */
        BigDecimal multiplierAfter,

        /** Thứ tự action trong ván, bắt đầu từ 1 (ANTE của người đầu = 1). */
        int sequenceNo,

        /** Epoch milli từ đồng hồ server. */
        long createdAtMillis
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
