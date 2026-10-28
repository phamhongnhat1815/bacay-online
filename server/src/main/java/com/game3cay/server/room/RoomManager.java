package com.game3cay.server.room;

import com.game3cay.server.bus.BUSException;
import com.game3cay.server.bus.UserBUS;
import com.game3cay.server.bus.UserDTO;
import com.game3cay.server.game.CompletedRound;
import com.game3cay.server.game.DefaultGameEngine;
import com.game3cay.server.game.GameEngine;
import com.game3cay.server.game.GameEngineException;
import com.game3cay.server.game.HandEvaluator;
import com.game3cay.server.game.ResultPersistence;
import com.game3cay.server.game.RoundParticipant;
import com.game3cay.shared.model.AdminLiveMatchDetail;
import com.game3cay.shared.model.AdminLivePlayerHand;
import com.game3cay.shared.model.AdminRoomDetail;
import com.game3cay.shared.model.BetAction;
import com.game3cay.shared.model.Card;
import com.game3cay.shared.model.ChatMessage;
import com.game3cay.shared.model.CreateRoomRequest;
import com.game3cay.shared.model.DealCardPayload;
import com.game3cay.shared.model.GameActionRequest;
import com.game3cay.shared.model.GameStartInfo;
import com.game3cay.shared.model.Hand;
import com.game3cay.shared.model.JoinRoomRequest;
import com.game3cay.shared.model.PlayAgainPrompt;
import com.game3cay.shared.model.RoomChatEvent;
import com.game3cay.shared.model.RoomSnapshot;
import com.game3cay.shared.model.RoomSummary;
import com.game3cay.shared.model.RoundResult;
import com.game3cay.shared.network.ErrorCode;
import com.game3cay.shared.network.Packet;
import com.game3cay.shared.network.PacketType;
import com.game3cay.shared.network.RuntimeSettings;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

public final class RoomManager implements RoomService, AutoCloseable {
    public static final int TABLE_COUNT = 20;
    public static final int AUTO_START_SECONDS = 8;
    public static final int DISCONNECT_GRACE_SECONDS = 15;

    private record RecentRoom(long roomId, long timestampNanos) {}
    private final Map<Long, RecentRoom> recentRoomHistory = new ConcurrentHashMap<>();

    private final RoomEvents events;
    private final UserBUS users;
    private final ResultPersistence persistence;
    private final LongSupplier resultClock;
    private final GameEngine engine = new DefaultGameEngine();
    private final HandEvaluator handEvaluator = new HandEvaluator();

    private final Map<Long, Room> rooms = new LinkedHashMap<>();
    private final Map<Long, Long> membership = new HashMap<>();
    private final Map<Long, ArrayDeque<Long>> chatTimes = new HashMap<>();
    private final Map<Long, String> activeRounds = new ConcurrentHashMap<>();
    // Reservations cover the DB snapshot window, without holding the room monitor during I/O.
    private final Set<Long> startingUsers = new HashSet<>();
    private final Set<Long> adjustingUsers = new HashSet<>();
    private final ExecutorService autoStarts = Executors.newVirtualThreadPerTaskExecutor();

    private final int turnTimeoutSeconds = RuntimeSettings.integer(
            "server.properties", "game.turnTimeoutSeconds", 30, 5, 300);

    private static final class Member {
        UserDTO user;
        final int seat;
        boolean ready;

        Member(UserDTO user, int seat) {
            this.user = user;
            this.seat = seat;
        }
    }

    private static final class Room {
        final long id;
        CreateRoomRequest settings;
        final LinkedHashMap<Long, Member> members = new LinkedHashMap<>();
        long ownerId;
        long version;
        long lastActionNanos;
        long resultUntilNanos;
        long autoStartDeadlineNanos;
        final Set<Long> afkUsers = new HashSet<>();
        String state = "WAITING";
        String roundId;
        boolean resultSent;
        boolean journaling;
        boolean autoStartQueued;
        List<CompletedRound.PrivateHand> hands = List.of();
        final ArrayDeque<ChatMessage> recentChats = new ArrayDeque<>();

        Room(long id, CreateRoomRequest settings) {
            this.id = id;
            this.settings = settings;
        }
    }

    public RoomManager(RoomEvents events) {
        this(events, null, null);
    }

    public RoomManager(RoomEvents events, UserBUS users, ResultPersistence persistence) {
        this(events, users, persistence, System::nanoTime);
    }

    public RoomManager(
            RoomEvents events,
            UserBUS users,
            ResultPersistence persistence,
            LongSupplier resultClock
    ) {
        this.events = Objects.requireNonNull(events, "events không được null");
        this.resultClock = Objects.requireNonNull(resultClock, "resultClock không được null");
        this.users = users;
        this.persistence = persistence;

        for (long id = 1; id <= TABLE_COUNT; id++) {
            CreateRoomRequest defaultSettings = new CreateRoomRequest("Bàn " + id, 2, 8, new BigDecimal("10.00"));
            rooms.put(id, new Room(id, defaultSettings));
        }
    }

    public synchronized boolean inRoom(long userId) {
        return membership.containsKey(userId);
    }

