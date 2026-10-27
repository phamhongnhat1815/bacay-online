package com.game3cay.server.room;

import com.game3cay.shared.model.ChatMessage;
import com.game3cay.shared.model.RoomSnapshot;
import com.game3cay.shared.model.RoomSummary;
import com.game3cay.shared.network.Packet;

import java.util.List;

public interface RoomEvents {
    void roomUpdated(RoomSnapshot room, List<Long> recipients);

    void chatMessage(long roomId, ChatMessage message, List<Long> recipients);

    default void packet(Packet packet, List<Long> recipients) {}

    default void lobbyUpdated(List<RoomSummary> rooms) {}
}
