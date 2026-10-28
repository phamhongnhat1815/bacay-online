package com.game3cay.server.network;

import com.game3cay.shared.network.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class TlsTransportTest {
    @TempDir Path temporary;

    @Test void generatedIdentityEncryptsFramesAndRejectsWrongHostOrUntrustedCertificate() throws Exception {
        Map<String, String> saved = new HashMap<>();
        for (String key : new String[]{"BACAY_TLS_ENABLED", "BACAY_TLS_DIR", "BACAY_TLS_KEYSTORE", "BACAY_TLS_STORE_PASSWORD", "BACAY_TLS_CERT"}) {
            saved.put(key, System.getProperty(key));
        }
        System.setProperty("BACAY_TLS_ENABLED", "true");
        System.setProperty("BACAY_TLS_DIR", temporary.resolve("first").toString());
        System.setProperty("BACAY_TLS_KEYSTORE", temporary.resolve("first/server.p12").toString());
        System.setProperty("BACAY_TLS_STORE_PASSWORD", "test-password-long-enough");
        ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();
        try (var server = ServerTls.listen(0)) {
            System.setProperty("BACAY_TLS_CERT", temporary.resolve("first/server.crt").toString());
            Future<?> transfer = worker.submit(() -> {
                try (var socket = server.accept(); var output = PacketIO.openOutput(socket)) {
                    PacketIO.write(output, Packet.serverPush(PacketType.SERVER_NOTICE, "TLS works"));
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            try (var plain = new Socket("127.0.0.1", server.getLocalPort());
                 var secured = TlsTransport.secureClient(plain, "localhost", 5000);
                 var input = PacketIO.openInput(secured)) {
                assertInstanceOf(javax.net.ssl.SSLSocket.class, secured);
                assertEquals("TLS works", PacketIO.read(input).getData());
            }
            transfer.get(10, TimeUnit.SECONDS);
            Future<?> badHost = worker.submit(() -> acceptFailedHandshake(server));
            try (var plain = new Socket("127.0.0.1", server.getLocalPort())) {
                assertThrows(java.io.IOException.class, () -> TlsTransport.secureClient(plain, "wrong.example", 5000));
            }
            badHost.get(10, TimeUnit.SECONDS);
            System.setProperty("BACAY_TLS_DIR", temporary.resolve("second").toString());
            System.setProperty("BACAY_TLS_KEYSTORE", temporary.resolve("second/server.p12").toString());
            try (var other = ServerTls.listen(0)) {
                System.setProperty("BACAY_TLS_CERT", temporary.resolve("second/server.crt").toString());
                Future<?> untrusted = worker.submit(() -> acceptFailedHandshake(server));
                try (var plain = new Socket("127.0.0.1", server.getLocalPort())) {
                    assertThrows(java.io.IOException.class, () -> TlsTransport.secureClient(plain, "localhost", 5000));
                }
                untrusted.get(10, TimeUnit.SECONDS);
            }
        } finally {
            worker.shutdownNow();
            saved.forEach((key, value) -> { if (value == null) System.clearProperty(key); else System.setProperty(key, value); });
        }
    }

    private static void acceptFailedHandshake(ServerSocket server) {
        try (var socket = server.accept()) {
            socket.setSoTimeout(5000);
            ((javax.net.ssl.SSLSocket) socket).startHandshake();
        } catch (Exception expected) { /* The client rejects identity/hostname and aborts this handshake. */ }
    }
}
