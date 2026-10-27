package com.game3cay.server.game;

import com.game3cay.server.bus.GameBUS;

import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Quản lý độ bền vững dữ liệu kết quả ván bài (Write-Ahead Journal / Pending Queue).
 * <p>
 * Trước khi công bố kết quả cho client, kết quả được lưu vào đĩa cục bộ (.round file).
 * Một luồng nền định kỳ thử ghi vào CSDL MySQL. Khi server khởi động lại, các ván chưa lưu
 * xong sẽ được tự động nạp lại và tiếp tục ghi vào CSDL, đảm bảo không bao giờ mất điểm của người chơi.
 */
public final class ResultPersistence implements AutoCloseable {

    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F-]{36}$");
    private static final String JOURNAL_FILTER =
            "maxdepth=24;maxarray=1024;maxbytes=1000000;"
                    + "com.game3cay.server.game.CompletedRound*;"
                    + "com.game3cay.shared.model.*;"
                    + "java.lang.*;java.math.*;java.util.*;!*";

    private final GameBUS bus;
    private final Path directory;
    private final Map<String, CompletedRound> pending = new LinkedHashMap<>();
    private final ScheduledExecutorService worker =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "result-writer");
                thread.setDaemon(true);
                return thread;
            });

    public ResultPersistence(GameBUS bus, Path directory) throws IOException {
        this.bus = Objects.requireNonNull(bus, "bus không được null");
        this.directory = Objects.requireNonNull(directory, "directory không được null");

        Files.createDirectories(directory);

        // Nạp các ván đang chờ từ đĩa khi khởi động
        try (var files = Files.list(directory)) {
            List<Path> roundFiles = files.filter(p -> p.getFileName().toString().endsWith(".round")).toList();
            for (Path file : roundFiles) {
                try (var in = new ObjectInputStream(Files.newInputStream(file))) {
                    in.setObjectInputFilter(ObjectInputFilter.Config.createFilter(JOURNAL_FILTER));
                    CompletedRound round = (CompletedRound) in.readObject();
                    pending.put(round.result().roundId(), round);
                } catch (ClassNotFoundException | RuntimeException e) {
                    throw new IOException("Tệp journal không tương thích hoặc bị lỗi: " + file.getFileName(), e);
                }
            }
        }

        // Định kỳ 3 giây thử lưu lại các ván đang chờ
        worker.scheduleWithFixedDelay(this::retryPendingResults, 0, 3, TimeUnit.SECONDS);
    }

    /**
     * Kiểm tra xem người chơi có ván nào đang chờ lưu CSDL không.
     *
     * @param userId mã người chơi
     * @return true nếu người chơi đang có kết quả chờ ghi
     */
    public synchronized boolean pendingFor(long userId) {
        return pending.values().stream()
                .anyMatch(r -> r.result().playerResults().stream().anyMatch(p -> p.userId() == userId));
    }

    /**
     * Ghi một ván bài vào hàng đợi và tệp tin trên đĩa.
     *
     * @param round ván bài hoàn thành
     * @throws IOException nếu lỗi ghi tệp trên đĩa
     */
    public synchronized void enqueue(CompletedRound round) throws IOException {
        String roundId = round.result().roundId();
        if (!UUID_PATTERN.matcher(roundId).matches()) {
            throw new IOException("roundId không đúng định dạng UUID: " + roundId);
        }

        Path targetFile = directory.resolve(roundId + ".round");
        Path tempFile = directory.resolve(roundId + ".tmp");

        if (!pending.containsKey(roundId)) {
            try (var out = new ObjectOutputStream(Files.newOutputStream(tempFile))) {
                out.writeObject(round);
            }
            try (var channel = FileChannel.open(tempFile, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(tempFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
            pending.put(roundId, round);
        }

        worker.execute(this::retryPendingResults);
    }

    private void retryPendingResults() {
        List<CompletedRound> snapshot;
        synchronized (this) {
            snapshot = List.copyOf(pending.values());
        }

        for (CompletedRound round : snapshot) {
            String roundId = round.result().roundId();
            try {
                bus.saveCompleted(round);
                Files.deleteIfExists(directory.resolve(roundId + ".round"));
                synchronized (this) {
                    pending.remove(roundId);
                }
                System.out.println("[DB] SAVED roundId=" + roundId);
            } catch (Exception e) {
                System.err.println("[DB] PENDING roundId=" + roundId + " cause=" + e.getClass().getSimpleName());
            }
        }
    }

    @Override
    public void close() {
        worker.shutdown();
        try {
            if (!worker.awaitTermination(5, TimeUnit.SECONDS)) {
                worker.shutdownNow();
            }
        } catch (InterruptedException e) {
            worker.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
