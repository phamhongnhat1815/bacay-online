package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Bản chụp số liệu thống kê thời gian thực của toàn bộ hệ thống Ba Cây Online.
 *
 * @param onlineUsers                số lượng tài khoản đang trực tuyến
 * @param activeRooms                số lượng bàn đang có người hoặc đang diễn ra ván bài
 * @param totalRegisteredUsers       tổng số tài khoản người dùng đã đăng ký
 * @param totalBalanceInCirculation tổng số điểm đang lưu hành trong toàn bộ hệ sinh thái
 * @param totalRoundsPlayedToday     tổng số ván bài đã kết thúc trong ngày hôm nay
 */
public record AdminStatsSnapshot(
        long onlineUsers,
        int activeRooms,
        long totalRegisteredUsers,
        BigDecimal totalBalanceInCirculation,
        int totalRoundsPlayedToday
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
