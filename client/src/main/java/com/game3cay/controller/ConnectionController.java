package com.game3cay.controller;

import com.game3cay.gui.*;
import com.game3cay.network.*;
import com.game3cay.shared.model.*;
import com.game3cay.shared.network.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.swing.*;
import javax.swing.Timer;

public final class ConnectionController implements PacketListener {
  private final MainFrame view;
  private final Timer timeout, maintenance;

  private SocketClient client;
  private AuthResult user;
  private RoomSnapshot room;

  private record Pending(
      String id, PacketType type, long roomId, boolean silent) {}

  private Pending pending;
  private Packet queuedAuth;
  private AuthRequest afterRegister;
  private String connectedHost;
  private int connectedPort;
  private boolean closing, profileDialogOpen;
  private long lastRooms, lastProfile;

  private DataDialog dataDialog;
  private AdminDialog adminDialog;
  private AdminPayload.TableCards inspection;
  private boolean watching;

  private final List<RoomChatEvent> enteringChat = new ArrayList<>();

  public ConnectionController(MainFrame view) {
    this.view = view;

    timeout =
        new Timer(
            12000,
            e -> disconnect(
                "Game chưa phản hồi. Vui lòng đăng nhập lại để tiếp tục."));
    timeout.setRepeats(false);

    maintenance = new Timer(1000, e -> maintain());
    maintenance.start();

    view.onSubmit(this::submitAuth);

    view.onLogout(() -> {
      if (watching || view.confirmLeave()) {
        request(PacketType.LOGOUT, null);
      }
    });

    view.lobby().onJoin(id -> {
      if (user != null && room == null) {
        chooseTable(id);
      }
    });

    view.room().onLeave(() -> {
      if (room != null && (watching || view.confirmLeave())) {
        request(PacketType.LEAVE_ROOM, null);
      }
    });

    view.room().onReady(this::ready);
    view.room().onChangeBet(
        amount -> request(PacketType.CHANGE_ROOM_BET, amount));
    view.room().onStart(
        () -> request(PacketType.START_GAME_REQUEST, null));

    view.room().onSend(() -> {
      String text = view.room().messageText();

      if (text.isBlank() || text.length() > 500) {
        view.showMessage("Tin nhắn từ 1 đến 500 ký tự.");
        return;
      }

      request(
          PacketType.CHAT_MESSAGE,
          new ChatMessage(null, null, text, "TEXT", 0));
    });

    view.room().game().onAction(
        a -> request(PacketType.GAME_ACTION, a));

    view.onProfile(() -> request(PacketType.GET_PROFILE, null));
    view.onHistory(() -> openData(true));
    view.onLeaderboard(() -> openData(false));
    view.onChangePassword(
        r -> request(PacketType.CHANGE_PASSWORD, r));

    view.onClose(() -> {
      if (!watching && !view.confirmLeave()) {
        return;
      }

      closing = true;
      maintenance.stop();
      timeout.stop();

      if (client != null) {
        client.disconnect();
      }

      queuedAuth = null;
      afterRegister = null;
      view.dispose();
    });

    updateEnabled();
  }

  private void chooseTable(long id) {
    Object[] choices = {"Vào chơi", "Xem bàn", "Hủy"};

    int choice =
        JOptionPane.showOptionDialog(
            view,
            "Vào chơi khi bàn đang chờ, hoặc xem cả ván đang diễn ra.",
            "Bàn " + id,
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null, choices, choices[1]);

    if (choice == 0 || choice == 1) {
      request(
          choice == 0 ? PacketType.JOIN_ROOM : PacketType.WATCH_ROOM,
          new JoinRoomRequest(id));
    }
  }

  private void updateAdminMenu() {
    if (user == null || !"ADMIN".equals(user.role())) {
      view.setJMenuBar(null);
    } else {
      var bar = new JMenuBar();
      var menu = new JMenu("Quản trị");
      var open = new JMenuItem("Tài khoản và kiểm tra bài");

      open.addActionListener(e -> openAdmin());
      menu.add(open);
      bar.add(menu);
      view.setJMenuBar(bar);
    }
    view.revalidate();
  }

  private void openAdmin() {
    if (user == null
        || !"ADMIN".equals(user.role())
        || pending != null) {
      return;
    }

    if (adminDialog != null) {
      adminDialog.dispose();
    }

    adminDialog = new AdminDialog(view);
    adminDialog.onRequest(
        r -> request(PacketType.ADMIN_REQUEST, r));

    if (inspection != null) {
      adminDialog.showCards(inspection);
    }

    adminDialog.setVisible(true);
    adminDialog.load();
  }

