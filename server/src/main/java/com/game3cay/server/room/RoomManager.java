package com.game3cay.server.room;

import com.game3cay.server.bus.*;
import com.game3cay.server.game.*;
import com.game3cay.shared.model.*;
import com.game3cay.shared.network.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Lock bảo vệ RAM; socket I/O được xếp hàng, truy vấn DB/ghi kết quả chạy ngoài lock. */
public final class RoomManager implements RoomService {
  private final RoomEvents events;
  private final UserBUS users;
  private final ResultPersistence persistence;
  private final java.util.function.LongSupplier resultClock;
  private final GameEngine engine = new DefaultGameEngine();
  private final Map<Long, Room> rooms = new LinkedHashMap<>();
  private final Map<Long, Long> membership = new HashMap<>();
  private final Map<Long, ArrayDeque<Long>> chatTimes = new HashMap<>();
  private final Map<Long, String> activeRounds = new java.util.concurrent.ConcurrentHashMap<>();
  private final int turnTimeout =
      RuntimeSettings.integer("server.properties", "game.turnTimeoutSeconds", 30, 5, 300);
  public static final int TABLE_COUNT = 20;

  private static final class Member {
    UserDTO user;
    final int seat;
    boolean ready;

    Member(UserDTO u, int s) {
      user = u;
      seat = s;
    }
  }

  private static final class Room {
    final long id;
    CreateRoomRequest settings;
    final LinkedHashMap<Long, Member> members = new LinkedHashMap<>();
    long owner, version, lastAction, resultUntil;
    String state = "WAITING", roundId;
    boolean resultSent;
    List<CompletedRound.PrivateHand> hands = List.of();

    Room(long id, CreateRoomRequest settings) {
      this.id = id;
      this.settings = settings;
    }
  }

  public RoomManager(RoomEvents e) {
    this(e, null, null);
  }

  public RoomManager(RoomEvents e, UserBUS u, ResultPersistence p) {
    this(e, u, p, System::nanoTime);
  }

  public RoomManager(
      RoomEvents e, UserBUS u, ResultPersistence p, java.util.function.LongSupplier resultClock) {
    events = Objects.requireNonNull(e);
    this.resultClock = Objects.requireNonNull(resultClock);
    users = u;
    persistence = p;
    for (long id = 1; id <= TABLE_COUNT; id++) {
      rooms.put(
          id,
          new Room(
              id, new CreateRoomRequest("Bàn " + id, 2, 8, new java.math.BigDecimal("10.00"))));
    }
  }

  public synchronized boolean inRoom(long uid) {
    return membership.containsKey(uid);
  }

  private void noPending(long uid) throws BUSException {
    if (activeRounds.containsKey(uid))
      throw fail(
          ErrorCode.INVALID_STATE, "Bạn còn điểm đang tham gia ở ván trước; chờ ván đó kết thúc");
    if (persistence != null && persistence.pendingFor(uid))
      throw fail(ErrorCode.INVALID_STATE, "Kết quả ván trước đang được lưu. Thử lại sau vài giây");
  }

  @Override
  public synchronized List<RoomSummary> listRooms() {
    return rooms.values().stream()
        .map(
            r ->
                new RoomSummary(
                    r.id,
                    "R" + r.id,
                    r.settings.roomName(),
                    r.state,
                    r.members.size(),
                    r.settings.maxPlayers(),
                    r.settings.betAmount()))
        .toList();
  }

  @Override
  public synchronized RoomSnapshot create(UserDTO actor, CreateRoomRequest request)
      throws BUSException {
    outside(actor.userId());
    noPending(actor.userId());
    throw fail(ErrorCode.INVALID_REQUEST, "Chọn một trong 20 bàn có sẵn ở sảnh chơi");
  }

  @Override
  public synchronized RoomSnapshot join(UserDTO actor, JoinRoomRequest request)
      throws BUSException {
    outside(actor.userId());
    noPending(actor.userId());
    var r = rooms.get(request.roomId());
    if (r == null) throw fail(ErrorCode.ROOM_NOT_FOUND, "Phòng không tồn tại");
    if (!"WAITING".equals(r.state)) throw fail(ErrorCode.ROOM_NOT_WAITING, "Phòng đang chơi");
    if (r.members.size() >= r.settings.maxPlayers())
      throw fail(ErrorCode.ROOM_FULL, "Phòng đã đầy");
    return add(r, actor);
  }

