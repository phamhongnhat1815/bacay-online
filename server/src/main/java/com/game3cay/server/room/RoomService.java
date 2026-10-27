package com.game3cay.server.room;

import com.game3cay.server.bus.BUSException;
import com.game3cay.server.bus.UserDTO;
import com.game3cay.shared.model.CreateRoomRequest;
import com.game3cay.shared.model.JoinRoomRequest;
import com.game3cay.shared.model.RoomSnapshot;
import com.game3cay.shared.model.RoomSummary;

import java.util.List;

public interface RoomService {
    List<RoomSummary> listRooms() throws BUSException;

    RoomSnapshot create(UserDTO actor, CreateRoomRequest request) throws BUSException;

    RoomSnapshot join(UserDTO actor, JoinRoomRequest request) throws BUSException;

    void leave(long userId) throws BUSException;

    RoomSnapshot setReady(long userId, boolean ready) throws BUSException;

    void chat(long userId, String content) throws BUSException;

    void checkStart(long userId) throws BUSException;

    void onDisconnected(long userId);

    void onConnectionLost(long userId);
}
