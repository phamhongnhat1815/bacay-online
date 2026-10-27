package com.game3cay.network;

import com.game3cay.shared.network.Packet;
import com.game3cay.shared.network.PacketIO;
import com.game3cay.shared.network.PacketType;
import com.game3cay.shared.network.RuntimeSettings;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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

/**
 * Quản lý kết nối TCP Socket phía Client tới Game Server.
 * <p>
 * Tính năng chính:
 * <ul>
 *   <li>Thao tác kết nối và gửi dữ liệu bất đồng bộ hoàn toàn, không làm đơ giao diện Swing (EDT).</li>
 *   <li>Luồng gửi tuần tự đơn lẻ ({@code sender}) với hàng đợi tối đa 32 gói tin.</li>
 *   <li>Luồng nền nhận dữ liệu ({@code client-reader}) liên tục đọc gói tin từ server.</li>
 *   <li>Tự động phản hồi gói tin {@code PING} bằng {@code PONG}.</li>
 *   <li>Watchdog kiểm tra phản hồi nhịp tim từ Server quá 15 giây.</li>
 * </ul>
 */
public final class SocketClient {

    private static final int CONNECT_TIMEOUT_MS = RuntimeSettings.integer(
            "client.properties", "connection.timeoutMs", 5000, 100, 60000);

    private static final int HEADER_TIMEOUT_MS = 5000;

    private static final int HEARTBEAT_SECONDS = RuntimeSettings.integer(
            "client.properties", "heartbeat.intervalSeconds", 5, 1, 60);

    private static final int SERVER_TIMEOUT_SECONDS = RuntimeSettings.integer(
            "client.properties", "heartbeat.timeoutSeconds", 15, 2, 300);

    private final PacketListener listener;
    private final Socket socket = new Socket();

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
    private ObjectOutputStream output;

    public SocketClient(PacketListener listener) {
        this.listener = Objects.requireNonNull(listener, "listener không được null");
    }

    /**
     * Bắt đầu kết nối bất đồng bộ tới Server.
     *
     * @param host địa chỉ máy chủ
     * @param port cổng TCP máy chủ
     */
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

            // Giới hạn thời gian chờ bắt tay luồng Object
            socket.setSoTimeout(HEADER_TIMEOUT_MS);

            // Hai phía đều tạo output và flush trước khi mở input để tránh deadlock luồng
            output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();

            ObjectInputStream input = PacketIO.openInput(socket);

            // Sau khi bắt tay thành công, chuyển sang không timeout (do heartbeat giám sát)
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

    /**
     * Đưa gói tin vào hàng đợi gửi đi.
     *
     * @param packet gói tin cần gửi
     * @return true nếu đã xếp hàng thành công, false nếu kết nối đã đóng hoặc hàng đợi đầy
     */
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

    private void listenLoop(ObjectInputStream input) {
        try {
            while (isConnected()) {
                Packet packet = PacketIO.read(input);
                lastReceivedNanos = System.nanoTime();

                if (packet.getType() == PacketType.PING) {
                    // Tự động trả lời PONG ngay lập tức
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
            // Bỏ qua lỗi đóng socket
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
