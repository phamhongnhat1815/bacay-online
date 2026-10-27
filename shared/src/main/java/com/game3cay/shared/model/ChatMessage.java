package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

public record ChatMessage(
        Long senderId,

        String senderDisplayName,
        String content,

        String type,

        long timestampMillis
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public static ChatMessage system(String content) {
        return new ChatMessage(null, null, content, "SYSTEM",
                Instant.now().toEpochMilli());
    }
}
