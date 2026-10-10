package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * Giai đoạn hiện tại của một ván bài trong Engine (RAM-only).
 *
 * <ul>
 *   <li>{@code WAITING} — Ván đã tạo nhưng chưa đủ điều kiện bắt đầu</li>
 *   <li>{@code DEALING} — Đang chia bài</li>
 *   <li>{@code BETTING} — Vòng tố đang diễn ra</li>
 *   <li>{@code SHOWDOWN} — Lộ bài và xác định người thắng</li>
 *   <li>{@code FINISHED} — Ván kết thúc bình thường</li>
 *   <li>{@code CANCELLED} — Ván bị hủy (không đủ người, người chơi rời)</li>
 * </ul>
 *
 * Mapping với {@code phase} field trong {@code GameStateSnapshot} (Protocol.md Mục 3.5).
 */
public enum GamePhase implements Serializable {

    WAITING,
    DEALING,
    BETTING,
    SHOWDOWN,
    FINISHED,
    CANCELLED;

    @Serial
    private static final long serialVersionUID = 1L;
}
