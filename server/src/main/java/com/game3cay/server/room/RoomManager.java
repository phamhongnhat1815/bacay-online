package com.game3cay.server.room;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.game3cay.server.bus.BUSException;
import com.game3cay.server.bus.UserDTO;
import com.game3cay.shared.model.ChatMessage;
import com.game3cay.shared.model.CreateRoomRequest;
import com.game3cay.shared.model.JoinRoomRequest;
import com.game3cay.shared.model.RoomSnapshot;
import com.game3cay.shared.model.RoomSummary;
import com.game3cay.shared.network.ErrorCode;

public final class RoomManager implements RoomService {
    private final RoomEvents events;

    private final Map<Long, Room> rooms = new LinkedHashMap<>();
    private final Map<Long, Long> membership = new HashMap<>();
    private final Map<Long, ArrayDeque<Long>> chatTimes = new HashMap<>();

    private long nextRoomId;

    private static final class Member {
        final UserDTO user;
        final int seat;
        boolean ready;

        Member(UserDTO user, int seat) {
            this.user = user;
            this.seat = seat;
        }
    }

    private static final class Room {
        final long id;
        final CreateRoomRequest settings;

        final LinkedHashMap<Long, Member> members =
                new LinkedHashMap<>();

        long owner;
        long version;
        /** Trạng thái phòng: WAITING / PLAYING / FINISHED */
        String state = "WAITING";

        Room(
                long id,
                UserDTO owner,
                CreateRoomRequest settings
        ) {
            this.id = id;
            this.owner = owner.userId();
            this.settings = settings;
        }
    }

    public RoomManager(RoomEvents events) {
        this.events = Objects.requireNonNull(events);
    }

    @Override
    public synchronized List<RoomSummary> listRooms() {
        var result = new ArrayList<RoomSummary>();

        for (Room r : rooms.values()) {
            result.add(new RoomSummary(
                    r.id,
                    "R" + r.id,
                    r.settings.roomName(),
                    r.state,
                    r.members.size(),
                    r.settings.maxPlayers(),
                    r.settings.betAmount()
            ));
        }

        return result;
    }

    @Override
    public synchronized RoomSnapshot create(
            UserDTO actor,
            CreateRoomRequest request
    ) throws BUSException {
        requireOutside(actor.userId());

        if (rooms.size() >= 100) {
            throw fail(
                    ErrorCode.INVALID_STATE,
                    "Đã đạt giới hạn 100 phòng"
            );
        }

        if (request.roomName() == null
                || request.roomName().isBlank()
                || request.roomName().length() > 100
                || request.minPlayers() < 2
                || request.maxPlayers() < request.minPlayers()
                || request.maxPlayers() > 8) {

            throw fail(
                    ErrorCode.INVALID_REQUEST,
                    "Thông tin tạo phòng không hợp lệ"
            );
        }

        // betAmount tuỳ chọn — game hiện tại không bắt buộc đặt cược.
        if (request.betAmount() != null
                && (request.betAmount().signum() < 0
                    || request.betAmount().precision() > 15
                    || request.betAmount().scale() < 0
                    || request.betAmount().scale() > 2)) {

            throw fail(
                    ErrorCode.INVALID_REQUEST,
                    "betAmount không hợp lệ"
            );
        }

        Room r = new Room(++nextRoomId, actor, request);
        rooms.put(r.id, r);

        return add(r, actor);
    }

    @Override
    public synchronized RoomSnapshot join(
            UserDTO actor,
            JoinRoomRequest request
    ) throws BUSException {
        requireOutside(actor.userId());

        Room r = rooms.get(request.roomId());

        if (r == null) {
            throw fail(
                    ErrorCode.ROOM_NOT_FOUND,
                    "Phòng không còn tồn tại"
            );
        }

        if (!"WAITING".equals(r.state)) {
            throw fail(
                    ErrorCode.ROOM_NOT_WAITING,
                    "Phòng đang trong trận, không thể vào"
            );
        }

        if (r.members.size() >= r.settings.maxPlayers()) {
            throw fail(
                    ErrorCode.ROOM_FULL,
                    "Phòng đã đủ người"
            );
        }

        return add(r, actor);
    }

    private RoomSnapshot add(Room r, UserDTO actor) {
        int seat = 1;
        Set<Integer> occupied = new HashSet<>();

        for (Member m : r.members.values()) {
            occupied.add(m.seat);
        }

        while (occupied.contains(seat)) {
            seat++;
        }

        r.members.put(
                actor.userId(),
                new Member(actor, seat)
        );
        membership.put(actor.userId(), r.id);
        r.version++;

        RoomSnapshot snapshot = snapshot(r);

        events.roomUpdated(snapshot, recipients(r));
        events.chatMessage(
                r.id,
                ChatMessage.system(
                        actor.displayName() + " đã vào phòng"
                ),
                recipients(r)
        );

        return snapshot;
    }

