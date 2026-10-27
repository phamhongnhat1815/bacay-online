package com.game3cay.server;

import com.game3cay.server.bus.JdbcGameBUS;
import com.game3cay.server.bus.JdbcUserBUS;
import com.game3cay.server.dal.ConnectionProvider;
import com.game3cay.server.dal.DatabaseConfig;
import com.game3cay.server.game.ResultPersistence;
import com.game3cay.server.network.ClientHandler;
import com.game3cay.server.network.SessionRegistry;
import com.game3cay.server.room.RoomEvents;
import com.game3cay.server.room.RoomManager;
import com.game3cay.shared.model.ChatMessage;
import com.game3cay.shared.model.RoomSnapshot;
import com.game3cay.shared.model.RoomSummary;
import com.game3cay.shared.network.Packet;
import com.game3cay.shared.network.PacketType;
import com.game3cay.shared.network.RuntimeSettings;

import java.net.ServerSocket;
import java.net.SocketException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Điểm khởi chạy chính (Main Entry Point) của máy chủ game Ba Cây Online.
 * <p>
 * Khởi tạo toàn bộ các thành phần:
 * <ul>
 *   <li>Kết nối MySQL (DAL/BUS) và kiểm tra schema CSDL.</li>
 *   <li>Hàng đợi ghi đĩa độ bền cao {@link ResultPersistence}.</li>
 *   <li>Bảng phiên kết nối {@link SessionRegistry} và quản lý bàn chơi {@link RoomManager}.</li>
 *   <li>Luồng định kỳ tick kiểm tra timeout lượt chơi và chuyển trạng thái bàn.</li>
 *   <li>Server socket TCP tiếp nhận kết nối người chơi.</li>
 *   <li>Shutdown hook đảm bảo đóng sạch socket, luồng và các tệp tin khi dừng server.</li>
 * </ul>
 */
public final class ServerApplication {

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(System.out, true, java.nio.charset.StandardCharsets.UTF_8));
        System.setErr(new java.io.PrintStream(System.err, true, java.nio.charset.StandardCharsets.UTF_8));
        int port = (args.length == 0)
                ? RuntimeSettings.integer("server.properties", "server.port", 8888, 1, 65535)
                : Integer.parseInt(args[0]);

        int maxConnections = RuntimeSettings.integer(
                "server.properties", "server.maxConnections", 256, 1, 256);

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Cổng mạng (Port) phải nằm trong khoảng từ 1 đến 65535");
        }

        ConnectionProvider connections = DatabaseConfig.fromEnvironment();
        JdbcUserBUS userBus = new JdbcUserBUS(connections);
        JdbcGameBUS gameBus = new JdbcGameBUS(connections);

        // Kiểm tra sớm schema CSDL: dừng chương trình ngay nếu chưa chạy file SQL setup
        gameBus.checkSchema();

        Path dataDir = Path.of(System.getenv().getOrDefault("BACAY_DATA_DIR", "data/pending-results"));
        ResultPersistence persistence = new ResultPersistence(gameBus, dataDir);
        SessionRegistry sessionRegistry = new SessionRegistry();

        RoomManager roomManager = new RoomManager(
                new RoomEvents() {
                    @Override
                    public void roomUpdated(RoomSnapshot snapshot, List<Long> recipients) {
                        sessionRegistry.broadcast(recipients, Packet.serverPush(PacketType.ROOM_UPDATED, snapshot));
                    }

                    @Override
                    public void chatMessage(long roomId, ChatMessage message, List<Long> recipients) {
                        PacketType type = "SYSTEM".equals(message.type())
                                ? PacketType.CHAT_SYSTEM
                                : PacketType.CHAT_MESSAGE;
                        sessionRegistry.broadcast(recipients, Packet.serverPush(type, new com.game3cay.shared.model.RoomChatEvent(roomId, message)));
                    }

                    @Override
                    public void packet(Packet packet, List<Long> recipients) {
                        sessionRegistry.broadcast(recipients, packet);
                    }

                    @Override
                    public void lobbyUpdated(List<RoomSummary> tables) {
                        sessionRegistry.broadcastAll(Packet.serverPush(PacketType.ROOM_LIST_RESULT, tables));
                    }
                },
                userBus,
                persistence
        );

        ThreadPoolExecutor clientWorkers = new ThreadPoolExecutor(
                0,
                maxConnections,
                60L,
                TimeUnit.SECONDS,
                new SynchronousQueue<>()
        );

        Set<ClientHandler> activeHandlers = ConcurrentHashMap.newKeySet();
        ScheduledExecutorService roomClock = Executors.newSingleThreadScheduledExecutor();
        roomClock.scheduleWithFixedDelay(roomManager::tick, 1, 1, TimeUnit.SECONDS);

        ServerSocket serverSocket = new ServerSocket(port);

        Runnable cleanupTask = () -> {
            try {
                serverSocket.close();
            } catch (Exception ignored) {
                // Bỏ qua lỗi đóng socket khi đang shutdown
            }

            activeHandlers.forEach(ClientHandler::close);
            clientWorkers.shutdown();
            roomClock.shutdown();

            try {
                if (!clientWorkers.awaitTermination(5, TimeUnit.SECONDS)) {
                    clientWorkers.shutdownNow();
                }
            } catch (InterruptedException e) {
                clientWorkers.shutdownNow();
                Thread.currentThread().interrupt();
            }

            persistence.close();
        };

        Runtime.getRuntime().addShutdownHook(new Thread(cleanupTask, "server-shutdown"));

        System.out.println(
                "Máy chủ Ba Cây TCP đang lắng nghe trên cổng " + port
                        + " | CSDL MySQL: Sẵn sàng | Giao thức: v" + Packet.VERSION
                        + " | Giới hạn: " + maxConnections + " kết nối đồng thời"
        );

        try {
            while (!serverSocket.isClosed()) {
                var handler = new ClientHandler(serverSocket.accept(), userBus, sessionRegistry, roomManager, gameBus);
                activeHandlers.add(handler);

                try {
                    clientWorkers.execute(() -> {
                        try {
                            handler.run();
                        } finally {
                            activeHandlers.remove(handler);
                        }
                    });
                } catch (RejectedExecutionException e) {
                    activeHandlers.remove(handler);
                    handler.close();
                    System.err.println("[NET] Từ chối kết nối mới: Đã đạt giới hạn " + maxConnections + " kết nối đồng thời");
                }
            }
        } catch (SocketException e) {
            if (!serverSocket.isClosed()) {
                throw e;
            }
        } finally {
            cleanupTask.run();
        }
    }
}
