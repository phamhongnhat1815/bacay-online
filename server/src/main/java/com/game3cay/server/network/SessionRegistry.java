package com.game3cay.server.network;

import com.game3cay.shared.network.Packet;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionRegistry {
    private final ConcurrentHashMap<Long, ClientHandler> onlineSessions = new ConcurrentHashMap<>();

    public synchronized boolean bind(long userId, ClientHandler handler) {
        ClientHandler old = onlineSessions.get(userId);
        if (old != null && old != handler && !old.isClosed()) {
            return false;
        }
        onlineSessions.put(userId, handler);
        return true;
    }

    public void release(long userId, ClientHandler handler) {
        onlineSessions.remove(userId, handler);
    }

    public void broadcast(List<Long> recipientUserIds, Packet packet) {
        if (recipientUserIds == null || recipientUserIds.isEmpty()) {
            return;
        }
        for (long userId : recipientUserIds) {
            ClientHandler handler = onlineSessions.get(userId);
            if (handler != null) {
                handler.send(packet);
            }
        }
    }

    public void broadcastAll(Packet packet) {
        onlineSessions.values().forEach(handler -> handler.send(packet));
    }

    public int onlineCount() {
        return onlineSessions.size();
    }

    public java.util.Optional<ClientHandler> find(long userId) {
        return java.util.Optional.ofNullable(onlineSessions.get(userId));
    }
}
