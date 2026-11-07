package com.game3cay.admin.network;

import com.game3cay.shared.network.*;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.time.Duration;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class AdminSocketClientTest {
    @Test void heartbeatKeepsRequestCorrelationAndCloseFailsPendingRequestsOnce() throws Exception {
        String previous = System.getProperty("BACAY_TLS_ENABLED");
        System.setProperty("BACAY_TLS_ENABLED", "false");
        ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();
        try (var server = new ServerSocket(0)) {
            CountDownLatch pongReceived = new CountDownLatch(1);
            CountDownLatch requestReceived = new CountDownLatch(1);
            var disconnects = new java.util.concurrent.atomic.AtomicInteger();
            var pushes = new java.util.concurrent.CopyOnWriteArrayList<PacketType>();
            Future<?> serving = worker.submit(() -> {
                try (var accepted = server.accept(); var out = PacketIO.openOutput(accepted); var in = PacketIO.openInput(accepted)) {
                    accepted.setSoTimeout(3000);
                    PacketIO.write(out, Packet.serverPush(PacketType.SERVER_NOTICE, "ready"));
                    PacketIO.write(out, new Packet(PacketType.PING, "heartbeat", null));
                    Packet pong = PacketIO.read(in);
                    assertEquals(PacketType.PONG, pong.getType());
                    assertEquals("heartbeat", pong.getRequestId());
                    pongReceived.countDown();
                    Packet request = PacketIO.read(in);
                    assertEquals(PacketType.ECHO_REQUEST, request.getType());
                    requestReceived.countDown();
                    assertEquals(-1, accepted.getInputStream().read());
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            AdminSocketClient client = new AdminSocketClient("127.0.0.1", server.getLocalPort(),
                    packet -> pushes.add(packet.getType()), disconnects::incrementAndGet);
            try {
                client.connect();
                assertTrue(pongReceived.await(3, TimeUnit.SECONDS));
                var pending = client.send(PacketType.ECHO_REQUEST, "pending");
                assertTrue(requestReceived.await(3, TimeUnit.SECONDS));
                client.close();
                client.close();
                ExecutionException error = assertThrows(ExecutionException.class,
                        () -> pending.get(1, TimeUnit.SECONDS));
                assertInstanceOf(java.io.IOException.class, error.getCause());
                assertFalse(client.isConnected());
                assertEquals(1, disconnects.get());
                assertEquals(java.util.List.of(PacketType.SERVER_NOTICE), pushes);
                serving.get(3, TimeUnit.SECONDS);
            } finally { client.close(); }
        } finally {
            worker.shutdownNow();
            if (previous == null) System.clearProperty("BACAY_TLS_ENABLED"); else System.setProperty("BACAY_TLS_ENABLED", previous);
        }
    }

    @Test void closingDuringWelcomeUnblocksConnectAndClosesUninitializedSocket() throws Exception {
        String previous = System.getProperty("BACAY_TLS_ENABLED");
        System.setProperty("BACAY_TLS_ENABLED", "false");
        ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();
        try (var server = new ServerSocket(0)) {
            AdminSocketClient client = new AdminSocketClient("127.0.0.1", server.getLocalPort(), null, null);
            Future<?> connecting = worker.submit(() -> assertThrows(java.io.IOException.class, client::connect));
            try (var accepted = server.accept()) {
                client.close();
                assertTimeoutPreemptively(Duration.ofSeconds(2), () -> connecting.get(2, TimeUnit.SECONDS));
                accepted.setSoTimeout(1000);
                assertEquals(-1, accepted.getInputStream().read());
                assertFalse(client.isConnected());
            } finally { client.close(); }
        } finally {
            worker.shutdownNow();
            if (previous == null) System.clearProperty("BACAY_TLS_ENABLED"); else System.setProperty("BACAY_TLS_ENABLED", previous);
        }
    }

    @Test void sendOnEdtReturnsImmediatelyAndResponseCompletesAsynchronously() throws Exception {
        String previous = System.getProperty("BACAY_TLS_ENABLED");
        System.setProperty("BACAY_TLS_ENABLED", "false");
        ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();
        try (var server = new ServerSocket(0)) {
            CountDownLatch requestReceived = new CountDownLatch(1);
            CountDownLatch respond = new CountDownLatch(1);
            Future<?> serving = worker.submit(() -> {
                try (var accepted = server.accept(); var out = PacketIO.openOutput(accepted); var in = PacketIO.openInput(accepted)) {
                    PacketIO.write(out, Packet.serverPush(PacketType.SERVER_NOTICE, "ready"));
                    Packet request = PacketIO.read(in);
                    requestReceived.countDown();
                    assertTrue(respond.await(3, TimeUnit.SECONDS));
                    PacketIO.write(out, Packet.ack(request.getRequestId()));
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            AdminSocketClient client = new AdminSocketClient("127.0.0.1", server.getLocalPort(), null, null);
            try {
                client.connect();
                var result = new java.util.concurrent.atomic.AtomicReference<CompletableFuture<Packet>>();
                assertTimeoutPreemptively(Duration.ofSeconds(1), () -> javax.swing.SwingUtilities.invokeAndWait(
                        () -> result.set(client.send(PacketType.ECHO_REQUEST, "hello"))));
                assertTrue(requestReceived.await(2, TimeUnit.SECONDS));
                assertFalse(result.get().isDone());
                respond.countDown();
                assertEquals(PacketType.ACK, result.get().get(3, TimeUnit.SECONDS).getType());
                serving.get(3, TimeUnit.SECONDS);
            } finally { respond.countDown(); client.close(); }
        } finally {
            worker.shutdownNow();
            if (previous == null) System.clearProperty("BACAY_TLS_ENABLED"); else System.setProperty("BACAY_TLS_ENABLED", previous);
        }
    }
}
