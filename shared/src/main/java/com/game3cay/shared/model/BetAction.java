package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * Hành động tố trong vòng cược Ba Cây.
 *
 * <ul>
 *   <li>{@code ANTE} — Đóng tiền cửa bắt buộc khi bắt đầu ván (Engine tự xử lý, client không gửi)</li>
 *   <li>{@code RAISE} — Tố thêm (kèm {@code raiseAmount &gt; 0})</li>
 *   <li>{@code CALL} — Theo (đủ mức cược cao nhất)</li>
 *   <li>{@code FOLD} — Úp bài</li>
 *   <li>{@code ALL_IN} — Cược toàn bộ số dư còn lại</li>
 * </ul>
 *
 * Mapping với {@code GameActionRequest.action} trong Protocol.md Mục 3.5.
 */
public enum BetAction implements Serializable {

    ANTE,
    RAISE,
    CALL,
    FOLD,
    ALL_IN;

    @Serial
    private static final long serialVersionUID = 1L;
}
