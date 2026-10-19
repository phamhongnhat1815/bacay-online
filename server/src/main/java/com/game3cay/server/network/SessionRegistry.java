package com.game3cay.server.network;

import com.game3cay.shared.network.Packet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionRegistry {
  private final ConcurrentHashMap<Long, ClientHandler> online = new ConcurrentHashMap<>();

  public boolean bind(long userId, ClientHandler handler) {
    return online.putIfAbsent(userId, handler) == null;
  }

  public void release(long userId, ClientHandler handler) {
    online.remove(userId, handler);
  }

  public void broadcast(List<Long> recipients, Packet packet) {
    for (long id : recipients) {
      ClientHandler handler = online.get(id);

      if (handler != null) {
        handler.send(packet);
      }
    }
  }

  public void broadcastAll(Packet packet) {
    online.values().forEach(handler -> handler.send(packet));
  }
}