  private void clearInspection() {
    inspection = null;
    if (adminDialog != null) {
      adminDialog.clearCards();
    }
  }

  private void submitAuth() {
    if (user != null || pending != null || queuedAuth != null) {
      return;
    }

    char[] chars = view.getPassword();
    char[] confirmation = view.getConfirmPassword();

    try {
      String username = view.getUsername();
      String password = new String(chars);

      if (username.isBlank()
          || username.length() > 50
          || password.isBlank()
          || password.length() > 128) {
        view.showMessage("Nhập tài khoản và mật khẩu hợp lệ.");
        return;
      }

      if (view.isRegisterMode()) {
        if (!username.matches("[a-zA-Z0-9_]{3,50}")) {
          view.showMessage("Tài khoản: 3–50 chữ không dấu, số hoặc _.");
          return;
        }
        if (password.length() < 8) {
          view.showMessage("Mật khẩu cần ít nhất 8 ký tự.");
          return;
        }
        if (!Arrays.equals(chars, confirmation)) {
          view.showMessage("Hai mật khẩu không khớp.");
          return;
        }
        if (view.getDisplayName().length() > 100
            || view.getEmail().length() > 100) {
          view.showMessage("Tên hiển thị và email tối đa 100 ký tự.");
          return;
        }
        if (!view.getEmail().isBlank()
            && !view.getEmail().matches(
                "[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
          view.showMessage("Email chưa hợp lệ.");
          return;
        }

        afterRegister = new AuthRequest(username, password);
        queuedAuth =
            Packet.request(
                PacketType.REGISTER,
                new RegisterRequest(
                    username, password,
                    optional(view.getDisplayName()),
                    optional(view.getEmail())));
      } else {
        queuedAuth =
            Packet.request(
                PacketType.LOGIN,
                new AuthRequest(username, password));
      }

      view.clearPasswords();
      view.showMessage("Đang vào game…");
      updateEnabled();
      timeout.restart();

      if (client != null
          && (!view.getHost().equals(connectedHost)
              || view.getPort() != connectedPort)) {
        SocketClient old = client;
        client = null;
        old.disconnect();
      }

      if (client != null && client.isConnected()) {
        sendQueuedAuth();
      } else {
        client = new SocketClient(this);
        connectedHost = view.getHost();
        connectedPort = view.getPort();
        client.connect(connectedHost, connectedPort);
      }
    } finally {
      Arrays.fill(chars, '\0');
      Arrays.fill(confirmation, '\0');
    }
  }

  private static String optional(String s) {
    return s.isBlank() ? null : s;
  }

  private void sendQueuedAuth() {
    Packet p = queuedAuth;
    queuedAuth = null;
    if (p != null) {
      send(p, false);
    }
  }

  private void ready() {
    if (room == null
        || user == null
        || !"WAITING".equals(room.status())) {
      return;
    }

    room.players().stream()
        .filter(p -> p.userId() == user.userId())
        .findFirst()
        .ifPresent(p -> request(PacketType.SET_READY, !p.ready()));
  }

  private void request(PacketType type, Object data) {
    request(type, data, false);
  }

  private void request(PacketType type, Object data, boolean silent) {
    if (pending != null || queuedAuth != null) {
      if (!silent) {
        view.showMessage("Đợi thao tác hiện tại hoàn tất rồi thử lại.");
      }
      return;
    }

    if (client == null || !client.isConnected()) {
      disconnect("Kết nối đã ngắt. Đăng nhập lại để tiếp tục.");
      return;
    }

    send(Packet.request(type, data), silent);
  }

  private void send(Packet p, boolean silent) {
    long target =
        p.getData() instanceof JoinRoomRequest j
            ? j.roomId()
            : room == null ? -1 : room.roomId();

    pending =
        new Pending(
            p.getRequestId(), p.getType(), target, silent);

    if (p.getType() == PacketType.JOIN_ROOM
        || p.getType() == PacketType.WATCH_ROOM) {
      enteringChat.clear();
    }

    if (!client.send(p)) {
      disconnect("Không gửi được thao tác. Vui lòng đăng nhập lại.");
      return;
    }

    updateEnabled();
    timeout.restart();
  }

  private void maintain() {
    if (user == null
        || pending != null
        || room != null
        || profileDialogOpen
        || adminDialog != null && adminDialog.isVisible()) {
      return;
    }

    long now = System.nanoTime();

    if (now - lastRooms >= TimeUnit.SECONDS.toNanos(10)) {
      loadRooms(true);
      return;
    }

    if (now - lastProfile >= TimeUnit.SECONDS.toNanos(5)) {
      lastProfile = now;
      request(PacketType.GET_PROFILE, null, true);
    }
  }

  private void loadRooms(boolean silent) {
    lastRooms = System.nanoTime();
    request(PacketType.ROOM_LIST, null, silent);
  }

  private void openData(boolean history) {
    if (user == null || pending != null) {
      return;
    }
    if (dataDialog != null) {
      dataDialog.dispose();
    }

    dataDialog =
        new DataDialog(
            view, history ? "Lịch sử của tôi" : "Bảng xếp hạng");

    dataDialog.onLoad(page -> {
      if (pending != null) {
        dataDialog.failure("Đợi thao tác hiện tại rồi tải lại.");
        return;
      }

      request(
          history ? PacketType.GET_HISTORY : PacketType.GET_LEADERBOARD,
          new PageRequest(page, 20));
    });

    dataDialog.setVisible(true);
    request(
        history ? PacketType.GET_HISTORY : PacketType.GET_LEADERBOARD,
        new PageRequest(0, 20));
  }

  @Override
  public void onConnected(SocketClient source) {
    onUi(source, this::sendQueuedAuth);
  }

  @Override
  public void onPacketReceived(SocketClient source, Packet packet) {
    onUi(source, () -> handle(packet));
  }

  @Override
  public void onDisconnected(SocketClient source, String reason) {
    onUi(
        source,
        () -> disconnect(
            user == null
                ? "Chưa thể vào game. Kiểm tra máy chủ và đăng nhập lại."
                : "Kết nối bị gián đoạn. Đăng nhập lại để tiếp tục."));
  }

  private void handle(Packet packet) {
    PacketType type = packet.getType();
    String id = packet.getRequestId();
    Object data = packet.getData();

    if (type == PacketType.PING || type == PacketType.SERVER_NOTICE) {
      return;
    }

    if (id == null) {
      if (user == null) {
        return;
      }

      if (type == PacketType.PROFILE_RESULT) {
        var p = (ProfileResult) data;
        if (p.userId() == user.userId()) {
          view.setBalance(p.balance());
        }
        return;
      }

      if (type == PacketType.ADMIN_TABLE_CARDS) {
        var p = (AdminPayload.TableCards) data;

        if ("ADMIN".equals(user.role())
            && watching && room != null
            && p.roomId() == room.roomId()
            && view.room().game().accepts(p.roundId())) {
          inspection = p;

          if (adminDialog != null) {
            adminDialog.showCards(p);
          }
        }
        return;
      }

      if (type == PacketType.ROOM_LIST_RESULT) {
        setRooms(data);
        return;
      }

      if (type == PacketType.ROOM_UPDATED) {
        applyRoom((RoomSnapshot) data, false);
        return;
      }

      if (type == PacketType.CHAT_MESSAGE
          || type == PacketType.CHAT_SYSTEM) {
        var event = (RoomChatEvent) data;

        if (room != null && event.roomId() == room.roomId()) {
          view.room().appendChat(event.message());
        } else if (pending != null
            && (pending.type() == PacketType.JOIN_ROOM
                || pending.type() == PacketType.WATCH_ROOM)
            && enteringChat.size() < 16) {
          enteringChat.add(event);
        }
        return;
      }

      if (room == null) {
        return;
      }

      var game = view.room().game();

      switch (type) {
        case START_GAME -> {
          var start = (GameStartInfo) data;
          if (start.roomId() == room.roomId()) {
            clearInspection();
            game.start(start.roundId(), user.userId());
            view.showMessage(
                "Ván mới đã bắt đầu. Chúc bạn may mắn!");
          }
        }

        case GAME_UPDATED -> game.game((GameStateSnapshot) data);
        case BET_UPDATED -> game.bets((BetStateSnapshot) data);

        case PLAYER_STATE -> {
          var state = (PlayerStateSnapshot) data;
          if (game.accepts(state.roundId())) {
            game.mine(state);
            view.setBalance(state.myBalance());
          }
        }

        case ROUND_RESULT -> {
          game.result((RoundResult) data);
          lastProfile = System.nanoTime();
          view.showMessage(
              "Kết quả hiện " + RoundResult.DISPLAY_SECONDS
                  + " giây; sau đó bạn chọn có sẵn sàng tiếp.");
        }

        default -> {}
      }
      return;
    }

    if (pending == null || !pending.id().equals(id)) {
      return;
    }

    Pending request = pending;

    if (type == PacketType.ERROR) {
      finish();

      var error = (Packet.ErrorPayload) data;
      afterRegister = null;

      if (error.code() == ErrorCode.UNAUTHENTICATED) {
        signedOut();
      }

      if ((error.code() == ErrorCode.NOT_IN_ROOM
              || error.code() == ErrorCode.ROOM_NOT_FOUND)
          && room != null) {
        room = null;
        view.showLobby();
        updateEnabled();
      }

      if (request.type() == PacketType.ADMIN_REQUEST
          && adminDialog != null) {
        adminDialog.failure(error.message());
      }

      if (dataDialog != null
          && dataDialog.isDisplayable()
          && (request.type() == PacketType.GET_HISTORY
              || request.type() == PacketType.GET_LEADERBOARD)) {
        dataDialog.failure(error.message());
      }

      view.showMessage(
          error.message() == null
              ? "Chưa thực hiện được thao tác này."
              : error.message());

      return;
    }

    if (type == PacketType.AUTH_RESULT
        && (request.type() == PacketType.LOGIN
            || request.type() == PacketType.REGISTER)) {
      var result = (AuthResult) data;
      finish();

      if (!result.success()) {
        afterRegister = null;
        view.showMessage(result.message());
        return;
      }

      if (request.type() == PacketType.REGISTER) {
        var login = afterRegister;
        afterRegister = null;
        view.registrationCompleted();
        view.showMessage(
            "Tạo tài khoản thành công. Đang vào sảnh…");

        if (login != null) {
          request(PacketType.LOGIN, login);
        }
        return;
      }

      if (result.userId() == null || result.userId() <= 0) {
        disconnect("Không xác nhận được tài khoản. Đăng nhập lại.");
        return;
      }

      user = result;
      watching = false;
      updateAdminMenu();

      view.showSignedIn(
          result.displayName() == null
              ? result.username() : result.displayName(),
          result.userId());

      view.setBalance(result.balance());
      view.showLobby();

      lastProfile = System.nanoTime();
      loadRooms(false);
      updateEnabled();
      view.showMessage("Chào mừng bạn đến sảnh Ba Cây.");
      return;
    }

    if (request.type() == PacketType.ROOM_LIST
        && type == PacketType.ROOM_LIST_RESULT) {
      finish();
      setRooms(data);
      return;
    }

    if (request.type() == PacketType.ADMIN_REQUEST
        && type == PacketType.ADMIN_RESULT) {
      finish();
      if (adminDialog != null && adminDialog.isDisplayable()) {
        adminDialog.showResponse((AdminPayload.Response) data);
      }
      return;
    }

    if ((request.type() == PacketType.JOIN_ROOM
            || request.type() == PacketType.WATCH_ROOM
            || request.type() == PacketType.SET_READY
            || request.type() == PacketType.CHANGE_ROOM_BET)
        && type == PacketType.ROOM_UPDATED) {
      var snapshot = (RoomSnapshot) data;

      if (snapshot.roomId() != request.roomId()) {
        disconnect(
            "Không đồng bộ được bàn chơi. Vui lòng đăng nhập lại.");
        return;
      }

      finish();

      boolean entering =
          request.type() == PacketType.JOIN_ROOM
              || request.type() == PacketType.WATCH_ROOM;

      if (entering) {
        watching = request.type() == PacketType.WATCH_ROOM;
        clearInspection();
      }

      applyRoom(snapshot, entering);

      view.showMessage(
          request.type() == PacketType.WATCH_ROOM
              ? "Đang xem bàn. "
                  + ("ADMIN".equals(user.role())
                      ? "Mở Quản trị → Bài trên bàn đang xem để kiểm tra bài."
                      : "Bài của người chơi được giữ kín.")
              : request.type() == PacketType.JOIN_ROOM
                  ? "Đã vào " + snapshot.roomName()
                      + ". Bấm Sẵn sàng để chơi."
                  : request.type() == PacketType.CHANGE_ROOM_BET
                      ? "Đã đổi cược cơ bản. Cả bàn cần xác nhận sẵn sàng lại."
                      : "Đã cập nhật trạng thái sẵn sàng.");
      return;
    }

    if (type == PacketType.PROFILE_RESULT
        && (request.type() == PacketType.GET_PROFILE
            || request.type() == PacketType.UPDATE_PROFILE)) {
      var p = (ProfileResult) data;
      finish();
      lastProfile = System.nanoTime();
      view.showSignedIn(p.displayName(), p.userId());

      if (room == null) {
        view.setBalance(p.balance());
      }

      if (request.type() == PacketType.GET_PROFILE
          && !request.silent()) {
        profileDialogOpen = true;
        try {
          view.editProfile(
              p, room == null,
              r -> request(PacketType.UPDATE_PROFILE, r));
        } finally {
          profileDialogOpen = false;
        }
      } else if (request.type() == PacketType.UPDATE_PROFILE) {
        view.showMessage("Đã lưu hồ sơ.");
      }
      return;
    }

    if ((request.type() == PacketType.GET_HISTORY
            && type == PacketType.HISTORY_RESULT)
        || (request.type() == PacketType.GET_LEADERBOARD
            && type == PacketType.LEADERBOARD_RESULT)) {
      finish();
      if (dataDialog != null && dataDialog.isDisplayable()) {
        dataDialog.showPage(
            (PageResult<?>) data,
            request.type() == PacketType.GET_HISTORY);
      }
      return;
    }

    if (type == PacketType.ACK) {
      finish();

      switch (request.type()) {
        case LOGOUT -> {
          signedOut();
          view.showMessage("Đã đăng xuất.");
        }

        case LEAVE_ROOM -> {
          room = null;
          watching = false;
          clearInspection();
          view.showLobby();
          updateEnabled();
          loadRooms(false);
          view.showMessage("Bạn đã về sảnh chơi.");
        }

        case CHAT_MESSAGE -> view.room().clearInput();
        case CHANGE_PASSWORD -> view.showMessage("Đã đổi mật khẩu.");
        case START_GAME_REQUEST, GAME_ACTION, PLAY_AGAIN_VOTE -> {}

        default ->
            disconnect(
                "Phiên bản game chưa tương thích. Mở lại bản mới nhất.");
      }
      return;
    }

    disconnect("Không đồng bộ được game. Vui lòng đăng nhập lại.");
  }

  private void setRooms(Object data) {
    view.lobby().setRooms(
        ((List<?>) data).stream()
            .map(RoomSummary.class::cast).toList());
  }

  private void applyRoom(RoomSnapshot next, boolean entering) {
    if (user == null
        || (!entering
            && (room == null || next.roomId() != room.roomId()))) {
      return;
    }

    if (room != null
        && room.roomId() == next.roomId()
        && next.stateVersion() <= room.stateVersion()) {
      return;
    }

    if (next.roomId() < 1
        || next.roomId() > 20
        || next.players() == null
        || next.players().size() > 8
        || !watching
            && next.players().stream()
                .noneMatch(p -> p.userId() == user.userId())
        || !next.players().isEmpty()
            && next.players().stream()
                .noneMatch(p -> p.userId() == next.ownerUserId())) {
      disconnect("Bàn chơi chưa đồng bộ. Đăng nhập lại để tiếp tục.");
      return;
    }

    boolean returnedToWaiting =
        room != null && "RESULT".equals(room.status())
            && "WAITING".equals(next.status());

    room = next;
    view.showRoom(next, user.userId());

    if (watching && "WAITING".equals(next.status())) {
      clearInspection();
    }

    if (returnedToWaiting) {
      view.showMessage(
          watching
              ? "Bạn đang xem. Chờ người chơi bắt đầu ván tiếp theo."
              : "Kết quả đã ẩn. Bạn có thể sẵn sàng cho ván mới hoặc rời bàn.");
    }

    for (var e : enteringChat) {
      if (e.roomId() == next.roomId()) {
        view.room().appendChat(e.message());
      }
    }

    enteringChat.clear();
    updateEnabled();
  }

  private void finish() {
    pending = null;
    timeout.stop();
    updateEnabled();
  }

  private void updateEnabled() {
    view.setOperationEnabled(queuedAuth == null && pending == null);
    if (adminDialog != null) {
      adminDialog.setBusy(pending != null || queuedAuth != null);
    }
  }

  private void signedOut() {
    user = null;
    room = null;
    watching = false;
    updateAdminMenu();
    clearInspection();

    if (adminDialog != null) {
      adminDialog.dispose();
      adminDialog = null;
    }

    enteringChat.clear();
    afterRegister = null;

    if (dataDialog != null) {
      dataDialog.dispose();
      dataDialog = null;
    }

    view.showSignedOut();
    updateEnabled();
  }

  private void disconnect(String reason) {
    var old = client;
    client = null;
    queuedAuth = null;
    afterRegister = null;
    pending = null;
    timeout.stop();

    if (old != null) {
      old.disconnect();
    }

    signedOut();
    view.showMessage(reason);
  }

  private void onUi(SocketClient source, Runnable action) {
    SwingUtilities.invokeLater(() -> {
      if (!closing && client == source) {
        action.run();
      }
    });
  }
}