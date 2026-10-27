package com.game3cay.admin.network;

import com.game3cay.shared.network.ErrorCode;
import com.game3cay.shared.network.Packet;
import com.game3cay.shared.network.PacketIO;
import com.game3cay.shared.network.PacketType;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Socket Client chuyên dụng cho ứng dụng quản trị Admin, kết nối qua kênh TCP Socket chuẩn Packet v2.
 */
public final class AdminSocketClient {

    private final String host;
    private final int port;
    private final Consumer<Packet> pushListener;
    private final Runnable onDisconnect;

    private Socket socket;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private Thread readerThread;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final Map<String, CompletableFuture<Packet>> pendingRequests = new ConcurrentHashMap<>();

    public AdminSocketClient(String host, int port, Consumer<Packet> pushListener, Runnable onDisconnect) {
        this.host = host;
        this.port = port;
        this.pushListener = pushListener;
        this.onDisconnect = onDisconnect;
    }

    public synchronized void connect() throws IOException {
        if (connected.get()) {
            return;
        }

        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        socket.setTcpNoDelay(true);

        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = PacketIO.openInput(socket);

        connected.set(true);

        readerThread = new Thread(this::readLoop, "admin-socket-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public CompletableFuture<Packet> send(PacketType type, Object data) {
        String requestId = UUID.randomUUID().toString();
        Packet packet = new Packet(type, requestId, data);
        CompletableFuture<Packet> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        if (!connected.get() || out == null) {
            future.completeExceptionally(new IOException("Chưa kết nối tới máy chủ"));
            pendingRequests.remove(requestId);
            return future;
        }

        try {
            PacketIO.write(out, packet);
        } catch (IOException e) {
            pendingRequests.remove(requestId);
            future.completeExceptionally(e);
            close();
        }

        // Tự động timeout sau 10 giây nếu server không trả lời
        return future.orTimeout(10, TimeUnit.SECONDS).whenComplete((res, err) -> {
            pendingRequests.remove(requestId);
        });
    }

    private void readLoop() {
        try {
            while (connected.get()) {
                Packet packet = PacketIO.read(in);
                if (packet == null) {
                    break;
                }

                if (packet.getType() == PacketType.PING) {
                    try {
                        PacketIO.write(out, new Packet(PacketType.PONG, packet.getRequestId(), null));
                    } catch (IOException ignored) {}
                    continue;
                }

                String reqId = packet.getRequestId();
                if (reqId != null && pendingRequests.containsKey(reqId)) {
                    CompletableFuture<Packet> future = pendingRequests.remove(reqId);
                    if (future != null) {
                        future.complete(packet);
                    }
                } else if (pushListener != null) {
                    pushListener.accept(packet);
                }
            }
        } catch (Exception e) {
            // Socket ngắt kết nối
        } finally {
            close();
        }
    }

    public synchronized void close() {
        if (!connected.compareAndSet(true, false)) {
            return;
        }

        for (var future : pendingRequests.values()) {
            future.completeExceptionally(new IOException("Kết nối máy chủ bị ngắt"));
        }
        pendingRequests.clear();

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}

        if (onDisconnect != null) {
            onDisconnect.run();
        }
    }

    public boolean isConnected() {
        return connected.get();
    }
}
