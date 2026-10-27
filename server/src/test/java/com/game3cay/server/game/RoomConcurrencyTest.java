package com.game3cay.server.game;

import static org.junit.jupiter.api.Assertions.*;

import com.game3cay.server.bus.*;
import com.game3cay.server.room.*;
import com.game3cay.shared.model.*;
import com.game3cay.shared.network.ErrorCode;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class RoomConcurrencyTest {
  UserDTO user(long id) {
    return new UserDTO(
        id, "p" + id, "P" + id, null, null, new BigDecimal("1000.00"), "ACTIVE", 0, 0, 0);
  }

  RoomManager rooms() {
    return new RoomManager(
        new RoomEvents() {
          public void roomUpdated(RoomSnapshot r, List<Long> ids) {}

          public void chatMessage(long id, ChatMessage m, List<Long> ids) {}
        });
  }

  @Test
  void twoJoinsCompeteForLastSeat() throws Exception {
    var rooms = rooms();
    var room = rooms.join(user(1), new JoinRoomRequest(1));
    for (long id = 4; id <= 9; id++) rooms.join(user(id), new JoinRoomRequest(1));
    var gate = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var results = new ArrayList<Future<Boolean>>();
      for (long id = 2; id <= 3; id++) {
        final long uid = id;
        results.add(
            pool.submit(
                () -> {
                  gate.await();
                  try {
                    rooms.join(user(uid), new JoinRoomRequest(room.roomId()));
                    return true;
                  } catch (BUSException e) {
                    assertEquals(ErrorCode.ROOM_FULL, e.getErrorCode());
                    return false;
                  }
                }));
      }
      gate.countDown();
      int success = 0;
      for (var f : results) if (f.get(3, TimeUnit.SECONDS)) success++;
      assertEquals(1, success);
      assertEquals(8, rooms.listRooms().getFirst().playerCount());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void twentyTablesRemainInNumericOrderAfterLastPlayerLeaves() throws Exception {
    var rooms = rooms();
    assertEquals(20, rooms.listRooms().size());
    for (int i = 1; i <= 20; i++) {
      var table = rooms.listRooms().get(i - 1);
      assertEquals(i, table.roomId());
      assertEquals("Bàn " + i, table.roomName());
      assertEquals(0, table.playerCount());
    }
    rooms.join(user(1), new JoinRoomRequest(20));
    rooms.leave(1);
    assertEquals(20, rooms.listRooms().size());
    var fresh = rooms.join(user(2), new JoinRoomRequest(20));
    assertEquals(2, fresh.ownerUserId());
    assertEquals(1, fresh.players().getFirst().seatNumber());
  }

  @Test
  void eightSeatsAndOwnerTransfer() throws Exception {
    var rooms = rooms();
    var room = rooms.join(user(1), new JoinRoomRequest(1));
    for (long id = 2; id <= 8; id++) rooms.join(user(id), new JoinRoomRequest(room.roomId()));
    assertEquals(
        ErrorCode.ROOM_FULL,
        assertThrows(
                BUSException.class, () -> rooms.join(user(9), new JoinRoomRequest(room.roomId())))
            .getErrorCode());
    rooms.leave(1);
    assertEquals(2L, rooms.setReady(2, true).ownerUserId());
    assertEquals(7, rooms.listRooms().getFirst().playerCount());
  }
}
