package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * Trạng thái một ván bài lưu vào DB (bảng {@code games.status}).
 *
 * <ul>
 *   <li>{@code WAITING} — Ván đã tạo, chờ người chơi sẵn sàng</li>
 *   <li>{@code PLAYING} — Ván đang diễn ra</li>
 *   <li>{@code FINISHED} — Ván kết thúc bình thường, có kết quả thắng/thua</li>
 *   <li>{@code CANCELLED} — Ván bị hủy, không cộng điểm</li>
 * </ul>
 *
 * Khác {@link GamePhase} (trạng thái nội bộ Engine — chi tiết hơn):
 * {@code RoundStatus} là giá trị thô map sang cột {@code games.status VARCHAR} trong MySQL.
 */
public enum RoundStatus implements Serializable {

    WAITING,
    PLAYING,
    FINISHED,
    CANCELLED;

    @Serial
    private static final long serialVersionUID = 1L;
}