  private RoomSnapshot add(Room r, UserDTO u) {
    if (r.members.isEmpty()) r.owner = u.userId();
    int seat = 1;
    var occupied = new HashSet<Integer>();
    r.members.values().forEach(m -> occupied.add(m.seat));
    while (occupied.contains(seat)) seat++;
    r.members.put(u.userId(), new Member(u, seat));
    membership.put(u.userId(), r.id);
    r.version++;
    updated(r);
    system(r, u.displayName() + " đã vào phòng");
    return snapshot(r);
  }

  @Override
  public synchronized void leave(long uid) throws BUSException {
    var r = requireRoom(uid);
    var m = r.members.get(uid);
    if (r.roundId != null && engine.isRoundActive(r.roundId)) {
      try {
        engine.handlePlayerLeft(r.roundId, uid);
        r.lastAction = System.nanoTime();
      } catch (GameEngineException e) {
        throw fail(e.getErrorCode(), e.getMessage());
      }
    }
    r.members.remove(uid);
    membership.remove(uid);
    chatTimes.remove(uid);
    if (r.owner == uid) r.owner = r.members.isEmpty() ? 0 : r.members.keySet().iterator().next();
    r.version++;
    if (r.roundId != null) publishGame(r);
    if (r.members.isEmpty()) {
      resetEmptyTable(r);
      updated(r);
    } else {
      updated(r);
      system(r, m.user.displayName() + " đã rời phòng");
    }
  }

  @Override
  public RoomSnapshot setReady(long uid, boolean ready) throws BUSException {
    noPending(uid);
    UserDTO fresh = users == null ? null : users.getProfile(uid);
    synchronized (this) {
      var r = requireRoom(uid);
      if (!"WAITING".equals(r.state)) throw fail(ErrorCode.INVALID_STATE, "Đang chơi ván");
      noPending(uid);
      var m = r.members.get(uid);
      if (fresh != null) m.user = fresh;
      if (m.user.balance().compareTo(r.settings.betAmount()) < 0)
        throw fail(ErrorCode.INVALID_BET_AMOUNT, "Bạn đang xem vì không đủ điểm cơ bản");
      m.ready = ready;
      r.version++;
      updated(r);
      return snapshot(r);
    }
  }

  /** Chỉ chủ bàn được đổi cược; mọi người phải xác nhận sẵn sàng lại. */
  public synchronized RoomSnapshot changeBet(long uid, java.math.BigDecimal amount)
      throws BUSException {
    var r = requireRoom(uid);
    if (r.owner != uid) throw fail(ErrorCode.NOT_ROOM_OWNER, "Chỉ chủ bàn được đổi cược cơ bản");
    if (!"WAITING".equals(r.state))
      throw fail(ErrorCode.INVALID_STATE, "Chỉ đổi cược khi bàn đang chờ ván mới");
    if (amount == null
        || amount.signum() <= 0
        || amount.scale() < 0
        || amount.scale() > 2
        || amount.precision() > 15)
      throw fail(ErrorCode.INVALID_BET_AMOUNT, "Mức cược phải dương, tối đa 2 số thập phân");
    noPending(uid);
    if (r.settings.betAmount().compareTo(amount) == 0) return snapshot(r);
    r.settings =
        new CreateRoomRequest(
            r.settings.roomName(),
            r.settings.minPlayers(),
            r.settings.maxPlayers(),
            amount.setScale(2));
    r.members.values().forEach(m -> m.ready = false);
    r.version++;
    updated(r);
    system(
        r,
        "Chủ bàn đổi cược cơ bản thành "
            + amount.stripTrailingZeros().toPlainString()
            + " điểm. Mọi người cần sẵn sàng lại.");
    return snapshot(r);
  }

