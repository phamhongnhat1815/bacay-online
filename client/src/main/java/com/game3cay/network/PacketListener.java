package com.game3cay.network;

import com.game3cay.shared.network.Packet;

public interface PacketListener {
    void onConnected(SocketClient client);

    void onPacketReceived(SocketClient client, Packet packet);

    void onDisconnected(SocketClient client, String reason);
}