    private void ensureNoPendingResult(long userId) throws BUSException {
        if (adjustingUsers.contains(userId)) {
            throw fail(ErrorCode.INVALID_STATE, "Số dư đang được điều chỉnh; vui lòng thử lại");
        }
        if (activeRounds.containsKey(userId)) {
            throw fail(ErrorCode.INVALID_STATE, "Bạn còn điểm đang tham gia ở ván trước; vui lòng chờ ván đó kết thúc");
        }
        if (persistence != null && persistence.pendingFor(userId)) {
            throw fail(ErrorCode.INVALID_STATE, "Kết quả ván trước đang được lưu vào CSDL; vui lòng thử lại sau vài giây");
        }
    }

    /** Coordinates admin adjustments with both active rounds and in-flight start snapshots. */
    public void adjustBalanceForAdmin(long adminId, long targetUserId, BigDecimal delta, String reason)
            throws BUSException {
        if (users == null) {
            throw fail(ErrorCode.INTERNAL_ERROR, "Chưa cấu hình dịch vụ tài khoản");
        }
        synchronized (this) {
            ensureNoPendingResult(targetUserId);
            if (startingUsers.contains(targetUserId)) {
                throw fail(ErrorCode.INVALID_STATE, "Người chơi đang bắt đầu ván; không thể điều chỉnh số dư");
            }
            adjustingUsers.add(targetUserId);
        }
        try {
            users.adjustBalanceForAdmin(adminId, targetUserId, delta, reason);
            UserDTO fresh = users.getProfile(targetUserId);
            synchronized (this) {
                Long roomId = membership.get(targetUserId);
                if (roomId != null) {
                    Room room = rooms.get(roomId);
                    Member member = room.members.get(targetUserId);
                    if (member != null) {
                        member.user = fresh;
                        member.ready = false;
                        room.version++;
                        checkAndStartAutoCountdown(room);
                        notifyRoomUpdated(room);
                    }
                }
            }
        } finally {
            synchronized (this) {
                adjustingUsers.remove(targetUserId);
            }
        }
    }

    @Override
    public synchronized List<RoomSummary> listRooms() {
        return rooms.values().stream()
                .map(r -> new RoomSummary(
                        r.id,
                        "R" + r.id,
                        r.settings.roomName(),
                        r.state,
                        r.members.size(),
                        r.settings.maxPlayers(),
                        r.settings.betAmount()
                ))
                .toList();
    }

    @Override
    public synchronized RoomSnapshot create(UserDTO actor, CreateRoomRequest request) throws BUSException {
        ensureOutsideRoom(actor.userId());
        ensureNoPendingResult(actor.userId());
        throw fail(ErrorCode.INVALID_REQUEST, "Vui lòng chọn một trong 20 bàn có sẵn ở sảnh chơi");
    }

    @Override
    public synchronized RoomSnapshot join(UserDTO actor, JoinRoomRequest request) throws BUSException {
        Long currentRoomId = membership.get(actor.userId());
        if (currentRoomId != null && currentRoomId.equals(request.roomId())) {
            Room room = rooms.get(request.roomId());
            if (room != null && room.members.containsKey(actor.userId())) {
                Member m = room.members.get(actor.userId());
                m.user = actor;
                sendSystemChat(room, actor.displayName() + " đã khôi phục kết nối vào bàn.");
                notifyRoomUpdated(room);

                if (room.roundId != null && engine.isRoundActive(room.roundId)) {
                    try {
                        sendPrivatePacket(actor.userId(), PacketType.START_GAME,
                                new GameStartInfo(room.id, room.roundId, 1));
                        sendPrivatePacket(
                                actor.userId(),
                                PacketType.GAME_UPDATED,
                                engine.getPublicSnapshot(room.roundId)
                        );
                        sendPrivatePacket(
                                actor.userId(),
                                PacketType.BET_UPDATED,
                                engine.getBetSnapshot(room.roundId)
                        );
                        sendPrivatePacket(
                                actor.userId(),
                                PacketType.PLAYER_STATE,
                                engine.getPlayerSnapshot(room.roundId, actor.userId())
                        );
                    } catch (GameEngineException ignored) {
                    }
                }
                return createRoomSnapshot(room);
            }
        }

        ensureOutsideRoom(actor.userId());

        Room room = rooms.get(request.roomId());
        if (room == null) {
            throw fail(ErrorCode.ROOM_NOT_FOUND, "Bàn chơi không tồn tại");
        }
        RecentRoom recent = recentRoomHistory.get(actor.userId());
        boolean isRejoiningRecentRoom = recent != null
                && recent.roomId == request.roomId()
                && (System.nanoTime() - recent.timestampNanos <= TimeUnit.SECONDS.toNanos(DISCONNECT_GRACE_SECONDS));
        boolean viewingOwnActiveRound = isRejoiningRecentRoom && room.roundId != null
                && room.roundId.equals(activeRounds.get(actor.userId()));
        if (!viewingOwnActiveRound) ensureNoPendingResult(actor.userId());
        if (!"WAITING".equals(room.state)) {
            if (!isRejoiningRecentRoom) {
                throw fail(ErrorCode.ROOM_NOT_WAITING, "Bàn chơi đang diễn ra ván bài");
            }
        }
        if (room.members.size() >= room.settings.maxPlayers()) {
            throw fail(ErrorCode.ROOM_FULL, "Bàn chơi đã đủ số lượng người");
        }

        recentRoomHistory.remove(actor.userId());
        addPlayerToRoom(room, actor);
        if (room.roundId != null) {
            try {
                sendPrivatePacket(actor.userId(), PacketType.START_GAME, new GameStartInfo(room.id, room.roundId, 1));
                sendPrivatePacket(actor.userId(), PacketType.GAME_UPDATED, engine.getPublicSnapshot(room.roundId));
                sendPrivatePacket(actor.userId(), PacketType.BET_UPDATED, engine.getBetSnapshot(room.roundId));
                if (room.hands.stream().anyMatch(h -> h.userId() == actor.userId())) {
                    sendPrivatePacket(actor.userId(), PacketType.PLAYER_STATE,
                            engine.getPlayerSnapshot(room.roundId, actor.userId()));
                }
            } catch (GameEngineException e) {
                throw fail(e.getErrorCode(), e.getMessage());
            }
        }
        return createRoomSnapshot(room);
    }