  private static void resetEmptyTable(Room r) {
    if (!r.members.isEmpty() || r.roundId != null) return;
    r.owner = 0;
    r.state = "WAITING";
    r.resultUntil = 0;
    r.settings =
        new CreateRoomRequest(r.settings.roomName(), 2, 8, new java.math.BigDecimal("10.00"));
  }

  @Override
  public synchronized void checkStart(long uid) throws BUSException {
    var r = requireRoom(uid);
    if (r.owner != uid) throw fail(ErrorCode.NOT_ROOM_OWNER, "Chỉ chủ phòng được bắt đầu");
    if (!"WAITING".equals(r.state)) throw fail(ErrorCode.INVALID_STATE, "Phòng chưa sẵn sàng");
    var active =
        r.members.values().stream()
            .filter(m -> m.user.balance().compareTo(r.settings.betAmount()) >= 0)
            .toList();
    if (active.size() < r.settings.minPlayers())
      throw fail(ErrorCode.NOT_ENOUGH_PLAYERS, "Chưa đủ người có điểm để chơi");
    for (var m : active) {
      noPending(m.user.userId());
      if (!m.ready) throw fail(ErrorCode.NOT_ALL_READY, "Có người chưa sẵn sàng");
    }
  }

  public void start(long uid) throws BUSException {
    if (users == null || persistence == null)
      throw fail(ErrorCode.INVALID_STATE, "Chưa cấu hình game/DB");
    List<Long> ids;
    long version;
    synchronized (this) {
      checkStart(uid);
      var r = requireRoom(uid);
      ids = List.copyOf(r.members.keySet());
      version = r.version;
    }
    var fresh = new LinkedHashMap<Long, UserDTO>();
    for (long id : ids) fresh.put(id, users.getProfile(id));
    synchronized (this) {
      var r = requireRoom(uid);
      if (r.version != version)
        throw fail(ErrorCode.INVALID_STATE, "Phòng vừa thay đổi, hãy thử lại");
      for (var m : r.members.values()) m.user = fresh.get(m.user.userId());
      checkStart(uid);
      var players =
          r.members.values().stream()
              .filter(m -> m.user.balance().compareTo(r.settings.betAmount()) >= 0)
              .sorted(Comparator.comparingInt(m -> m.seat))
              .map(
                  m ->
                      new RoundParticipant(
                          m.user.userId(),
                          m.user.username(),
                          m.user.displayName(),
                          m.user.balance()))
              .toList();
      // Đưa chủ phòng về đầu danh sách người chia (nếu chủ phòng đang xem thì ghế đầu chia).
      var ordered = new ArrayList<>(players);
      int ownerIndex = -1;
      for (int i = 0; i < ordered.size(); i++)
        if (ordered.get(i).userId() == r.owner) ownerIndex = i;
      if (ownerIndex > 0) Collections.rotate(ordered, -ownerIndex);
      String roundId = UUID.randomUUID().toString();
      try {
        engine.initRound(roundId, r.id, ordered, r.settings.betAmount());
        for (var p : ordered) activeRounds.put(p.userId(), roundId);
        r.roundId = roundId;
        r.state = "PLAYING";
        r.resultSent = false;
        r.hands =
            ordered.stream()
                .map(
                    p -> {
                      try {
                        return new CompletedRound.PrivateHand(
                            p.userId(), engine.getPlayerCards(roundId, p.userId()));
                      } catch (GameEngineException e) {
                        throw new IllegalStateException(e);
                      }
                    })
                .toList();
        r.lastAction = System.nanoTime();
        r.version++;
        updated(r);
        push(r, PacketType.START_GAME, new GameStartInfo(r.id, roundId, 1));
        for (var p : ordered)
          privatePush(
              p.userId(),
              PacketType.DEAL_CARD,
              new DealCardPayload(roundId, engine.getPlayerCards(roundId, p.userId()), 1));
        system(r, "Ván mới bắt đầu. Mỗi người nhận 3 lá bài.");
        publishGame(r);
      } catch (GameEngineException e) {
        throw fail(e.getErrorCode(), e.getMessage());
      }
    }
  }