    @Override
    public synchronized void leave(long userId) throws BUSException {
        Room r = requireRoom(userId);

        Member removed = r.members.remove(userId);
        membership.remove(userId);

        if (r.members.isEmpty()) {
            rooms.remove(r.id);
            return;
        }

        if (r.owner == userId) {
            r.owner = r.members.keySet().iterator().next();
        }

        r.version++;

        events.roomUpdated(snapshot(r), recipients(r));
        events.chatMessage(
                r.id,
                ChatMessage.system(
                        removed.user.displayName() + " đã rời phòng"
                ),
                recipients(r)
        );
    }

    @Override
    public synchronized RoomSnapshot setReady(
            long userId,
            boolean ready
    ) throws BUSException {
        Room r = requireRoom(userId);
        Member member = r.members.get(userId);

        if (member.ready != ready) {
            member.ready = ready;
            r.version++;

            events.roomUpdated(snapshot(r), recipients(r));
        }

        return snapshot(r);
    }

    @Override
    public synchronized void chat(
            long userId,
            String content
    ) throws BUSException {
        Room r = requireRoom(userId);

        if (content == null || content.isBlank()) {
            throw fail(
                    ErrorCode.INVALID_REQUEST,
                    "Tin nhắn không được trống"
            );
        }

        if (content.length() > 500) {
            throw fail(
                    ErrorCode.MESSAGE_TOO_LONG,
                    "Tin nhắn tối đa 500 ký tự"
            );
        }

        long now = System.nanoTime();

        var times = chatTimes.computeIfAbsent(
                userId,
                id -> new ArrayDeque<>()
        );

        while (!times.isEmpty()
                && now - times.peekFirst()
                    >= TimeUnit.SECONDS.toNanos(10)) {

            times.removeFirst();
        }

        if (times.size() >= 5) {
            throw fail(
                    ErrorCode.RATE_LIMIT_EXCEEDED,
                    "Chỉ được gửi 5 tin trong 10 giây"
            );
        }

        times.addLast(now);

        UserDTO sender = r.members.get(userId).user;

        ChatMessage message = new ChatMessage(
                sender.userId(),
                sender.displayName(),
                content.trim(),
                "TEXT",
                System.currentTimeMillis()
        );

        events.chatMessage(
                r.id,
                message,
                recipients(r)
        );
    }

    // M3 kiểm tra điều kiện; chạy Engine thuộc M4.
    public synchronized void checkStart(long userId)
            throws BUSException {

        Room r = requireRoom(userId);

        if (r.owner != userId) {
            throw fail(
                    ErrorCode.NOT_ROOM_OWNER,
                    "Chỉ chủ phòng được bắt đầu"
            );
        }

        if (r.members.size() < r.settings.minPlayers()) {
            throw fail(
                    ErrorCode.NOT_ENOUGH_PLAYERS,
                    "Chưa đủ người chơi"
            );
        }

        if (r.members.values().stream().anyMatch(m -> !m.ready)) {
            throw fail(
                    ErrorCode.NOT_ALL_READY,
                    "Có người chưa sẵn sàng"
            );
        }
    }

    @Override
    public synchronized void onDisconnected(long userId) {
        if (membership.containsKey(userId)) {
            try {
                leave(userId);
            } catch (BUSException ignored) {
            }
        }

        chatTimes.remove(userId);
    }

    private void requireOutside(long userId) throws BUSException {
        if (membership.containsKey(userId)) {
            throw fail(
                    ErrorCode.ALREADY_IN_ROOM,
                    "Bạn đang ở trong một phòng"
            );
        }
    }

    private Room requireRoom(long userId) throws BUSException {
        Long id = membership.get(userId);

        if (id == null) {
            throw fail(
                    ErrorCode.NOT_IN_ROOM,
                    "Bạn chưa ở trong phòng"
            );
        }

        Room r = rooms.get(id);

        if (r == null) {
            throw fail(
                    ErrorCode.ROOM_NOT_FOUND,
                    "Phòng không còn tồn tại"
            );
        }

        return r;
    }

    private static List<Long> recipients(Room r) {
        return List.copyOf(r.members.keySet());
    }

    private static RoomSnapshot snapshot(Room r) {
        var players = new ArrayList<RoomSnapshot.PlayerSlot>();

        for (Member m : r.members.values()) {
            players.add(new RoomSnapshot.PlayerSlot(
                    m.user.userId(),
                    m.user.displayName(),
                    m.seat,
                    m.ready,
                    "ACTIVE"   // đang ngồi trong phòng chờ
            ));
        }

        return new RoomSnapshot(
                r.id,
                "R" + r.id,
                r.settings.roomName(),
                r.state,   // dùng state thực, không hardcode "WAITING"
                r.owner,
                r.settings.minPlayers(),
                r.settings.maxPlayers(),
                r.settings.betAmount(),
                List.copyOf(players),
                r.version
        );
    }

    private static BUSException fail(
            ErrorCode code,
            String message
    ) {
        return new BUSException(code, message);
    }
}