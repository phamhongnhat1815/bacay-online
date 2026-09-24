package com.bacay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Kết quả của một ván bài — gửi qua mạng trong {@code ROUND_RESULT}.
 * Payload công khai: tất cả người chơi đều nhận được.
 */
public record RoundResult(
        /** roundId để đối chiếu — phải khớp với ván đang chơi. */
        String roundId,
        /** Kết quả từng người chơi (đã sắp xếp theo rank từ cao xuống). */
        List<PlayerResult> playerResults,
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