  public synchronized void action(long uid, GameActionRequest request) throws BUSException {
    var r = requireRoom(uid);
    if (r.roundId == null || !r.roundId.equals(request.roundId()))
      throw fail(ErrorCode.STALE_ROUND, "Sai ván hiện tại");
    try {
      engine.handleAction(r.roundId, uid, request.action(), request.raiseTo());
      r.lastAction = System.nanoTime();
      String actionText =
          switch (request.action()) {
            case CALL -> "theo / kiểm tra";
            case RAISE ->
                "tố đến " + request.raiseTo().stripTrailingZeros().toPlainString() + " điểm";
            case ALL_IN -> "tất tay";
            case FOLD -> "úp bài";
            default -> "đã đặt điểm";
          };
      system(r, r.members.get(uid).user.displayName() + " " + actionText);
      publishGame(r);
    } catch (GameEngineException e) {
      throw fail(e.getErrorCode(), e.getMessage());
    }
  }

  private void publishGame(Room r) {
    if (r.roundId == null) return;
    try {
      var state = engine.getPublicSnapshot(r.roundId);
      push(r, PacketType.GAME_UPDATED, state);
      push(r, PacketType.BET_UPDATED, engine.getBetSnapshot(r.roundId));
      for (var hand : r.hands)
        if (r.members.containsKey(hand.userId()))
          privatePush(
              hand.userId(),
              PacketType.PLAYER_STATE,
              engine.getPlayerSnapshot(r.roundId, hand.userId()));
      if (engine.isRoundActive(r.roundId)) return;
      var result = engine.getRoundResult(r.roundId);
      if (!r.resultSent) {
        try {
          persistence.enqueue(
              new CompletedRound(
                  result, r.id, r.settings.roomName(), r.settings.betAmount(), r.hands));
        } catch (IOException e) {
          System.err.println("[JOURNAL] Không ghi được roundId=" + r.roundId);
          return;
        }
        for (var p : result.playerResults()) activeRounds.remove(p.userId(), r.roundId);
        r.resultSent = true;
        push(r, PacketType.ROUND_RESULT, result);
        push(
            r,
            PacketType.PLAY_AGAIN_PROMPT,
            new PlayAgainPrompt(
                r.roundId,
                "Kết quả hiện " + RoundResult.DISPLAY_SECONDS + " giây, sau đó chọn sẵn sàng"));
        for (var m : r.members.values()) {
          m.ready = false;
          for (var p : result.playerResults())
            if (p.userId() == m.user.userId()) {
              var u = m.user;
              var balance = engine.getPlayerSnapshot(r.roundId, u.userId()).myBalance();
              m.user =
                  new UserDTO(
                      u.userId(),
                      u.username(),
                      u.displayName(),
                      u.email(),
                      u.avatarUrl(),
                      balance,
                      u.status(),
                      u.totalGames(),
                      u.totalWins(),
                      u.totalLosses());
            }
        }
        system(
            r,
            "CANCELLED".equals(result.roundStatus())
                ? "Ván đã hủy. Điểm được hoàn lại."
                : "Ván đã kết thúc. Xem kết quả trong " + RoundResult.DISPLAY_SECONDS + " giây.");
        System.out.println(
            "[GAME] " + result.roundStatus() + " roomId=" + r.id + " roundId=" + r.roundId);
        engine.cleanupRound(r.roundId);
        r.roundId = null;
        r.state = "RESULT";
        r.resultUntil =
            resultClock.getAsLong() + TimeUnit.SECONDS.toNanos(RoundResult.DISPLAY_SECONDS);
        resetEmptyTable(r);
        r.version++;
        updated(r);
      }
    } catch (GameEngineException e) {
      throw new IllegalStateException(e);
    }
  }

