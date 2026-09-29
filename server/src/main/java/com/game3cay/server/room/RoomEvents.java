package com.game3cay.server.room;

import java.util.List;

import com.game3cay.shared.model.ChatMessage;
import com.game3cay.shared.model.RoomSnapshot;

public interface RoomEvents {
    void roomUpdated(RoomSnapshot room, List<Long> recipients);

    void chatMessage(
            long roomId,
            ChatMessage message,
            List<Long> recipients
    );
}