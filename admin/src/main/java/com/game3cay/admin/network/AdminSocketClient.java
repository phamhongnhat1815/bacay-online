package com.game3cay.admin.network;

import com.game3cay.shared.network.Packet;
import com.game3cay.shared.network.PacketIO;
import com.game3cay.shared.network.PacketType;
import com.game3cay.shared.network.TlsTransport;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class AdminSocketClient {
    private final String host;
    private final int port;
    private final Consumer<Packet> pushListener;
    private final Runnable onDisconnect;
    private volatile Socket socket;
    private PacketIO.Input in;
    private PacketIO.Output out;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean connected = new AtomicBoolean();
    private final Map<String, CompletableFuture<Packet>> pendingRequests = new ConcurrentHashMap<>();
    private final ExecutorService sender = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(32), task -> daemon(task, "admin-socket-sender"));

    public AdminSocketClient(String host, int port, Consumer<Packet> pushListener, Runnable onDisconnect) {
        this.host = host;
        this.port = port;
        this.pushListener = pushListener;
        this.onDisconnect = onDisconnect;
    }

    public void connect() throws IOException {
        if (!started.compareAndSet(false, true)) throw new IOException("Phiên kết nối đã được sử dụng");
        try {
            socket = new Socket();
            if (closed.get()) throw new IOException("Phiên kết nối đã đóng");
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket = TlsTransport.secureClient(socket, host, 5000);
            socket.setTcpNoDelay(true);
            out = PacketIO.openOutput(socket);
            in = PacketIO.openInput(socket);
            in.setIdleTimeoutMillis(5000);
            Packet welcome = PacketIO.read(in);
            if (welcome.getType() != PacketType.SERVER_NOTICE) throw new IOException("Máy chủ không gửi lời chào hợp lệ");
            in.setIdleTimeoutMillis(300_000); // Supports server heartbeat intervals up to 60 seconds.
            if (closed.get()) throw new IOException("Phiên kết nối đã đóng");
            connected.set(true);
            if (pushListener != null) pushListener.accept(welcome);
            daemon(this::readLoop, "admin-socket-reader").start();
        } catch (ClassNotFoundException | RuntimeException e) {
            close();
            throw new IOException("Không thể khởi tạo kết nối quản trị", e);
        } catch (IOException e) {
            close();
            throw e;
        }
    }

    public CompletableFuture<Packet> send(PacketType type, Object data) {
        String requestId = UUID.randomUUID().toString();
        Packet packet = new Packet(type, requestId, data);
        CompletableFuture<Packet> future = new CompletableFuture<>();
        try { PacketIO.validate(packet); }
        catch (IOException e) { return CompletableFuture.failedFuture(e); }
        if (!isConnected()) return CompletableFuture.failedFuture(new IOException("Chưa kết nối tới máy chủ"));
        pendingRequests.put(requestId, future);
        // Installed before queueing/writing: a blocked writer is interrupted by closing its socket.
        future.orTimeout(10, TimeUnit.SECONDS).whenComplete((response, error) -> {
            pendingRequests.remove(requestId, future);
            if (error instanceof TimeoutException) close();
        });
        try {
            sender.execute(() -> {
                if (future.isDone() || !isConnected()) return;
                try { PacketIO.write(out, packet); }
                catch (IOException | RuntimeException e) { future.completeExceptionally(e); close(); }
            });
        } catch (RejectedExecutionException e) { future.completeExceptionally(new IOException("Hàng đợi gửi đã đầy", e)); }
        return future;
    }

    private void readLoop() {
        try {
            while (isConnected()) {
                Packet packet = PacketIO.read(in);
                if (packet.getType() == PacketType.PING) {
                    try {
                        sender.execute(() -> {
                            try { PacketIO.write(out, new Packet(PacketType.PONG, packet.getRequestId(), null)); }
                            catch (IOException e) { close(); }
                        });
                    } catch (RejectedExecutionException e) { close(); }
                    continue;
                }
                CompletableFuture<Packet> future = packet.getRequestId() == null ? null : pendingRequests.remove(packet.getRequestId());
                if (future != null) future.complete(packet);
                else if (pushListener != null) pushListener.accept(packet);
            }
        } catch (IOException | ClassNotFoundException | RuntimeException e) {
            if (!closed.get()) java.util.logging.Logger.getLogger(getClass().getName()).fine("Admin connection ended: " + e.getClass().getSimpleName());
        } finally { close(); }
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        boolean wasConnected = connected.getAndSet(false);
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        sender.shutdownNow();
        pendingRequests.values().forEach(future -> future.completeExceptionally(new IOException("Kết nối máy chủ bị ngắt")));
        pendingRequests.clear();
        if (wasConnected && onDisconnect != null) onDisconnect.run();
    }

    public boolean isConnected() { return connected.get() && !closed.get(); }
    private static Thread daemon(Runnable task, String name) {
        Thread thread = new Thread(task, name); thread.setDaemon(true); return thread;
    }
}
