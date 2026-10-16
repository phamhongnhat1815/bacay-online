package com.bacay.server.bus;

import java.math.BigDecimal;

/**
 * DTO thông tin người dùng trả từ BUS layer.
 * Không chứa password_hash, salt hoặc bất kỳ thông tin nhạy cảm nào.
 *
 * <p>Khánh dùng {@code UserDTO} để tạo {@code AuthResult} / {@code ProfileResult}
 * trong gói {@code shared.model} rồi gửi cho client.
 */
public record UserDTO(
        long userId,
        String username,
        String displayName,
        String email,
        String avatarUrl,
        BigDecimal balance,
        /** ACTIVE / BLOCKED */
        String status,
        int totalGames,
        int totalWins,
        int totalLosses
) {}
