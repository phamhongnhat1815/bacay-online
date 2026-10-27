package com.game3cay.network;

import com.game3cay.shared.network.Packet;

/**
 * Giao diện nhận thông báo sự kiện từ {@link SocketClient}.
 */
public interface PacketListener {

    /**
     * Gọi khi kết nối Socket tới Server thành công.
     *
     * @param client đối tượng SocketClient vừa kết nối
     */
    void onConnected(SocketClient client);

    /**
     * Gọi khi nhận được một gói tin {@link Packet} hợp lệ từ Server.
     *
     * @param client đối tượng SocketClient nhận gói tin
     * @param packet gói tin vừa nhận
     */
    void onPacketReceived(SocketClient client, Packet packet);

    /**
     * Gọi khi kết nối tới Server bị gián đoạn hoặc ngắt kết nối.
     *
     * @param client đối tượng SocketClient bị ngắt
     * @param reason lý do ngắt kết nối
     */
    void onDisconnected(SocketClient client, String reason);
}
