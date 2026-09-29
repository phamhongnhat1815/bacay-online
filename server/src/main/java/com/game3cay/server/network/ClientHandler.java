package com.game3cay.server.network;

import com.game3cay.shared.network.*;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class ClientHandler implements Runnable {
    private final Socket socket;
    private final AtomicLong lastPong = new AtomicLong();
    private final AtomicReference<String> waitingPong =
            new AtomicReference<>();

    private ObjectOutputStream output;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        var heartbeat = Executors.newSingleThreadScheduledExecutor();

        try (socket) {
            socket.setSoTimeout(5000);

            output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();

            ObjectInputStream input = PacketIO.openInput(socket);
            socket.setSoTimeout(0);

            lastPong.set(System.nanoTime());

            send(new Packet(
                    PacketType.SERVER_NOTICE, null,
                    "Đã kết nối server M1"
            ));

            heartbeat.scheduleAtFixedRate(
                    this::tick, 0, 5, TimeUnit.SECONDS
            );

            while (!socket.isClosed()) {
                handle(PacketIO.read(input));
            }

        } catch (IOException | ClassNotFoundException | RuntimeException e) {
            System.out.println(
                    "Đóng kết nối: " + e.getClass().getSimpleName()
            );
        } finally {
            heartbeat.shutdownNow();

            // M2/M3: Khánh bổ sung giải phóng phiên
            // và cho người chơi rời phòng tại đây.
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

        if (packet.getRequestId() == null) {
            error(null, ErrorCode.INVALID_REQUEST,
                    "Request phải có requestId");
            return;
        }

        switch (packet.getType()) {
            case ECHO_REQUEST ->
                send(new Packet(
                        PacketType.ACK, packet.getRequestId(), null
                ));

            case LOGIN, REGISTER, LOGOUT,
                 ROOM_LIST, CREATE_ROOM, JOIN_ROOM, LEAVE_ROOM,
                 SET_READY, START_GAME_REQUEST, CHAT_MESSAGE ->
                error(
                        packet.getRequestId(),
                        ErrorCode.NOT_IMPLEMENTED,
                        "Chức năng chưa được triển khai: " + packet.getType()
                );

            default ->
                error(
                        packet.getRequestId(),
                        ErrorCode.INVALID_REQUEST,
                        "Loại thông điệp không được gửi từ client"
                );
        }
    }

    private void error(String id, ErrorCode code, String message) {
        send(new Packet(
                PacketType.ERROR, id,
                new Packet.ErrorPayload(code, message)
        ));
    }

    private void send(Packet packet) {
        try {
            PacketIO.write(output, packet);
        } catch (IOException e) {
            close();
        }
    }

    private void close() {
        try {
            socket.close();
        } catch (IOException ignored) {}
    }
}