package com.game3cay.shared.model;

import java.io.Serializable;

public record RoomChatEvent(long roomId, ChatMessage message)
        implements Serializable {

    private static final long serialVersionUID = 1L;
}