package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;

public record AdminBroadcastRequest(
        String message,
        String level
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