    private RoomSnapshot addPlayerToRoom(Room room, UserDTO user) {
        if (room.members.isEmpty()) {
            room.ownerId = user.userId();
        }

        int seatNumber = 1;
        Set<Integer> occupiedSeats = new HashSet<>();
        for (Member m : room.members.values()) {
            occupiedSeats.add(m.seat);
        }
        while (occupiedSeats.contains(seatNumber)) {
            seatNumber++;
        }

        Member member = new Member(user, seatNumber);
        if ("WAITING".equals(room.state) && user.balance().compareTo(room.settings.betAmount()) >= 0) {
            member.ready = true;
        }
        room.members.put(user.userId(), member);
        membership.put(user.userId(), room.id);
        room.version++;

        checkAndStartAutoCountdown(room);

        notifyRoomUpdated(room);
        sendSystemChat(room, user.displayName() + " đã vào bàn" + (member.ready ? " (Đã sẵn sàng)" : ""));
        return createRoomSnapshot(room);
    }

    @Override
    public void leave(long userId) throws BUSException {
        Room room;
        synchronized (this) {
            room = requireUserRoom(userId);
            Member leavingMember = room.members.get(userId);

            boolean participant = room.hands.stream().anyMatch(h -> h.userId() == userId);
            if (participant && room.roundId != null && engine.isRoundActive(room.roundId)) {
                try {
                    engine.handlePlayerLeft(room.roundId, userId);
                    room.lastActionNanos = System.nanoTime();
                } catch (GameEngineException e) {
                    throw fail(e.getErrorCode(), e.getMessage());
                }
            }

            recentRoomHistory.put(userId, new RecentRoom(room.id, System.nanoTime()));
            room.members.remove(userId);
            membership.remove(userId);
            chatTimes.remove(userId);

            if (room.ownerId == userId) {
                room.ownerId = room.members.isEmpty() ? 0 : room.members.keySet().iterator().next();
            }

            room.version++;
            if (room.members.isEmpty()) {
                room.autoStartDeadlineNanos = 0;
                resetEmptyTable(room);
                notifyRoomUpdated(room);
            } else {
                checkAndStartAutoCountdown(room);
                notifyRoomUpdated(room);
                sendSystemChat(room, leavingMember.user.displayName() + " đã rời bàn");
            }
        }
        publishGameUpdate(room);
    }

    @Override
    public RoomSnapshot setReady(long userId, boolean ready) throws BUSException {
        synchronized (this) {
            ensureNoPendingResult(userId);
        }
        UserDTO freshUser = (users == null) ? null : users.getProfile(userId);

        synchronized (this) {
            Room room = requireUserRoom(userId);
            if (!"WAITING".equals(room.state)) {
                throw fail(ErrorCode.INVALID_STATE, "Bàn đang trong ván bài; không thể đổi trạng thái sẵn sàng");
            }
            ensureNoPendingResult(userId);

            Member member = room.members.get(userId);
            if (freshUser != null) {
                member.user = freshUser;
            }

            if (member.user.balance().compareTo(room.settings.betAmount()) < 0) {
                throw fail(ErrorCode.INVALID_BET_AMOUNT, "Bạn đang ở chế độ xem vì số dư không đủ mức cược cơ bản");
            }

            member.ready = ready;
            room.version++;
            checkAndStartAutoCountdown(room);
            notifyRoomUpdated(room);
            return createRoomSnapshot(room);
        }
    }

