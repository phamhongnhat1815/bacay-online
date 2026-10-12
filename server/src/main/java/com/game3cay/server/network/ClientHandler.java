package com.game3cay.server.network;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.game3cay.server.bus.BUSException;
import com.game3cay.server.bus.UserBUS;
import com.game3cay.server.bus.UserDTO;
import com.game3cay.server.room.RoomManager;
import com.game3cay.shared.model.AuthRequest;
import com.game3cay.shared.model.AuthResult;
import com.game3cay.shared.model.ChatMessage;
import com.game3cay.shared.model.CreateRoomRequest;
import com.game3cay.shared.model.JoinRoomRequest;
import com.game3cay.shared.model.RegisterRequest;
import com.game3cay.shared.network.ErrorCode;
import com.game3cay.shared.network.Packet;
import com.game3cay.shared.network.PacketIO;
import com.game3cay.shared.network.PacketType;

public final class ClientHandler implements Runnable {
    private final Socket socket;
    private final UserBUS users;
    private final SessionRegistry sessions;
    private final RoomManager rooms;

    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong lastPong = new AtomicLong();

    private final AtomicReference<String> waitingPong =
            new AtomicReference<>();

    private final ExecutorService sender = new ThreadPoolExecutor(
            1, 1, 0,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(128),
            task -> daemon(task, "server-sender")
    );

    private final ScheduledExecutorService heartbeat =
            Executors.newSingleThreadScheduledExecutor(
                    task -> daemon(task, "server-heartbeat")
            );

    private ObjectOutputStream output;
    private UserDTO user;

    public ClientHandler(
            Socket socket,
            UserBUS users,
            SessionRegistry sessions,
            RoomManager rooms
    ) {
        this.socket = socket;
        this.users = users;
        this.sessions = sessions;
        this.rooms = rooms;
    }

    @Override
    public void run() {
        try (socket) {
            socket.setSoTimeout(5000);

            output = new ObjectOutputStream(
                    socket.getOutputStream()
            );
            output.flush();

            ObjectInputStream input = PacketIO.openInput(socket);
            socket.setSoTimeout(0);

            lastPong.set(System.nanoTime());

            send(new Packet(
                    PacketType.SERVER_NOTICE,
                    null,
                    "Đã kết nối server M1–M3"
            ));

            heartbeat.scheduleAtFixedRate(
                    this::tick, 0, 5, TimeUnit.SECONDS
            );

            while (!closed.get()) {
                handle(PacketIO.read(input));
            }
        } catch (IOException
                | ClassNotFoundException
                | RuntimeException e) {

            System.out.println(
                    "Đóng kết nối: " + e.getClass().getSimpleName()
            );
        } finally {
            close();
            clearSession();
        }
    }

