package com.game3cay.network;

import com.game3cay.shared.network.Packet;
import com.game3cay.shared.network.PacketIO;
import com.game3cay.shared.network.PacketType;
import com.game3cay.shared.network.RuntimeSettings;
import com.game3cay.shared.network.TlsTransport;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SocketClient {
    private static final int CONNECT_TIMEOUT_MS = RuntimeSettings.integer(
            "client.properties", "connection.timeoutMs", 5000, 100, 60000);

    private static final int HEADER_TIMEOUT_MS = 5000;

    private static final int HEARTBEAT_SECONDS = RuntimeSettings.integer(
            "client.properties", "heartbeat.intervalSeconds", 5, 1, 60);

    private static final int SERVER_TIMEOUT_SECONDS = RuntimeSettings.integer(
            "client.properties", "heartbeat.timeoutSeconds", 15, 2, 300);

    private final PacketListener listener;
    private volatile Socket socket = new Socket();

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final ExecutorService sender = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(32),
            task -> createDaemonThread(task, "client-sender")
    );

    private final ScheduledExecutorService heartbeat =
            Executors.newSingleThreadScheduledExecutor(task -> createDaemonThread(task, "client-heartbeat"));

    private volatile boolean connected;
    private volatile long lastReceivedNanos;
    private PacketIO.Output output;

    public SocketClient(PacketListener listener) {
        this.listener = Objects.requireNonNull(listener, "listener không được null");
    }

    public void connect(String host, int port) {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("Mỗi đối tượng SocketClient chỉ được sử dụng cho một phiên kết nối duy nhất.");
        }

        try {
            sender.execute(() -> openConnection(host, port));
        } catch (RejectedExecutionException e) {
            close("Client đã đóng.");
        }
    }

    private void openConnection(String host, int port) {
        try {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket = TlsTransport.secureClient(socket, host, HEADER_TIMEOUT_MS);
            socket.setTcpNoDelay(true);

            socket.setSoTimeout(HEADER_TIMEOUT_MS);

            output = PacketIO.openOutput(socket);
            output.flush();

            PacketIO.Input input = PacketIO.openInput(socket);
            input.setIdleTimeoutMillis(SERVER_TIMEOUT_SECONDS * 1000);

            socket.setSoTimeout(0);

            lastReceivedNanos = System.nanoTime();
            connected = true;

            if (closed.get()) {
                connected = false;
                return;
            }

            listener.onConnected(this);

            createDaemonThread(() -> listenLoop(input), "client-reader").start();

            heartbeat.scheduleAtFixedRate(
                    this::checkHeartbeatWatchdog,
                    HEARTBEAT_SECONDS,
                    HEARTBEAT_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (IOException | RuntimeException e) {
            close("Kết nối thất bại: " + e.getMessage());
        }
    }

    public boolean send(Packet packet) {
        if (!isConnected()) {
            return false;
        }

        try {
            sender.execute(() -> {
                if (!isConnected()) {
                    return;
                }
                try {
                    PacketIO.write(output, packet);
                } catch (IOException | RuntimeException e) {
                    close("Lỗi khi gửi dữ liệu: " + e.getMessage());
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            return false;
        }
    }

    private void listenLoop(PacketIO.Input input) {
        try {
            while (isConnected()) {
                Packet packet = PacketIO.read(input);
                lastReceivedNanos = System.nanoTime();

                if (packet.getType() == PacketType.PING) {
                    if (!send(new Packet(PacketType.PONG, packet.getRequestId(), packet.getData()))) {
                        close("Không thể đưa phản hồi PONG vào hàng đợi.");
                        return;
                    }
                    listener.onPacketReceived(this, packet);
                } else {
                    listener.onPacketReceived(this, packet);
                }
            }
        } catch (EOFException | SocketException e) {
            close("Mất kết nối hoặc máy chủ đã đóng socket.");
        } catch (IOException | ClassNotFoundException | RuntimeException e) {
            close("Lỗi nhận dữ liệu từ máy chủ: " + e.getMessage());
        }
    }

    private void checkHeartbeatWatchdog() {
        if (!isConnected()) {
            return;
        }

        long silentNanos = System.nanoTime() - lastReceivedNanos;
        if (silentNanos >= TimeUnit.SECONDS.toNanos(SERVER_TIMEOUT_SECONDS)) {
            close("Máy chủ không phản hồi nhịp tim trong thời gian cho phép.");
        }
    }

    public boolean isConnected() {
        return connected && !closed.get();
    }

    public void disconnect() {
        close("Người dùng đã chủ động ngắt kết nối.");
    }

    private void close(String reason) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        connected = false;

        try {
            socket.close();
        } catch (IOException ignored) {
        } finally {
            heartbeat.shutdownNow();
            sender.shutdownNow();
            listener.onDisconnected(this, reason);
        }
    }

    private static Thread createDaemonThread(Runnable task, String threadName) {
        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        return thread;
    }
}
