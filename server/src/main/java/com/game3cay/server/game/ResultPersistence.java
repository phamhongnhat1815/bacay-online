package com.game3cay.server.game;

import com.game3cay.server.bus.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Ghi journal trước khi công bố kết quả, retry DB cùng roundId, đọc lại khi restart. */
public final class ResultPersistence implements AutoCloseable {
  private final GameBUS bus;
  private final Path directory;
  private final Map<String, CompletedRound> pending = new LinkedHashMap<>();
  private final ScheduledExecutorService worker =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            var t = new Thread(r, "result-writer");
            t.setDaemon(true);
            return t;
          });

  public ResultPersistence(GameBUS bus, Path directory) throws IOException {
    this.bus = Objects.requireNonNull(bus);
    this.directory = directory;
    Files.createDirectories(directory);
    try (var files = Files.list(directory)) {
      for (var file : files.filter(p -> p.getFileName().toString().endsWith(".round")).toList()) {
        try (var in = new ObjectInputStream(Files.newInputStream(file))) {
          in.setObjectInputFilter(
              ObjectInputFilter.Config.createFilter(
                  "maxdepth=24;maxarray=1024;maxbytes=1000000;com.game3cay.server.game.CompletedRound*;com.game3cay.shared.model.*;java.lang.*;java.math.*;java.util.*;!*"));
          var round = (CompletedRound) in.readObject();
          pending.put(round.result().roundId(), round);
        } catch (ClassNotFoundException | RuntimeException e) {
          throw new IOException("Journal không tương thích: " + file.getFileName(), e);
        }
      }
    }
    worker.scheduleWithFixedDelay(this::retry, 0, 3, TimeUnit.SECONDS);
  }

  public synchronized boolean pendingFor(long uid) {
    return pending.values().stream()
        .anyMatch(r -> r.result().playerResults().stream().anyMatch(p -> p.userId() == uid));
  }

  public synchronized void enqueue(CompletedRound round) throws IOException {
    String id = round.result().roundId();
    if (!id.matches("[0-9a-fA-F-]{36}")) throw new IOException("roundId không hợp lệ");
    var target = directory.resolve(id + ".round");
    var temp = directory.resolve(id + ".tmp");
    if (!pending.containsKey(id)) {
      try (var out = new ObjectOutputStream(Files.newOutputStream(temp))) {
        out.writeObject(round);
      }
      try (var channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
        channel.force(true);
      }
      try {
        Files.move(
            temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
      }
      pending.put(id, round);
    }
    worker.execute(this::retry);
  }

  private void retry() {
    List<CompletedRound> copy;
    synchronized (this) {
      copy = List.copyOf(pending.values());
    }
    for (var round : copy) {
      try {
        bus.saveCompleted(round);
        Files.deleteIfExists(directory.resolve(round.result().roundId() + ".round"));
        synchronized (this) {
          pending.remove(round.result().roundId());
        }
        System.out.println("[DB] SAVED roundId=" + round.result().roundId());
      } catch (Exception e) {
        System.err.println(
            "[DB] PENDING roundId="
                + round.result().roundId()
                + " cause="
                + e.getClass().getSimpleName());
      }
    }
  }

  @Override
  public void close() {
    worker.shutdown();
    try {
      if (!worker.awaitTermination(5, TimeUnit.SECONDS)) worker.shutdownNow();
    } catch (InterruptedException e) {
      worker.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