    private void tick() {
        if (System.nanoTime() - lastPong.get()
                > TimeUnit.SECONDS.toNanos(15)) {

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
            error(
                    null,
                    ErrorCode.INVALID_REQUEST,
                    "Request phải có requestId"
            );
            return;
        }

        try {
            switch (packet.getType()) {
                case ECHO_REQUEST -> ack(id);

                case REGISTER -> {
                    if (user != null) {
                        throw fail(
                                ErrorCode.INVALID_STATE,
                                "Hãy đăng xuất trước khi đăng ký"
                        );
                    }

                    RegisterRequest r =
                            (RegisterRequest) packet.getData();

                    UserDTO created = userBUS().register(
                            r.username(),
                            r.password(),
                            r.displayName(),
                            r.email()
                    );

                    // Đăng ký không tạo phiên đăng nhập.
                    auth(id, created);
                }

                case LOGIN -> {
                    if (user != null) {
                        throw fail(
                                ErrorCode.ALREADY_LOGGED_IN,
                                "Kết nối này đã đăng nhập"
                        );
                    }

                    AuthRequest r =
                            (AuthRequest) packet.getData();

                    UserDTO authenticated = userBUS().authenticate(
                            r.username(),
                            r.password()
                    );

                    if (!sessions.bind(
                            authenticated.userId(), this
                    )) {
                        throw fail(
                                ErrorCode.ALREADY_LOGGED_IN,
                                "Tài khoản đang đăng nhập ở kết nối khác"
                        );
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
                            PacketType.ROOM_LIST_RESULT,
                            id,
                            rooms.listRooms()
                    ));
                }

                case CREATE_ROOM -> send(new Packet(
                        PacketType.ROOM_UPDATED,
                        id,
                        rooms.create(
                                requireUser(),
                                (CreateRoomRequest) packet.getData()
                        )
                ));

                case JOIN_ROOM -> send(new Packet(
                        PacketType.ROOM_UPDATED,
                        id,
                        rooms.join(
                                requireUser(),
                                (JoinRoomRequest) packet.getData()
                        )
                ));

                case LEAVE_ROOM -> {
                    rooms.leave(requireUser().userId());
                    ack(id);
                }

                case SET_READY -> send(new Packet(
                        PacketType.ROOM_UPDATED,
                        id,
                        rooms.setReady(
                                requireUser().userId(),
                                (Boolean) packet.getData()
                        )
                ));

                case CHAT_MESSAGE -> {
                    UserDTO actor = requireUser();

                    if (!(packet.getData() instanceof ChatMessage message)
                            || !"TEXT".equals(message.type())) {

                        throw fail(
                                ErrorCode.INVALID_REQUEST,
                                "Client chỉ được gửi chat TEXT"
                        );
                    }

                    // Không dùng senderId/tên/timestamp client khai.
                    rooms.chat(actor.userId(), message.content());
                    ack(id);
                }

                case START_GAME_REQUEST -> {
                    rooms.checkStart(requireUser().userId());

                    throw fail(
                            ErrorCode.NOT_IMPLEMENTED,
                            "Chức năng bắt đầu ván chưa sẵn sàng"
                    );
                }

                default -> error(
                        id,
                        ErrorCode.INVALID_REQUEST,
                        "Loại thông điệp không được gửi từ client"
                );
            }
        } catch (BUSException e) {
            error(id, e.getErrorCode(), e.getMessage());
        } catch (RuntimeException e) {
            error(
                    id,
                    ErrorCode.INTERNAL_ERROR,
                    "Không thể xử lý yêu cầu"
            );
        }
    }

    private UserBUS userBUS() throws BUSException {
        if (users == null) {
            throw fail(
                    ErrorCode.INTERNAL_ERROR,
                    "Server chưa được cấu hình dữ liệu tài khoản"
            );
        }

        return users;
    }

    private UserDTO requireUser() throws BUSException {
        if (user == null) {
            throw fail(
                    ErrorCode.UNAUTHENTICATED,
                    "Bạn cần đăng nhập trước"
            );
        }

        return user;
    }

    private void auth(String id, UserDTO u) {
        send(new Packet(
                PacketType.AUTH_RESULT,
                id,
                new AuthResult(
                        true,
                        u.userId(),
                        u.username(),
                        u.displayName(),
                        u.balance(),
                        null,
                        "Thành công"
                )
        ));
    }

    private void clearSession() {
        if (user == null) {
            return;
        }

        UserDTO old = user;
        user = null;

        rooms.onDisconnected(old.userId());
        sessions.release(old.userId(), this);
    }

    private void ack(String id) {
        send(new Packet(PacketType.ACK, id, null));
    }

    private void error(
            String id,
            ErrorCode code,
            String message
    ) {
        send(new Packet(
                PacketType.ERROR,
                id,
                new Packet.ErrorPayload(code, message)
        ));
    }

    private static BUSException fail(
            ErrorCode code,
            String message
    ) {
        return new BUSException(code, message);
    }

    // Chỉ xếp vào hàng đợi; ghi socket chạy ở sender.
    public void send(Packet packet) {
        if (closed.get()) {
            return;
        }

        try {
            sender.execute(() -> {
                if (closed.get()) {
                    return;
                }

                try {
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

    private static Thread daemon(
            Runnable task,
            String name
    ) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }
}