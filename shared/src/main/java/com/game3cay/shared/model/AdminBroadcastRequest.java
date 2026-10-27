package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * Yêu cầu phát thông báo toàn hệ thống từ Admin đến tất cả Client đang kết nối.
 *
 * @param message nội dung thông báo
 * @param level   mức độ ưu tiên ("INFO", "WARNING", "URGENT")
 */
public record AdminBroadcastRequest(
        String message,
        String level
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
