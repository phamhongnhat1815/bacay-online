package com.game3cay.network;

import com.game3cay.shared.network.*;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SocketClient {
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int HEADER_TIMEOUT_MS = 5000;

    private static final int HEARTBEAT_SECONDS = 5;
    private static final int SERVER_TIMEOUT_SECONDS = 15;

    private final PacketListener listener;
    private final Socket socket = new Socket();

    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    // Một đường gửi duy nhất, hàng đợi tối đa 32 tác vụ.
    private final ExecutorService sender = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(32),
            task -> daemon(task, "client-sender")
    );

    private final ScheduledExecutorService heartbeat =
            Executors.newSingleThreadScheduledExecutor(
                    task -> daemon(task, "client-heartbeat")
            );

    private volatile boolean connected;
    private volatile long lastReceivedNanos;

    private ObjectOutputStream output;

    public SocketClient(PacketListener listener) {
        this.listener = listener;
    }

    // Bất đồng bộ: gọi từ Swing không làm treo UI.
    public void connect(String host, int port) {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException(
                    "Mỗi SocketClient chỉ dùng cho một kết nối."
            );
        }

        try {
            sender.execute(() -> openConnection(host, port));
        } catch (RejectedExecutionException e) {
            close("Client đã đóng.");
        }
    }

    private void openConnection(String host, int port) {
        try {
            socket.connect(
                    new InetSocketAddress(host, port),
                    CONNECT_TIMEOUT_MS
            );

            // Giới hạn thời gian chờ header Object Stream.
            socket.setSoTimeout(HEADER_TIMEOUT_MS);

            // Hai phía đều tạo output và flush trước khi tạo input.
            output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();

            ObjectInputStream input = PacketIO.openInput(socket);

            // Sau bước khởi tạo, heartbeat quản lý kết nối im lặng.
            socket.setSoTimeout(0);

            lastReceivedNanos = System.nanoTime();
            connected = true;

            if (closed.get()) {
                connected = false;
                return;
            }

            listener.onConnected(this);

            daemon(() -> listen(input), "client-reader").start();

            heartbeat.scheduleAtFixedRate(
                    this::checkHeartbeat,
                    HEARTBEAT_SECONDS,
                    HEARTBEAT_SECONDS,
                    TimeUnit.SECONDS
            );

        } catch (IOException | RuntimeException e) {
            close("Kết nối thất bại: " + e.getMessage());
        }
    }

    // true = đã xếp vào hàng đợi, KHÔNG có nghĩa server đã nhận.
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
                    close("Lỗi gửi dữ liệu: " + e.getMessage());
                }
            });

            return true;

        } catch (RejectedExecutionException e) {
            return false;
        }
    }

    private void listen(ObjectInputStream input) {
        try {
            while (isConnected()) {
                Packet packet = PacketIO.read(input);
                lastReceivedNanos = System.nanoTime();

                if (packet.getType() == PacketType.PING) {
                    // Hỗ trợ trường hợp server chủ động gửi heartbeat.
                    send(new Packet(
                            PacketType.PONG,
                            packet.getRequestId(),
                            packet.getData()
                    ));
                } else {
                    listener.onPacketReceived(this, packet);
                }
            }

        } catch (EOFException | SocketException e) {
            close("Mất kết nối hoặc server đã đóng socket.");

        } catch (IOException | ClassNotFoundException | RuntimeException e) {
            close("Lỗi nhận dữ liệu: " + e.getMessage());
        }
    }

    private void checkHeartbeat() {
        if (!isConnected()) {
            return;
        }

        long silentNanos = System.nanoTime() - lastReceivedNanos;

        if (silentNanos >= TimeUnit.SECONDS.toNanos(SERVER_TIMEOUT_SECONDS)) {
            close("Server không phản hồi trong thời gian cho phép.");
            return;
        }

        send(Packet.request(PacketType.PING, "heartbeat"));
    }

    public boolean isConnected() {
        return connected && !closed.get();
    }

    public void disconnect() {
        close("Đã ngắt kết nối.");
    }

    private void close(String reason) {
        // Dù nhiều thread cùng phát hiện lỗi, chỉ dọn và thông báo một lần.
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        connected = false;

        try {
            // Đóng socket cũng đóng hai stream,
            // đồng thời đánh thức read/write đang chờ.
            socket.close();

        } catch (IOException ignored) {
        } finally {
            heartbeat.shutdownNow();
            sender.shutdownNow();
            listener.onDisconnected(this, reason);
        }
    }

    private static Thread daemon(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }
}