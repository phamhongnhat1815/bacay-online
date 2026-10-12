package com.bacay.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Entry point của server.
 *
 * <p>Khởi động:
 * <ol>
 *   <li>Đọc cấu hình từ {@code server.properties} trên classpath.</li>
 *   <li>Mở {@link ServerSocket} trên cổng cấu hình.</li>
 *   <li>Vòng lặp nhận kết nối — tạo {@code ClientHandler} cho mỗi client.</li>
 *   <li>Đăng ký shutdown hook để đóng tài nguyên có kiểm soát.</li>
 * </ol>
 *
 * <p>Quy tắc quan trọng (N01):
 * <ul>
 *   <li>Một client lỗi KHÔNG làm vòng {@code accept()} dừng.</li>
 *   <li>Vượt giới hạn kết nối → từ chối rõ ràng và đóng socket ngay.</li>
 *   <li>Không tạo {@link Thread} vô hạn — dùng {@link ExecutorService} có giới hạn.</li>
 * </ul>
 *
 * <p>Chạy từ CLI:
 * <pre>{@code
 *   cd server
 *   mvn package -DskipTests
 *   java -jar target/bacay-server.jar
 *   # Hoặc override cổng:
 *   java -Dserver.port=9999 -jar target/bacay-server.jar
 * }</pre>
 */
public class RunServer {

    private static final Logger log = LoggerFactory.getLogger(RunServer.class);

    // --- Cấu hình (đọc từ server.properties, override được bằng System property) ---
    private final int port;
    private final int maxConnections;

    // --- Quản lý vòng đời ---
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServerSocket serverSocket;
    private ExecutorService threadPool;

    // --- Các manager (sẽ inject sau khi module hoàn thiện) ---
    // private final ConnectionManager connectionManager;
    // private final RoomManager roomManager;
    // private final MysqlConnector dbConnector;

    public RunServer(Properties config) {
        this.port           = intProp(config, "server.port",           8888);
        this.maxConnections = intProp(config, "server.maxConnections", 100);
    }

    /** Bắt đầu lắng nghe và phục vụ client. Blocking cho đến khi server dừng. */
    public void start() throws IOException {
        threadPool   = Executors.newFixedThreadPool(maxConnections);
        serverSocket = new ServerSocket(port);
        running.set(true);

        log.info("=== BaCay Server started on port {} (maxConnections={}) ===", port, maxConnections);

        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                handleNewConnection(clientSocket);
            } catch (IOException e) {
                if (running.get()) {
                    // Lỗi thật sự (không phải do shutdown)
                    log.error("Lỗi khi accept kết nối mới", e);
                    // Vòng lặp tiếp tục — một lỗi accept không làm server dừng
                }
            }
        }

        log.info("Server accept loop ended.");
    }

    private void handleNewConnection(Socket socket) {
        // TODO (N01, N03): Kiểm tra giới hạn kết nối
        // Nếu vượt giới hạn → gửi ERROR packet rồi đóng socket ngay
        // Tạm thời: log và submit vào thread pool
        log.info("New connection from {}", socket.getRemoteSocketAddress());
        threadPool.submit(() -> {
            // TODO: new ClientHandler(socket, connectionManager, roomManager).run();
            // Tạm thời chỉ log và đóng
            try {
                log.debug("ClientHandler placeholder for {}", socket.getRemoteSocketAddress());
                socket.close();
            } catch (IOException e) {
                log.warn("Không đóng được socket", e);
            }
        });
    }

    /** Dừng server — đóng serverSocket và thread pool có kiểm soát. */
    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        log.info("Stopping server...");
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close(); // Làm serverSocket.accept() ném IOException → thoát vòng lặp
            }
        } catch (IOException e) {
            log.warn("Lỗi khi đóng ServerSocket", e);
        }
        if (threadPool != null) {
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(10, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("Server stopped.");
    }

    // -------------------------------------------------------------------------
    // Main
    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        Properties config = loadConfig();
        RunServer server = new RunServer(config);

        // Shutdown hook — Ctrl+C hoặc kill -SIGTERM
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown hook triggered.");
            server.stop();
        }, "shutdown-hook"));

        try {
            server.start();
        } catch (IOException e) {
            log.error("Server không khởi động được", e);
            System.exit(1);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Properties loadConfig() {
        Properties props = new Properties();
        try (InputStream in = RunServer.class.getResourceAsStream("/server.properties")) {
            if (in != null) {
                props.load(in);
                log.info("Đọc cấu hình từ server.properties");
            } else {
                log.warn("Không tìm thấy server.properties — dùng giá trị mặc định");
            }
        } catch (IOException e) {
            log.warn("Lỗi đọc server.properties", e);
        }
        // System properties override file (ví dụ -Dserver.port=9999)
        props.putAll(System.getProperties());
        return props;
    }

    private static int intProp(Properties p, String key, int defaultVal) {
        String val = p.getProperty(key);
        if (val == null) return defaultVal;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            log.warn("Giá trị không hợp lệ cho {}: '{}' — dùng mặc định {}", key, val, defaultVal);
            return defaultVal;
        }
    }
}
