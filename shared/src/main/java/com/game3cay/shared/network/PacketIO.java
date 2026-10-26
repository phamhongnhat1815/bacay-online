package com.game3cay.shared.network;

import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;

import com.game3cay.shared.model.AdminPayload;
import com.game3cay.shared.model.AuthRequest;
import com.game3cay.shared.model.AuthResult;
import com.game3cay.shared.model.BetStateSnapshot;
import com.game3cay.shared.model.ChangePasswordRequest;
import com.game3cay.shared.model.ChatMessage;
import com.game3cay.shared.model.CreateRoomRequest;
import com.game3cay.shared.model.DealCardPayload;
import com.game3cay.shared.model.GameActionRequest;
import com.game3cay.shared.model.GameHistoryItem;
import com.game3cay.shared.model.GameStartInfo;
import com.game3cay.shared.model.GameStateSnapshot;
import com.game3cay.shared.model.JoinRoomRequest;
import com.game3cay.shared.model.LeaderboardItem;
import com.game3cay.shared.model.PageRequest;
import com.game3cay.shared.model.PageResult;
import com.game3cay.shared.model.PlayAgainPrompt;
import com.game3cay.shared.model.PlayerStateSnapshot;
import com.game3cay.shared.model.ProfileResult;
import com.game3cay.shared.model.RegisterRequest;
import com.game3cay.shared.model.RoomChatEvent;
import com.game3cay.shared.model.RoomSnapshot;
import com.game3cay.shared.model.RoomSummary;
import com.game3cay.shared.model.RoundResult;
import com.game3cay.shared.model.UpdateProfileRequest;

public final class PacketIO {
  private PacketIO() {}

  public static ObjectInputStream openInput(Socket socket) throws IOException {
    var input = new ObjectInputStream(socket.getInputStream());

    input.setObjectInputFilter(
        ObjectInputFilter.Config.createFilter(
            "maxdepth=24;maxarray=4096;"
                + "com.game3cay.shared.model.*;"
                + "com.game3cay.shared.network.Packet;"
                + "com.game3cay.shared.network.Packet$ErrorPayload;"
                + "com.game3cay.shared.network.PacketType;"
                + "com.game3cay.shared.network.ErrorCode;"
                + "java.lang.Enum;java.lang.String;java.lang.Number;"
                + "java.lang.Long;java.lang.Boolean;java.lang.Object;"
                + "java.math.BigDecimal;java.math.BigInteger;"
                + "java.util.ArrayList;java.util.CollSer;"
                + "java.util.ImmutableCollections$List12;"
                + "java.util.ImmutableCollections$ListN;!*"));

    return input;
  }

  public static Packet read(ObjectInputStream input)
      throws IOException, ClassNotFoundException {
    if (!(input.readObject() instanceof Packet packet)) {
      throw new IOException("Dữ liệu không phải Packet");
    }

    validate(packet);
    return packet;
  }

  public static void write(ObjectOutputStream output, Packet packet)
      throws IOException {
    validate(packet);

    synchronized (output) {
      output.reset();
      output.writeObject(packet);
      output.flush();
    }
  }

  private static boolean text(String value, int max) {
    return value != null && !value.isBlank() && value.length() <= max;
  }

  private static boolean optional(String value, int max) {
    return value == null || value.length() <= max;
  }

