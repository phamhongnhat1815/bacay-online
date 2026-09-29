package com.game3cay.controller;

import com.game3cay.gui.MainFrame;
import com.game3cay.network.*;
import com.game3cay.shared.network.*;

import javax.swing.SwingUtilities;

public final class ConnectionController implements PacketListener {
    private final MainFrame view;

    private SocketClient currentClient;
    private boolean closing;
    private int testNumber;

    public ConnectionController(MainFrame view) {
        this.view = view;

        view.onConnect(this::connect);

        view.onDisconnect(() -> {
            if (currentClient != null) {
                currentClient.disconnect();
            }
        });

        view.onSend(this::sendTest);

        view.onClose(() -> {
            closing = true;

            if (currentClient != null) {
                currentClient.disconnect();
            }

            view.dispose();
        });
    }

    private void connect() {
        if (currentClient != null) {
            return;
        }

        String host = view.getHost();
        int port;

        try {
            port = Integer.parseInt(view.getPortText());

            if (host.isBlank() || port < 1 || port > 65535) {
                throw new NumberFormatException();
            }

        } catch (NumberFormatException e) {
            view.appendLog(
                    "IP không được trống; port phải từ 1 đến 65535."
            );
            return;
        }

        view.setConnectionState(false, true, "Đang kết nối...");
        view.appendLog("Kết nối tới " + host + ":" + port);

        currentClient = new SocketClient(this);
        currentClient.connect(host, port);
    }

    private void sendTest() {
        if (currentClient == null) {
            return;
        }

        Packet packet = Packet.request(
                PacketType.PING,
                "T01 #" + ++testNumber
        );

        if (currentClient.send(packet)) {
            view.appendLog(
                    "[Chờ gửi] PING " + packet.getRequestId()
            );
        } else {
            view.appendLog(
                    "Không xếp được yêu cầu gửi: đã ngắt hoặc hàng đợi đầy."
            );
        }
    }

    @Override
    public void onConnected(SocketClient client) {
        onUi(client, () -> {
            if (!client.isConnected()) {
                return;
            }

            view.setConnectionState(true, false, "Đã kết nối");
            view.appendLog("Kết nối thành công.");
        });
    }

    @Override
    public void onPacketReceived(SocketClient client, Packet packet) {
        onUi(client, () -> {
            // Không đưa heartbeat tự động vào log UI.
            if (packet.getType() == PacketType.PONG
                    && "heartbeat".equals(packet.getData())) {
                return;
            }

            view.appendLog(
                    "[Nhận] " + packet.getType()
                    + " | id=" + packet.getRequestId()
                    + " | " + packet.getData()
            );
        });
    }

    @Override
    public void onDisconnected(SocketClient client, String reason) {
        onUi(client, () -> {
            currentClient = null;

            view.setConnectionState(false, false, "Chưa kết nối");
            view.appendLog(reason);
        });
    }

    private void onUi(SocketClient client, Runnable action) {
        SwingUtilities.invokeLater(() -> {
            // Kết nối cũ không được cập nhật giao diện của kết nối mới.
            if (!closing && currentClient == client) {
                action.run();
            }
        });
    }
}