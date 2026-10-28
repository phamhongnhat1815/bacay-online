package com.game3cay.admin.network;

import com.game3cay.shared.network.*;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.time.Duration;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class AdminSocketClientTest {
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
