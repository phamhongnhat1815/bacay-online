package com.game3cay.server.network;

import com.game3cay.server.bus.*;
import com.game3cay.server.room.RoomManager;
import com.game3cay.shared.model.*;
import com.game3cay.shared.network.*;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class ClientHandler implements Runnable {
  private static final int HEARTBEAT_INTERVAL =
      RuntimeSettings.integer(
          "server.properties", "heartbeat.intervalSeconds", 5, 1, 60);

  private static final int HEARTBEAT_TIMEOUT =
      RuntimeSettings.integer(
          "server.properties", "heartbeat.timeoutSeconds", 15, 2, 300);

  private final Socket socket;
  private final UserBUS users;
  private final SessionRegistry sessions;
  private final RoomManager rooms;
  private final GameBUS games;
  private final AdminService admins;

  private final java.util.Map<String, Packet> replies =
      java.util.Collections.synchronizedMap(
          new java.util.LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(
                java.util.Map.Entry<String, Packet> entry) {
              return size() > 128;
            }
          });

  private final AtomicBoolean closed = new AtomicBoolean();
  private final AtomicLong lastPong = new AtomicLong();
  private final AtomicReference<String> waitingPong = new AtomicReference<>();

  private final ExecutorService sender =
      new ThreadPoolExecutor(
          1, 1, 0, TimeUnit.MILLISECONDS,
          new ArrayBlockingQueue<>(128),
          task -> daemon(task, "server-sender"));

  private final ScheduledExecutorService heartbeat =
      Executors.newSingleThreadScheduledExecutor(
          task -> daemon(task, "server-heartbeat"));

  private ObjectOutputStream output;
  private volatile UserDTO user;

  public ClientHandler(
      Socket socket, UserBUS users,
      SessionRegistry sessions, RoomManager rooms) {
    this(socket, users, sessions, rooms, null);
  }

  public ClientHandler(
      Socket socket, UserBUS users, SessionRegistry sessions,
      RoomManager rooms, GameBUS games) {
    this(socket, users, sessions, rooms, games, null);
  }

  public ClientHandler(
      Socket socket, UserBUS users, SessionRegistry sessions,
      RoomManager rooms, GameBUS games, AdminService admins) {
    this.socket = socket;
    this.users = users;
    this.sessions = sessions;
    this.rooms = rooms;
    this.games = games;
    this.admins = admins;
  }

  @Override
  public void run() {
    try (socket) {
      socket.setSoTimeout(5000);
      output = new ObjectOutputStream(socket.getOutputStream());
      output.flush();

      ObjectInputStream input = PacketIO.openInput(socket);
      socket.setSoTimeout(0);
      lastPong.set(System.nanoTime());

      send(new Packet(
          PacketType.SERVER_NOTICE, null,
          "Đã kết nối server M1–M6, protocol v2"));

      heartbeat.scheduleAtFixedRate(
          this::tick, 0, HEARTBEAT_INTERVAL, TimeUnit.SECONDS);

      while (!closed.get()) {
        handle(PacketIO.read(input));
      }
    } catch (IOException | ClassNotFoundException | RuntimeException e) {
      System.out.println("Đóng kết nối: " + e.getClass().getSimpleName());
    } finally {
      close();
      clearSession();
    }
  }

  private void tick() {
    if (System.nanoTime() - lastPong.get()
        > TimeUnit.SECONDS.toNanos(HEARTBEAT_TIMEOUT)) {
      close();
      return;
    }

    Packet ping = Packet.request(PacketType.PING, null);
    if (waitingPong.compareAndSet(null, ping.getRequestId())) {
      send(ping);
    }
  }

  private void handle(Packet packet) {
    if (packet.getType() == PacketType.PONG) {
      String expected = waitingPong.get();
      if (expected != null
          && expected.equals(packet.getRequestId())
          && waitingPong.compareAndSet(expected, null)) {
        lastPong.set(System.nanoTime());
      }
      return;
    }

    String id = packet.getRequestId();

    if (id == null) {
      error(null, ErrorCode.INVALID_REQUEST, "Request phải có requestId");
      return;
    }

    Packet previous = replies.get(id);
    if (previous != null) {
      send(previous);
      return;
    }

    try {
      switch (packet.getType()) {
        case ECHO_REQUEST -> ack(id);

        case REGISTER -> {
          if (user != null) {
            throw fail(
                ErrorCode.INVALID_STATE,
                "Hãy đăng xuất trước khi đăng ký");
          }

          var r = (RegisterRequest) packet.getData();
          var created =
              userBUS().register(
                  r.username(), r.password(), r.displayName(), r.email());
          auth(id, created);
        }

        case LOGIN -> {
          if (user != null) {
            throw fail(
                ErrorCode.ALREADY_LOGGED_IN,
                "Kết nối này đã đăng nhập");
          }

          var r = (AuthRequest) packet.getData();
          var authenticated =
              userBUS().authenticate(r.username(), r.password());

          if (!sessions.bind(authenticated.userId(), this)) {
            throw fail(
                ErrorCode.ALREADY_LOGGED_IN,
                "Tài khoản đang đăng nhập ở kết nối khác");
          }

          user = authenticated;
          auth(id, user);
        }

        case LOGOUT -> {
          requireUser();
          clearSession();
          ack(id);
        }

        case ROOM_LIST -> {
          requireUser();
          send(new Packet(
              PacketType.ROOM_LIST_RESULT, id, rooms.listRooms()));
        }

        case CREATE_ROOM ->
            send(new Packet(
                PacketType.ROOM_UPDATED,
                id,
                rooms.create(
                    userBUS().getProfile(requireUser().userId()),
                    (CreateRoomRequest) packet.getData())));

        case JOIN_ROOM ->
            send(new Packet(
                PacketType.ROOM_UPDATED,
                id,
                rooms.join(
                    userBUS().getProfile(requireUser().userId()),
                    (JoinRoomRequest) packet.getData())));

        case WATCH_ROOM -> {
          var actor = userBUS().getProfile(requireUser().userId());
          boolean inspect =
              admins != null && "ADMIN".equals(admins.roleOf(actor.userId()));

          var watched =
              rooms.watch(actor, (JoinRoomRequest) packet.getData(), inspect);

          send(new Packet(PacketType.ROOM_UPDATED, id, watched));
          rooms.sendWatchState(actor.userId());
        }

        case ADMIN_REQUEST -> {
          long actor = requireUser().userId();

          if (admins == null) {
            throw fail(ErrorCode.FORBIDDEN, "Chưa bật quản trị");
          }

          var r = (AdminPayload.Request) packet.getData();
          admins.requireAdmin(actor);

          if (r.action() != AdminPayload.Action.LIST_USERS) {
            if (r.action() == AdminPayload.Action.CREDIT) {
              rooms.creditWhenIdle(
                  r.userId(), () -> admins.change(actor, id, r));
            } else {
              admins.change(actor, id, r);
            }

            if (r.action() == AdminPayload.Action.CREDIT) {
              var fresh = userBUS().getProfile(r.userId());

              sessions.broadcast(
                  java.util.List.of(r.userId()),
                  Packet.serverPush(
                      PacketType.PROFILE_RESULT,
                      new ProfileResult(
                          fresh.userId(), fresh.username(), fresh.displayName(),
                          fresh.email(), fresh.avatarUrl(), fresh.balance(),
                          fresh.totalGames(), fresh.totalWins(), fresh.totalLosses())));
            }

            if (r.action() == AdminPayload.Action.RESET_PASSWORD
                || r.action() == AdminPayload.Action.SET_BLOCKED
                    && "BLOCKED".equals(r.value())) {
              sessions.disconnectUser(r.userId());
            }
          }

          var response =
              admins.list(
                  actor, r.page(),
                  r.action() == AdminPayload.Action.LIST_USERS
                      ? r.value() : "");

          send(new Packet(
              PacketType.ADMIN_RESULT,
              id,
              new AdminPayload.Response(
                  r.action() == AdminPayload.Action.LIST_USERS
                      ? response.message()
                      : "Đã thực hiện và lưu lịch sử quản trị",
                  response.accounts(),
                  response.page(),
                  response.totalPages())));
        }

        case LEAVE_ROOM -> {
          rooms.leave(requireUser().userId());
          ack(id);
        }

        case SET_READY ->
            send(new Packet(
                PacketType.ROOM_UPDATED,
                id,
                rooms.setReady(
                    requireUser().userId(), (Boolean) packet.getData())));

        case CHANGE_ROOM_BET ->
            send(new Packet(
                PacketType.ROOM_UPDATED,
                id,
                rooms.changeBet(
                    requireUser().userId(),
                    (java.math.BigDecimal) packet.getData())));

        case CHAT_MESSAGE -> {
          var actor = requireUser();

          if (!(packet.getData() instanceof ChatMessage message)
              || !"TEXT".equals(message.type())) {
            throw fail(
                ErrorCode.INVALID_REQUEST,
                "Client chỉ được gửi chat TEXT");
          }

          rooms.chat(actor.userId(), message.content());
          ack(id);
        }

        case START_GAME_REQUEST -> {
          rooms.start(requireUser().userId());
          ack(id);
        }

        case GAME_ACTION -> {
          rooms.action(
              requireUser().userId(),
              (GameActionRequest) packet.getData());
          ack(id);
        }

        case PLAY_AGAIN_VOTE -> {
          rooms.setReady(
              requireUser().userId(), (Boolean) packet.getData());
          ack(id);
        }

        case GET_PROFILE -> profile(id, requireUser().userId());

        case UPDATE_PROFILE -> {
          long uid = requireUser().userId();

          if (rooms.inRoom(uid)) {
            throw fail(
                ErrorCode.INVALID_STATE,
                "Rời phòng trước khi sửa hồ sơ");
          }

          var r = (UpdateProfileRequest) packet.getData();
          userBUS().updateProfile(
              uid, r.displayName(), r.email(), r.avatarUrl());
          user = userBUS().getProfile(uid);
          profile(id, uid);
        }

        case CHANGE_PASSWORD -> {
          var r = (ChangePasswordRequest) packet.getData();
          userBUS().changePassword(
              requireUser().userId(),
              r.currentPassword(), r.newPassword());
          ack(id);
        }

        case GET_HISTORY -> {
          long uid = requireUser().userId();
          var r = (PageRequest) packet.getData();

          if (games == null) {
            throw fail(
                ErrorCode.INVALID_STATE, "Chưa cấu hình dữ liệu ván");
          }

          send(new Packet(
              PacketType.HISTORY_RESULT,
              id,
              games.getHistory(uid, r.page(), r.pageSize())));
        }

        case GET_LEADERBOARD -> {
          requireUser();
          var r = (PageRequest) packet.getData();

          if (games == null) {
            throw fail(
                ErrorCode.INVALID_STATE, "Chưa cấu hình dữ liệu ván");
          }

          send(new Packet(
              PacketType.LEADERBOARD_RESULT,
              id,
              games.getLeaderboard(r.page(), r.pageSize())));
        }

        default ->
            error(
                id, ErrorCode.INVALID_REQUEST,
                "Loại thông điệp không được gửi từ client");
      }
    } catch (BUSException e) {
      error(id, e.getErrorCode(), e.getMessage());
    } catch (RuntimeException e) {
      error(id, ErrorCode.INTERNAL_ERROR, "Không thể xử lý yêu cầu");
    }
  }

  private void profile(String id, long uid) throws BUSException {
    var u = userBUS().getProfile(uid);

    send(new Packet(
        PacketType.PROFILE_RESULT,
        id,
        new ProfileResult(
            u.userId(), u.username(), u.displayName(),
            u.email(), u.avatarUrl(), u.balance(),
            u.totalGames(), u.totalWins(), u.totalLosses())));
  }

  private UserBUS userBUS() throws BUSException {
    if (users == null) {
      throw fail(
          ErrorCode.INTERNAL_ERROR,
          "Server chưa được cấu hình dữ liệu tài khoản");
    }
    return users;
  }

  private UserDTO requireUser() throws BUSException {
    if (user == null) {
      throw fail(
          ErrorCode.UNAUTHENTICATED, "Bạn cần đăng nhập trước");
    }
    return user;
  }

  private void auth(String id, UserDTO u) throws BUSException {
    send(new Packet(
        PacketType.AUTH_RESULT,
        id,
        new AuthResult(
            true, u.userId(), u.username(), u.displayName(),
            u.balance(), null, "Thành công",
            admins == null ? "PLAYER" : admins.roleOf(u.userId()))));
  }

  private void clearSession() {
    if (user == null) {
      return;
    }

    UserDTO old = user;
    user = null;

    try {
      rooms.onDisconnected(old.userId());
    } finally {
      sessions.release(old.userId(), this);
    }
  }

  private void ack(String id) {
    send(new Packet(PacketType.ACK, id, null));
  }

  private void error(String id, ErrorCode code, String message) {
    send(new Packet(
        PacketType.ERROR, id, new Packet.ErrorPayload(code, message)));
  }

  private static BUSException fail(ErrorCode code, String message) {
    return new BUSException(code, message);
  }

  public void send(Packet packet) {
    if (packet.getRequestId() != null
        && packet.getType() != PacketType.PING) {
      replies.put(packet.getRequestId(), packet);
    }

    if (closed.get()) {
      return;
    }

    try {
      sender.execute(() -> {
        if (closed.get()) {
          return;
        }

        try {
          if (packet.getType() == PacketType.ADMIN_TABLE_CARDS) {
            var recipient = user;
            if (admins == null || recipient == null) {
              return;
            }

            try {
              admins.requireAdmin(recipient.userId());
            } catch (BUSException denied) {
              return;
            }
          }

          PacketIO.write(output, packet);
        } catch (IOException | RuntimeException e) {
          close();
        }
      });
    } catch (RejectedExecutionException e) {
      close();
    }
  }

  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }

    try {
      socket.close();
    } catch (IOException ignored) {
    }

    heartbeat.shutdownNow();
    sender.shutdownNow();
  }

  private static Thread daemon(Runnable task, String name) {
    Thread thread = new Thread(task, name);
    thread.setDaemon(true);
    return thread;
  }
}