  public synchronized void tick() {
    for (var r : List.copyOf(rooms.values())) {
      if ("RESULT".equals(r.state) && resultClock.getAsLong() >= r.resultUntil) {
        r.state = "WAITING";
        r.resultUntil = 0;
        r.version++;
        updated(r);
        system(r, "Bàn đã sẵn sàng cho ván mới. Mọi người tự quyết định có chơi tiếp.");
      }
      if (r.roundId == null) continue;
      try {
        if (!engine.isRoundActive(r.roundId)) {
          publishGame(r);
          continue;
        }
        if (System.nanoTime() - r.lastAction >= TimeUnit.SECONDS.toNanos(turnTimeout)) {
          Long turn = engine.getPublicSnapshot(r.roundId).currentTurnUserId();
          if (turn != null) {
            engine.handleAction(r.roundId, turn, BetAction.FOLD, null);
            r.lastAction = System.nanoTime();
            system(r, "Hết " + turnTimeout + " giây: tự úp bài người chơi " + turn);
            publishGame(r);
          }
        }
      } catch (Exception e) {
        System.err.println("[GAME] tick roomId=" + r.id + " cause=" + e.getClass().getSimpleName());
      }
    }
  }

  @Override
  public synchronized void chat(long uid, String content) throws BUSException {
    var r = requireRoom(uid);
    if (content == null || content.isBlank())
      throw fail(ErrorCode.INVALID_REQUEST, "Tin nhắn trống");
    if (content.length() > 500) throw fail(ErrorCode.MESSAGE_TOO_LONG, "Tối đa 500 ký tự");
    long now = System.nanoTime();
    var times = chatTimes.computeIfAbsent(uid, k -> new ArrayDeque<>());
    while (!times.isEmpty() && now - times.peekFirst() >= TimeUnit.SECONDS.toNanos(10))
      times.removeFirst();
    if (times.size() >= 5) throw fail(ErrorCode.RATE_LIMIT_EXCEEDED, "Tối đa 5 tin/10 giây");
    times.addLast(now);
    events.chatMessage(
        r.id,
        new ChatMessage(
            uid,
            r.members.get(uid).user.displayName(),
            content.trim(),
            "TEXT",
            System.currentTimeMillis()),
        recipients(r));
  }

  @Override
  public synchronized void onDisconnected(long uid) {
    if (membership.containsKey(uid))
      try {
        leave(uid);
      } catch (BUSException e) {
        System.err.println("[ROOM] cleanup userId=" + uid);
      }
    chatTimes.remove(uid);
  }

  private void outside(long uid) throws BUSException {
    if (membership.containsKey(uid)) throw fail(ErrorCode.ALREADY_IN_ROOM, "Bạn đang ở một phòng");
  }

  private Room requireRoom(long uid) throws BUSException {
    var id = membership.get(uid);
    if (id == null) throw fail(ErrorCode.NOT_IN_ROOM, "Chưa ở trong phòng");
    var r = rooms.get(id);
    if (r == null) throw fail(ErrorCode.ROOM_NOT_FOUND, "Không tìm thấy phòng");
    return r;
  }

  private static List<Long> recipients(Room r) {
    return List.copyOf(r.members.keySet());
  }

  private void updated(Room r) {
    events.roomUpdated(snapshot(r), recipients(r));
    events.lobbyUpdated(listRooms());
  }

  private void system(Room r, String text) {
    events.chatMessage(r.id, ChatMessage.system(text), recipients(r));
  }

  private void push(Room r, PacketType type, Object data) {
    events.packet(Packet.serverPush(type, data), recipients(r));
  }

  private void privatePush(long uid, PacketType type, Object data) {
    events.packet(Packet.serverPush(type, data), List.of(uid));
  }

  private static RoomSnapshot snapshot(Room r) {
    return new RoomSnapshot(
        r.id,
        "R" + r.id,
        r.settings.roomName(),
        r.state,
        r.owner,
        r.settings.minPlayers(),
        r.settings.maxPlayers(),
        r.settings.betAmount(),
        r.members.values().stream()
            .map(
                m ->
                    new RoomSnapshot.PlayerSlot(
                        m.user.userId(),
                        m.user.displayName(),
                        m.seat,
                        m.ready,
                        m.user.balance().compareTo(r.settings.betAmount()) >= 0
                            ? "PLAYING"
                            : "SPECTATOR"))
            .toList(),
        r.version);
  }

  private static BUSException fail(ErrorCode c, String m) {
    return new BUSException(c, m);
  }
}