    public synchronized RoomSnapshot changeBet(long userId, BigDecimal newBetAmount) throws BUSException {
        Room room = requireUserRoom(userId);
        if (room.ownerId != userId) {
            throw fail(ErrorCode.NOT_ROOM_OWNER, "Chỉ chủ bàn mới có quyền đổi mức cược cơ bản");
        }
        if (!"WAITING".equals(room.state)) {
            throw fail(ErrorCode.INVALID_STATE, "Chỉ được đổi mức cược khi bàn đang chờ ván mới");
        }
        if (newBetAmount == null
                || newBetAmount.signum() <= 0
                || newBetAmount.scale() < 0
                || newBetAmount.scale() > 2
                || newBetAmount.precision() > 15) {
            throw fail(ErrorCode.INVALID_BET_AMOUNT, "Mức cược phải là số dương, tối đa 2 chữ số thập phân");
        }

        ensureNoPendingResult(userId);
        if (room.settings.betAmount().compareTo(newBetAmount) == 0) {
            return createRoomSnapshot(room);
        }

        room.settings = new CreateRoomRequest(
                room.settings.roomName(),
                room.settings.minPlayers(),
                room.settings.maxPlayers(),
                newBetAmount.setScale(2)
        );

        for (Member m : room.members.values()) {
            m.ready = false;
        }
        room.autoStartDeadlineNanos = 0;

        room.version++;
        notifyRoomUpdated(room);
        sendSystemChat(
                room,
                "Chủ bàn đã đổi cược cơ bản thành "
                        + newBetAmount.stripTrailingZeros().toPlainString()
                        + " điểm. Mọi người cần xác nhận sẵn sàng lại."
        );
        return createRoomSnapshot(room);
    }

    private static void resetEmptyTable(Room room) {
        if (!room.members.isEmpty() || room.roundId != null) {
            return;
        }
        room.ownerId = 0;
        room.state = "WAITING";
        room.resultUntilNanos = 0;
        room.settings = new CreateRoomRequest(room.settings.roomName(), 2, 8, new BigDecimal("10.00"));
    }

    @Override
    public synchronized void checkStart(long userId) throws BUSException {
        Room room = requireUserRoom(userId);
        if (room.ownerId != userId) {
            throw fail(ErrorCode.NOT_ROOM_OWNER, "Chỉ chủ bàn mới có quyền bắt đầu ván bài");
        }
        if (!"WAITING".equals(room.state)) {
            throw fail(ErrorCode.INVALID_STATE, "Bàn chơi chưa ở trạng thái sẵn sàng");
        }

        List<Member> eligibleMembers = room.members.values().stream()
                .filter(m -> m.user.balance().compareTo(room.settings.betAmount()) >= 0)
                .toList();

        if (eligibleMembers.size() < room.settings.minPlayers()) {
            throw fail(ErrorCode.NOT_ENOUGH_PLAYERS, "Chưa đủ người chơi có đủ số dư tối thiểu để bắt đầu");
        }

        for (Member m : eligibleMembers) {
            ensureNoPendingResult(m.user.userId());
            if (!m.ready) {
                throw fail(ErrorCode.NOT_ALL_READY, "Còn người chơi chưa bấm sẵn sàng: " + m.user.displayName());
            }
        }
    }

    public void start(long userId) throws BUSException {
        if (users == null || persistence == null) {
            throw fail(ErrorCode.INTERNAL_ERROR, "Chưa cấu hình cơ sở dữ liệu ván bài");
        }

        List<Long> memberIds;
        long capturedVersion;
        Room capturedRoom;
        synchronized (this) {
            checkStart(userId);
            Room room = requireUserRoom(userId);
            capturedRoom = room;
            memberIds = List.copyOf(room.members.keySet());
            for (long id : memberIds) {
                if (startingUsers.contains(id) || adjustingUsers.contains(id)) {
                    throw fail(ErrorCode.INVALID_STATE, "Số dư đang được cập nhật; vui lòng thử lại");
                }
            }
            startingUsers.addAll(memberIds);
            capturedVersion = room.version;
        }

        try {
            Map<Long, UserDTO> freshUsers = new LinkedHashMap<>();
            for (long id : memberIds) {
                freshUsers.put(id, users.getProfile(id));
            }

            synchronized (this) {
                Room room = requireUserRoom(userId);
                if (room.version != capturedVersion) {
                    throw fail(ErrorCode.INVALID_STATE, "Bàn chơi vừa có thay đổi; hãy thử lại");
                }

                for (Member m : room.members.values()) {
                    m.user = freshUsers.get(m.user.userId());
                }

                checkStart(userId);

                List<RoundParticipant> participants = room.members.values().stream()
                        .filter(m -> m.user.balance().compareTo(room.settings.betAmount()) >= 0)
                        .sorted(Comparator.comparingInt(m -> m.seat))
                        .map(m -> new RoundParticipant(
                                m.user.userId(),
                                m.user.username(),
                                m.user.displayName(),
                                m.user.balance()
                        ))
                        .toList();

                List<RoundParticipant> orderedParticipants = new ArrayList<>(participants);
                int ownerIndex = -1;
                for (int i = 0; i < orderedParticipants.size(); i++) {
                    if (orderedParticipants.get(i).userId() == room.ownerId) {
                        ownerIndex = i;
                        break;
                    }
                }
                if (ownerIndex > 0) {
                    Collections.rotate(orderedParticipants, -ownerIndex);
                }

                String roundId = UUID.randomUUID().toString();
                try {
                    engine.initRound(roundId, room.id, orderedParticipants, room.settings.betAmount());
                    for (RoundParticipant p : orderedParticipants) {
                        activeRounds.put(p.userId(), roundId);
                    }

                    room.roundId = roundId;
                    room.state = "PLAYING";
                    room.autoStartDeadlineNanos = 0;
                    room.afkUsers.clear();
                    room.resultSent = false;
                    room.hands = orderedParticipants.stream()
                            .map(p -> {
                                try {
                                    return new CompletedRound.PrivateHand(p.userId(), engine.getPlayerCards(roundId, p.userId()));
                                } catch (GameEngineException e) {
                                    throw new IllegalStateException(e);
                                }
                            })
                            .toList();

                    room.lastActionNanos = System.nanoTime();
                    room.version++;
                    notifyRoomUpdated(room);

                    broadcastToRoom(room, PacketType.START_GAME, new GameStartInfo(room.id, roundId, 1));

                    for (RoundParticipant p : orderedParticipants) {
                        sendPrivatePacket(
                                p.userId(),
                                PacketType.DEAL_CARD,
                                new DealCardPayload(roundId, engine.getPlayerCards(roundId, p.userId()), 1)
                        );
                    }

                } catch (GameEngineException e) {
                    throw fail(e.getErrorCode(), e.getMessage());
                }
            }
            publishGameUpdate(capturedRoom);
        } finally {
            synchronized (this) {
                startingUsers.removeAll(memberIds);
            }
        }
    }