  private static void validate(Packet packet) throws IOException {
    if (packet == null
        || packet.getType() == null
        || packet.getProtocolVersion() != Packet.VERSION
        || (packet.getRequestId() != null
            && !text(packet.getRequestId(), 64))) {
      throw new IOException("Packet sai định dạng hoặc phiên bản");
    }

    Object data = packet.getData();

    boolean valid =
        switch (packet.getType()) {
          case PING, PONG ->
              data == null || data instanceof String s && s.length() <= 1000;

          case ECHO_REQUEST, SERVER_NOTICE ->
              data instanceof String s && text(s, 1000);

          case ACK, LOGOUT, ROOM_LIST, LEAVE_ROOM,
              START_GAME_REQUEST, GET_PROFILE -> data == null;

          case LOGIN ->
              data instanceof AuthRequest a
                  && text(a.username(), 50)
                  && text(a.password(), 128);

          case REGISTER ->
              data instanceof RegisterRequest a
                  && text(a.username(), 50)
                  && text(a.password(), 128)
                  && optional(a.displayName(), 100)
                  && optional(a.email(), 100);

          case AUTH_RESULT -> data instanceof AuthResult;

          case ERROR ->
              data instanceof Packet.ErrorPayload e
                  && e.code() != null
                  && text(e.message(), 500);

          case CREATE_ROOM ->
              data instanceof CreateRoomRequest r
                  && text(r.roomName(), 100)
                  && r.minPlayers() >= 2
                  && r.maxPlayers() >= r.minPlayers()
                  && r.maxPlayers() <= 8
                  && r.betAmount() != null
                  && r.betAmount().signum() > 0
                  && r.betAmount().precision() <= 15
                  && r.betAmount().scale() >= 0
                  && r.betAmount().scale() <= 2;

          case JOIN_ROOM, WATCH_ROOM ->
              data instanceof JoinRoomRequest r && r.roomId() > 0;

          case SET_READY -> data instanceof Boolean;

          case CHANGE_ROOM_BET ->
              data instanceof java.math.BigDecimal amount
                  && amount.signum() > 0
                  && amount.scale() >= 0
                  && amount.scale() <= 2
                  && amount.precision() <= 15;

          case ROOM_UPDATED ->
              data instanceof RoomSnapshot r
                  && r.players() != null
                  && r.players().size() <= 8;

          case ROOM_LIST_RESULT ->
              data instanceof List<?> list
                  && list.size() <= 100
                  && list.stream().allMatch(RoomSummary.class::isInstance);

          case CHAT_MESSAGE ->
              data instanceof ChatMessage c
                      && c.content() != null
                      && c.content().length() <= 2000
                      && ("TEXT".equals(c.type())
                          || "EMOJI".equals(c.type())
                          || "SYSTEM".equals(c.type()))
                  || data instanceof RoomChatEvent e
                      && validChatEvent(e)
                      && "TEXT".equals(e.message().type());

          case CHAT_SYSTEM ->
              data instanceof RoomChatEvent e
                  && validChatEvent(e)
                  && "SYSTEM".equals(e.message().type());

          case START_GAME ->
              data instanceof GameStartInfo g
                  && g.roomId() > 0
                  && text(g.roundId(), 64);

          case DEAL_CARD ->
              data instanceof DealCardPayload d
                  && text(d.roundId(), 64)
                  && d.cards() != null
                  && d.cards().size() == 3;

          case GAME_ACTION ->
              data instanceof GameActionRequest g
                  && text(g.roundId(), 64)
                  && g.action() != null;

          case GAME_UPDATED ->
              data instanceof GameStateSnapshot g
                  && text(g.roundId(), 64)
                  && g.activePlayers() != null
                  && g.activePlayers().size() <= 8;

          case PLAYER_STATE ->
              data instanceof PlayerStateSnapshot p
                  && text(p.roundId(), 64)
                  && p.myCards() != null
                  && p.myCards().size() == 3;

          case BET_UPDATED ->
              data instanceof BetStateSnapshot b
                  && b.playerBets() != null
                  && b.playerBets().size() <= 8;

          case ROUND_RESULT ->
              data instanceof RoundResult r
                  && r.playerResults() != null
                  && r.playerResults().size() <= 8
                  && r.betHistory() != null
                  && r.betHistory().size() <= 1024;

          case PLAY_AGAIN_PROMPT -> data instanceof PlayAgainPrompt;
          case PLAY_AGAIN_VOTE -> data instanceof Boolean;

          case GET_HISTORY, GET_LEADERBOARD -> data instanceof PageRequest;

          case HISTORY_RESULT ->
              data instanceof PageResult<?> p
                  && p.items() != null
                  && p.items().size() <= 100
                  && p.items().stream().allMatch(GameHistoryItem.class::isInstance);

          case LEADERBOARD_RESULT ->
              data instanceof PageResult<?> p
                  && p.items() != null
                  && p.items().size() <= 100
                  && p.items().stream().allMatch(LeaderboardItem.class::isInstance);

          case PROFILE_RESULT -> data instanceof ProfileResult;

          case UPDATE_PROFILE ->
              data instanceof UpdateProfileRequest p
                  && optional(p.displayName(), 100)
                  && optional(p.email(), 100)
                  && optional(p.avatarUrl(), 255);

          case CHANGE_PASSWORD ->
              data instanceof ChangePasswordRequest p
                  && text(p.currentPassword(), 128)
                  && text(p.newPassword(), 128);

          case ADMIN_REQUEST ->
              data instanceof AdminPayload.Request r
                  && r.action() != null
                  && r.page() >= 0
                  && r.page() <= 1_000_000
                  && optional(r.value(), 128)
                  && (r.amount() == null
                      || r.amount().precision() <= 15
                          && r.amount().scale() >= 0
                          && r.amount().scale() <= 2);

          case ADMIN_RESULT ->
              data instanceof AdminPayload.Response r
                  && r.accounts() != null
                  && r.accounts().size() <= 20;

          case ADMIN_TABLE_CARDS ->
              data instanceof AdminPayload.TableCards r
                  && r.roomId() > 0
                  && text(r.roundId(), 64)
                  && r.players() != null
                  && r.players().size() <= 8
                  && r.players().stream()
                      .allMatch(p -> p.cards() != null && p.cards().size() == 3);

          case MUTE_PLAYER -> data instanceof Long uid && uid > 0;
        };

    if (!valid) {
      throw new IOException("Payload không hợp lệ cho " + packet.getType());
    }
  }

  private static boolean validChatEvent(RoomChatEvent event) {
    ChatMessage c = event.message();

    return event.roomId() > 0
        && c != null
        && text(c.content(), 500)
        && c.timestampMillis() > 0
        && ("SYSTEM".equals(c.type())
            || "TEXT".equals(c.type())
                && c.senderId() != null
                && c.senderId() > 0
                && text(c.senderDisplayName(), 100));
  }
}