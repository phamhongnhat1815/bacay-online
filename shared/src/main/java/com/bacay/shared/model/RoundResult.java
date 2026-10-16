package com.bacay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Kết quả của một ván bài — gửi qua mạng trong {@code ROUND_RESULT}.
 * Payload công khai: tất cả người chơi đều nhận được.
 *
 * <p>{@link com.bacay.server.bus.GameBUS#saveResult} dùng object này để lưu:
 * {@code games}, {@code game_players}, {@code game_cards}, {@code point_bets},
 * {@code side_pots}, {@code side_pot_players}, {@code transactions}.
 */
public record RoundResult(
        /** roundId UUID — khớp với {@code games.round_id}, dùng để chống ghi lặp. */
        String roundId,

        /** Kết quả từng người (đã sắp xếp theo rank từ cao xuống). */
        List<PlayerResult> playerResults,

        /**
         * Lịch sử toàn bộ lượt đặt cược trong ván (theo thứ tự sequenceNo tăng dần).
         * Được GameBUS ghi vào bảng {@code point_bets}.
         * Rỗng nếu ván bị CANCELLED trước khi ai đặt.
         */
        List<BetRecord> betHistory,

        /**
         * Danh sách side pot nếu có ALL_IN nhiều mức.
         * Được GameBUS ghi vào bảng {@code side_pots} + {@code side_pot_players}.
         * Rỗng nếu không có ALL_IN.
         */
        List<SidePot> sidePots,

        /**
         * Trạng thái kết thúc ván.
         * FINISHED = có kết quả thật; CANCELLED = ván bị hủy (không cộng điểm).
         */
        String roundStatus,

        /** Thời gian kết thúc lấy từ đồng hồ server (epoch milli). */
        long endedAtMillis
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
