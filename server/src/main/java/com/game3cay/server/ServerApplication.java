package com.game3cay.server;

import java.net.ServerSocket;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.game3cay.server.bus.JdbcUserBUS;
import com.game3cay.server.bus.UserBUS;
import com.game3cay.server.network.ClientHandler;
import com.game3cay.server.network.SessionRegistry;
import com.game3cay.server.room.RoomEvents;
import com.game3cay.server.room.RoomManager;
import com.game3cay.shared.model.ChatMessage;
import com.game3cay.shared.model.RoomChatEvent;
import com.game3cay.shared.model.RoomSnapshot;
import com.game3cay.shared.network.Packet;
import com.game3cay.shared.network.PacketType;

public final class ServerApplication {
    public static void main(String[] args) throws Exception {
        int port = args.length == 0
                ? 8888
                : Integer.parseInt(args[0]);

        UserBUS configured = null;

        try {
            configured = JdbcUserBUS.fromEnvironment();
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.out.println(
                    "Chưa cấu hình DB: M1 vẫn chạy; "
                    + "M2 cần cấu hình và khởi động lại server."
            );
        }

        final UserBUS users = configured;
        SessionRegistry sessions = new SessionRegistry();

        RoomManager rooms = new RoomManager(new RoomEvents() {
            @Override
            public void roomUpdated(
                    RoomSnapshot room,
                    List<Long> recipients
            ) {
                sessions.broadcast(
                        recipients,
                        new Packet(
                                PacketType.ROOM_UPDATED,
                                null,
                                room
                        )
                );
            }

            @Override
            public void chatMessage(
                    long roomId,
                    ChatMessage message,
                    List<Long> recipients
            ) {
                PacketType type = "SYSTEM".equals(message.type())
                        ? PacketType.CHAT_SYSTEM
                        : PacketType.CHAT_MESSAGE;

                sessions.broadcast(
                        recipients,
                        new Packet(
                                type,
                                null,
                                new RoomChatEvent(roomId, message)
                        )
                );
            }
        });

        var workers = new ThreadPoolExecutor(
                0, 32, 60,
                TimeUnit.SECONDS,
                new SynchronousQueue<>()
        );

        Set<ClientHandler> connections =
                ConcurrentHashMap.newKeySet();

        try (var server = new ServerSocket(port)) {
            System.out.println(
                    "TCP server đang nghe cổng " + port
            );

            while (!server.isClosed()) {
                ClientHandler handler = new ClientHandler(
                        server.accept(),
                        users,
                        sessions,
                        rooms
                );

                connections.add(handler);

                try {
                    workers.execute(() -> {
                        try {
                            handler.run();
                        } finally {
                            connections.remove(handler);
                        }
                    });
                } catch (RejectedExecutionException e) {
                    connections.remove(handler);
                    handler.close();
                }
            }
        } finally {
            connections.forEach(ClientHandler::close);
            workers.shutdownNow();
        }
    }
}