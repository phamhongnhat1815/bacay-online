package com.bacay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Tin nhắn chat — dùng cho cả {@code CHAT_MESSAGE} và {@code CHAT_SYSTEM}.
 */
public record ChatMessage(
        /** null nếu là tin hệ thống. */
        Long senderId,
        /** null nếu là tin hệ thống. */
        String senderDisplayName,
        String content,
        /** TEXT, EMOJI, SYSTEM */
        String type,
        /** Thời gian lấy từ đồng hồ server (epoch milli). */
        long timestampMillis
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Factory tiện lợi cho thông báo hệ thống. */
    public static ChatMessage system(String content) {
        return new ChatMessage(null, null, content, "SYSTEM",
                Instant.now().toEpochMilli());
    }
}