    public void action(long userId, GameActionRequest request) throws BUSException {
        Room room;
        synchronized (this) {
            room = requireUserRoom(userId);
            if (room.roundId == null || !room.roundId.equals(request.roundId())) {
                throw fail(ErrorCode.STALE_ROUND, "Mã ván chơi không khớp với ván đang diễn ra");
            }

            try {
                engine.handleAction(room.roundId, userId, request.action(), request.raiseTo());
                room.lastActionNanos = System.nanoTime();
                room.afkUsers.remove(userId);

            } catch (GameEngineException e) {
                throw fail(e.getErrorCode(), e.getMessage());
            }
        }
        publishGameUpdate(room);
    }

    private void publishGameUpdate(Room room) {
        CompletedRound completed;
        String roundId;
        synchronized (this) {
            if (room.roundId == null || room.journaling) return;
            try {
                var stateSnapshot = engine.getPublicSnapshot(room.roundId);
                broadcastToRoom(room, PacketType.GAME_UPDATED, stateSnapshot);
                broadcastToRoom(room, PacketType.BET_UPDATED, engine.getBetSnapshot(room.roundId));
                for (CompletedRound.PrivateHand hand : room.hands) {
                    if (room.members.containsKey(hand.userId())) {
                        sendPrivatePacket(hand.userId(), PacketType.PLAYER_STATE,
                                engine.getPlayerSnapshot(room.roundId, hand.userId()));
                    }
                }
                if (engine.isRoundActive(room.roundId) || room.resultSent) return;
                roundId = room.roundId;
                completed = new CompletedRound(engine.getRoundResult(roundId), room.id,
                        room.settings.roomName(), room.settings.betAmount(), room.hands);
                room.journaling = true;
            } catch (GameEngineException e) {
                throw new IllegalStateException(e);
            }
        }

        // The journal must be durable before releasing point reservations or publishing the result.
        // Disk I/O runs outside the monitor so other tables can continue playing.
        try {
            if (persistence == null) throw new IOException("Chưa cấu hình journal kết quả");
            persistence.enqueue(completed);
        } catch (IOException | RuntimeException e) {
            synchronized (this) {
                room.journaling = false;
            }
            System.err.println("[JOURNAL] Không thể ghi kết quả roundId=" + roundId + " cause="
                    + e.getClass().getSimpleName());
            return; // The engine and reservations stay intact; tick will retry.
        }

        synchronized (this) {
            room.journaling = false;
            if (!roundId.equals(room.roundId) || room.resultSent) return;
            RoundResult result = completed.result();
            for (var p : result.playerResults()) activeRounds.remove(p.userId(), roundId);
            room.resultSent = true;
            broadcastToRoom(room, PacketType.ROUND_RESULT, result);
            broadcastToRoom(room, PacketType.PLAY_AGAIN_PROMPT,
                    new PlayAgainPrompt(roundId, "Kết quả hiển thị trong " + RoundResult.DISPLAY_SECONDS
                            + " giây, sau đó chọn sẵn sàng để chơi tiếp"));
            for (Member m : room.members.values()) {
                m.ready = false;
                if (result.playerResults().stream().anyMatch(p -> p.userId() == m.user.userId())) {
                    UserDTO u = m.user;
                    try {
                        BigDecimal balance = engine.getPlayerSnapshot(roundId, u.userId()).myBalance();
                        m.user = new UserDTO(u.userId(), u.username(), u.displayName(), u.email(),
                                u.avatarUrl(), balance, u.status(), u.role(), u.totalGames(),
                                u.totalWins(), u.totalLosses());
                    } catch (GameEngineException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
            System.out.println("[GAME] " + result.roundStatus() + " roomId=" + room.id + " roundId=" + roundId);
            engine.cleanupRound(roundId);
            room.roundId = null;
            room.hands = List.of();
            room.state = "RESULT";
            room.resultUntilNanos = resultClock.getAsLong()
                    + TimeUnit.SECONDS.toNanos(RoundResult.DISPLAY_SECONDS);
            resetEmptyTable(room);
            room.version++;
            notifyRoomUpdated(room);
        }
    }
    private void checkAndStartAutoCountdown(Room room) {
        if (!"WAITING".equals(room.state)) {
            return;
        }
        if (canAutoStart(room)) {
            if (room.autoStartDeadlineNanos == 0) {
                room.autoStartDeadlineNanos = resultClock.getAsLong() + TimeUnit.SECONDS.toNanos(AUTO_START_SECONDS);
            }
        } else {
            if (room.autoStartDeadlineNanos != 0) {
                room.autoStartDeadlineNanos = 0;
            }
        }
    }

    private boolean canAutoStart(Room room) {
        List<Member> eligible = room.members.values().stream()
                .filter(m -> m.user.balance().compareTo(room.settings.betAmount()) >= 0).toList();
        return eligible.size() >= room.settings.minPlayers() && eligible.stream().allMatch(m -> {
            long id = m.user.userId();
            return m.ready && !activeRounds.containsKey(id) && !startingUsers.contains(id)
                    && !adjustingUsers.contains(id) && (persistence == null || !persistence.pendingFor(id));
        });
    }

    public void tick() {
        List<Room> publish = new ArrayList<>();
        List<Room> start = new ArrayList<>();
        synchronized (this) {
            for (Room room : rooms.values()) {
                if ("RESULT".equals(room.state) && resultClock.getAsLong() >= room.resultUntilNanos) {
                    room.state = "WAITING";
                    room.resultUntilNanos = 0;
                    List<Long> remove = room.members.values().stream()
                            .filter(m -> room.afkUsers.contains(m.user.userId())
                                    || m.user.balance().compareTo(room.settings.betAmount()) < 0)
                            .map(m -> m.user.userId()).toList();
                    for (long id : remove) {
                        sendPrivatePacket(id, PacketType.LEAVE_ROOM,
                                "Bạn đã được chuyển về sảnh do hết thời gian thao tác hoặc không đủ điểm cược.");
                        // A RESULT room no longer has an engine round, so this performs only in-memory cleanup.
                        try { leave(id); }
                        catch (BUSException e) {
                            System.err.println("[ROOM] Không thể dọn ghế userId=" + id + " roomId=" + room.id);
                        }
                    }
                    room.afkUsers.clear();
                    for (Member m : room.members.values()) m.ready = true;
                    room.version++;
                    notifyRoomUpdated(room);
                }
                if ("WAITING".equals(room.state) && !room.autoStartQueued) {
                    checkAndStartAutoCountdown(room);
                    if (room.autoStartDeadlineNanos > 0) {
                        if (resultClock.getAsLong() >= room.autoStartDeadlineNanos) {
                            room.autoStartDeadlineNanos = 0;
                            room.autoStartQueued = true;
                            start.add(room);
                        } else {
                            room.version++;
                            notifyRoomUpdated(room);
                        }
                    }
                }
                if (room.roundId == null || room.journaling) continue;
                try {
                    if (!engine.isRoundActive(room.roundId)) {
                        publish.add(room);
                    } else if (System.nanoTime() - room.lastActionNanos
                            >= TimeUnit.SECONDS.toNanos(turnTimeoutSeconds)) {
                        Long current = engine.getPublicSnapshot(room.roundId).currentTurnUserId();
                        if (current != null) {
                            engine.handleAction(room.roundId, current, BetAction.FOLD, null);
                            room.lastActionNanos = System.nanoTime();
                            room.afkUsers.add(current);
                            publish.add(room);
                        }
                    }
                } catch (GameEngineException e) {
                    System.err.println("[GAME] Lỗi tick roomId=" + room.id + " code=" + e.getErrorCode());
                }
            }
        }
        for (Room room : publish) publishGameUpdate(room);
        for (Room room : start) {
            try {
                autoStarts.execute(() -> {
                    long owner;
                    synchronized (this) { owner = room.ownerId; }
                    try { start(owner); }
                    catch (BUSException | RuntimeException e) {
                        synchronized (this) {
                            sendSystemChat(room, "Chưa thể tự động bắt đầu ván; vui lòng kiểm tra trạng thái sẵn sàng.");
                        }
                        System.err.println("[ROOM] Tự động bắt đầu lỗi roomId=" + room.id
                                + " cause=" + e.getClass().getSimpleName());
                    } finally {
                        synchronized (this) { room.autoStartQueued = false; }
                    }
                });
            } catch (RejectedExecutionException e) {
                synchronized (this) { room.autoStartQueued = false; }
            }
        }
    }
    @Override
    public synchronized void chat(long userId, String content) throws BUSException {
        Room room = requireUserRoom(userId);
        if (content == null || content.isBlank()) {
            throw fail(ErrorCode.INVALID_REQUEST, "Nội dung tin nhắn không được để trống");
        }
        if (content.length() > 500) {
            throw fail(ErrorCode.MESSAGE_TOO_LONG, "Tin nhắn tối đa 500 ký tự");
        }

        long now = System.nanoTime();
        ArrayDeque<Long> timestamps = chatTimes.computeIfAbsent(userId, k -> new ArrayDeque<>());
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= TimeUnit.SECONDS.toNanos(10)) {
            timestamps.removeFirst();
        }

        if (timestamps.size() >= 5) {
            throw fail(ErrorCode.RATE_LIMIT_EXCEEDED, "Bạn gửi tin nhắn quá nhanh (tối đa 5 tin / 10 giây)");
        }
        timestamps.addLast(now);

        String displayName = room.members.get(userId).user.displayName();
        ChatMessage chatMessage = new ChatMessage(
                userId,
                displayName,
                content.trim(),
                "TEXT",
                System.currentTimeMillis()
        );
        synchronized (room.recentChats) {
            room.recentChats.addLast(chatMessage);
            if (room.recentChats.size() > 50) {
                room.recentChats.removeFirst();
            }
        }
        events.chatMessage(room.id, chatMessage, getRoomMemberIds(room));
    }

    @Override
    public void onDisconnected(long userId) {
        try {
            leave(userId);
        } catch (BUSException e) {
            if (e.getErrorCode() != ErrorCode.NOT_IN_ROOM) {
                System.err.println("[ROOM] Dọn dẹp lỗi khi ngắt kết nối userId=" + userId);
            }
        }
        synchronized (this) { chatTimes.remove(userId); }
    }

    @Override
    public void onConnectionLost(long userId) {
        onDisconnected(userId);
    }

    private void ensureOutsideRoom(long userId) throws BUSException {
        if (membership.containsKey(userId)) {
            throw fail(ErrorCode.ALREADY_IN_ROOM, "Bạn đang ở trong một bàn chơi khác");
        }
    }

    private Room requireUserRoom(long userId) throws BUSException {
        Long roomId = membership.get(userId);
        if (roomId == null) {
            throw fail(ErrorCode.NOT_IN_ROOM, "Bạn chưa tham gia bàn chơi nào");
        }
        Room room = rooms.get(roomId);
        if (room == null) {
            throw fail(ErrorCode.ROOM_NOT_FOUND, "Không tìm thấy bàn chơi");
        }
        return room;
    }

    private static List<Long> getRoomMemberIds(Room room) {
        return List.copyOf(room.members.keySet());
    }

    private void notifyRoomUpdated(Room room) {
        events.roomUpdated(createRoomSnapshot(room), getRoomMemberIds(room));
        events.lobbyUpdated(listRooms());
    }

    private void sendSystemChat(Room room, String text) {
        ChatMessage msg = ChatMessage.system(text);
        synchronized (room.recentChats) {
            room.recentChats.addLast(msg);
            if (room.recentChats.size() > 50) {
                room.recentChats.removeFirst();
            }
        }
        events.chatMessage(room.id, msg, getRoomMemberIds(room));
    }

    private void broadcastToRoom(Room room, PacketType type, Object data) {
        events.packet(Packet.serverPush(type, data), getRoomMemberIds(room));
    }

    private void sendPrivatePacket(long userId, PacketType type, Object data) {
        events.packet(Packet.serverPush(type, data), List.of(userId));
    }

    private RoomSnapshot createRoomSnapshot(Room room) {
        List<RoomSnapshot.PlayerSlot> slots = room.members.values().stream()
                .map(m -> {
                    String status = m.user.balance().compareTo(room.settings.betAmount()) >= 0
                            ? "PLAYING"
                            : "SPECTATOR";
                    return new RoomSnapshot.PlayerSlot(
                            m.user.userId(),
                            m.user.displayName(),
                            m.seat,
                            m.ready,
                            status
                    );
                })
                .toList();

        int countdown = 0;
        if (room.autoStartDeadlineNanos > 0) {
            long remainingNanos = room.autoStartDeadlineNanos - resultClock.getAsLong();
            if (remainingNanos > 0) {
                countdown = (int) Math.ceil(remainingNanos / 1_000_000_000.0);
            }
        }

        return new RoomSnapshot(
                room.id,
                "R" + room.id,
                room.settings.roomName(),
                room.state,
                room.ownerId,
                room.settings.minPlayers(),
                room.settings.maxPlayers(),
                room.settings.betAmount(),
                slots,
                room.version,
                countdown
        );
    }

    public synchronized List<AdminRoomDetail> getAdminRoomDetails() {
        List<AdminRoomDetail> details = new ArrayList<>();
        for (long id = 1; id <= TABLE_COUNT; id++) {
            Room r = rooms.get(id);
            if (r == null) {
                continue;
            }
            BigDecimal pot = BigDecimal.ZERO;
            if (r.roundId != null) {
                try {
                    var betState = engine.getBetSnapshot(r.roundId);
                    if (betState != null) {
                        pot = betState.mainPot();
                    }
                } catch (Exception ignored) {
                }
            }
            List<String> names = r.members.values().stream()
                    .map(m -> m.user.displayName())
                    .toList();
            details.add(new AdminRoomDetail(
                    r.id,
                    r.settings.roomName(),
                    r.state,
                    r.settings.betAmount(),
                    pot,
                    r.members.size(),
                    r.settings.maxPlayers(),
                    names
            ));
        }
        return details;
    }

    public synchronized int getActiveRoomCount() {
        int count = 0;
        for (Room r : rooms.values()) {
            if (!r.members.isEmpty() || !"WAITING".equals(r.state)) {
                count++;
            }
        }
        return count;
    }

    public void forceResetRoom(long roomId, String reason) throws BUSException {
        Room r;
        synchronized (this) {
            r = rooms.get(roomId);
            if (r == null) {
                throw fail(ErrorCode.ROOM_NOT_FOUND, "Không tìm thấy bàn: " + roomId);
            }
            if (r.journaling || r.autoStartQueued || r.members.keySet().stream().anyMatch(startingUsers::contains)) {
                throw fail(ErrorCode.INVALID_STATE, "Bàn đang bắt đầu hoặc lưu kết quả; vui lòng thử lại sau");
            }
            if (r.roundId != null) {
                try { engine.cancelRound(r.roundId); }
                catch (GameEngineException e) { throw fail(e.getErrorCode(), e.getMessage()); }
            }
        }
        publishGameUpdate(r);
        synchronized (this) {
            if (r.roundId != null) {
                throw fail(ErrorCode.INTERNAL_ERROR, "Chưa ghi được kết quả hủy ván; bàn được giữ để thử lại an toàn");
            }
            r.state = "WAITING";
            r.resultUntilNanos = 0;
            r.resultSent = false;
            r.hands = List.of();
            r.autoStartDeadlineNanos = 0;
            r.afkUsers.clear();
            for (Member m : r.members.values()) {
                m.ready = false;
            }
            r.version++;

            notifyRoomUpdated(r);
            sendSystemChat(r, "Bàn đã được Admin giải tán / đặt lại: " + (reason != null ? reason : "Lý do quản trị"));
            events.lobbyUpdated(listRooms());
        }
    }

    public synchronized AdminLiveMatchDetail getAdminRoomLiveDetail(long roomId) throws BUSException {
        Room r = rooms.get(roomId);
        if (r == null) {
            throw fail(ErrorCode.ROOM_NOT_FOUND, "Không tìm thấy bàn: " + roomId);
        }

        BigDecimal pot = BigDecimal.ZERO;
        Long currentTurnUserId = null;
        String currentTurnDisplayName = null;
        int stage = 1;

        if (r.roundId != null) {
            try {
                var betSnapshot = engine.getBetSnapshot(r.roundId);
                if (betSnapshot != null) {
                    pot = betSnapshot.mainPot();
                    currentTurnUserId = betSnapshot.currentTurnUserId();
                }
                var pub = engine.getPublicSnapshot(r.roundId);
                if (pub != null && currentTurnUserId == null) {
                    currentTurnUserId = pub.currentTurnUserId();
                }
            } catch (Exception ignored) {
            }
        }

        if (currentTurnUserId != null) {
            Member turnMember = r.members.get(currentTurnUserId);
            if (turnMember != null) {
                currentTurnDisplayName = turnMember.user.displayName();
            }
        }

        List<AdminLivePlayerHand> playerHands = new ArrayList<>();
        for (Member m : r.members.values()) {
            long uid = m.user.userId();
            BigDecimal bet = BigDecimal.ZERO;
            String status = m.ready ? "READY" : "WAITING";
            List<Card> cards = List.of();
            String evaluation;

            if (r.roundId != null) {
                try {
                    var playerState = engine.getPlayerSnapshot(r.roundId, uid);
                    if (playerState != null) {
                        bet = playerState.myCurrentBet();
                        if (playerState.folded()) {
                            status = "FOLDED";
                        } else if (playerState.allIn()) {
                            status = "ALL_IN";
                        } else {
                            status = "ACTIVE";
                        }
                    }
                    cards = engine.getPlayerCards(r.roundId, uid);
                } catch (Exception ignored) {
                }

                if ("FOLDED".equals(status)) {
                    evaluation = "Đã úp bài";
                } else if (cards != null && cards.size() == 3) {
                    evaluation = describeHand(cards, handEvaluator);
                } else {
                    evaluation = "Đang chờ ván mới";
                }
            } else {
                evaluation = m.ready ? "Đã sẵn sàng" : "Chưa sẵn sàng";
            }

            playerHands.add(new AdminLivePlayerHand(
                    uid,
                    m.user.username(),
                    m.user.displayName(),
                    m.seat,
                    m.user.balance(),
                    bet,
                    status,
                    cards != null ? cards : List.of(),
                    evaluation
            ));
        }

        List<ChatMessage> chats;
        synchronized (r.recentChats) {
            chats = List.copyOf(r.recentChats);
        }

        return new AdminLiveMatchDetail(
                r.id,
                r.settings.roomName(),
                r.state,
                r.settings.betAmount(),
                pot,
                r.roundId,
                currentTurnUserId,
                currentTurnDisplayName,
                stage,
                r.members.size(),
                r.settings.maxPlayers(),
                playerHands,
                chats
        );
    }

    private static String describeHand(List<Card> cards, HandEvaluator evaluator) {
        if (cards == null || cards.size() != 3) {
            return "Chưa đủ 3 lá";
        }
        try {
            Hand hand = evaluator.evaluate(cards);
            return switch (hand.handType()) {
                case SAP -> "Sáp " + cards.get(0).rank().displayName();
                case LIENG -> "Liêng";
                case BO_DOI -> "Bồ đội";
                case DIEM -> hand.score() + " Điểm";
            };
        } catch (Exception e) {
            return "Bài không hợp lệ";
        }
    }

    private static BUSException fail(ErrorCode code, String message) {
        return new BUSException(code, message);
    }

    @Override
    public void close() {
        autoStarts.shutdownNow();
        try {
            autoStarts.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
