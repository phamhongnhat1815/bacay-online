package com.game3cay.server;

import com.game3cay.server.bus.*;
import com.game3cay.server.dal.DatabaseConfig;
import com.game3cay.server.game.ResultPersistence;
import com.game3cay.server.network.*;
import com.game3cay.server.room.*;
import com.game3cay.shared.model.*;
import com.game3cay.shared.network.*;
import java.net.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

public final class ServerApplication {
  public static void main(String[] args) throws Exception {
    int port =
        args.length == 0
            ? RuntimeSettings.integer(
                "server.properties", "server.port", 8888, 1, 65535)
            : Integer.parseInt(args[0]);

    int limit =
        RuntimeSettings.integer(
            "server.properties", "server.maxConnections", 256, 1, 256);

    if (port < 1 || port > 65535) {
      throw new IllegalArgumentException("Port 1–65535");
    }

    var connections = DatabaseConfig.fromEnvironment();
    var users = new JdbcUserBUS(connections);
    var games = new JdbcGameBUS(connections);
    games.checkSchema();

    var admins = new AdminService(connections);
    admins.checkSchema();

    var persistence =
        new ResultPersistence(
            games,
            Path.of(System.getenv().getOrDefault(
                "BACAY_DATA_DIR", "data/pending-results")));

    var sessions = new SessionRegistry();

    var rooms =
        new RoomManager(
            new RoomEvents() {
              public void roomUpdated(RoomSnapshot r, List<Long> ids) {
                sessions.broadcast(
                    ids, Packet.serverPush(PacketType.ROOM_UPDATED, r));
              }

              public void chatMessage(
                  long roomId, ChatMessage m, List<Long> ids) {
                sessions.broadcast(
                    ids,
                    Packet.serverPush(
                        "SYSTEM".equals(m.type())
                            ? PacketType.CHAT_SYSTEM
                            : PacketType.CHAT_MESSAGE,
                        new RoomChatEvent(roomId, m)));
              }

              public void packet(Packet p, List<Long> ids) {
                sessions.broadcast(ids, p);
              }

              public void lobbyUpdated(List<RoomSummary> tables) {
                sessions.broadcastAll(
                    Packet.serverPush(PacketType.ROOM_LIST_RESULT, tables));
              }
            },
            users,
            persistence);

    var workers =
        new ThreadPoolExecutor(
            0, limit, 60, TimeUnit.SECONDS, new SynchronousQueue<>());

    Set<ClientHandler> handlers = ConcurrentHashMap.newKeySet();
    var clock = Executors.newSingleThreadScheduledExecutor();
    clock.scheduleWithFixedDelay(rooms::tick, 1, 1, TimeUnit.SECONDS);

    var server = new ServerSocket(port);

    Runnable close =
        () -> {
          try {
            server.close();
          } catch (Exception ignored) {
          }

          handlers.forEach(ClientHandler::close);
          workers.shutdown();
          clock.shutdown();

          try {
            workers.awaitTermination(5, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }

          persistence.close();
          workers.shutdownNow();
        };

    Runtime.getRuntime().addShutdownHook(
        new Thread(close, "server-shutdown"));

    System.out.println(
        "TCP server đang nghe cổng " + port
            + " | MySQL OK | protocol v" + Packet.VERSION
            + " | tối đa " + limit + " kết nối");

    try {
      while (!server.isClosed()) {
        var handler =
            new ClientHandler(
                server.accept(), users, sessions, rooms, games, admins);

        handlers.add(handler);

        try {
          workers.execute(() -> {
            try {
              handler.run();
            } finally {
              handlers.remove(handler);
            }
          });
        } catch (RejectedExecutionException e) {
          handlers.remove(handler);
          handler.close();
          System.err.println(
              "[NET] Từ chối: đủ " + limit + " kết nối");
        }
      }
    } catch (SocketException e) {
      if (!server.isClosed()) {
        throw e;
      }
    } finally {
      close.run();
    }
  }
}