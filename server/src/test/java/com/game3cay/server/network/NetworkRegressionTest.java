package com.game3cay.server.network;

import com.game3cay.shared.network.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.net.*;
import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class NetworkRegressionTest {
    @Test void rejectsOversizedFrameFromHeaderWithoutReadingPayload() throws Exception {
        try (var pair = new Wire()) {
            var raw = new DataOutputStream(pair.outgoing.getOutputStream());
            header(raw, Packet.VERSION, PacketType.ECHO_REQUEST.ordinal() + 1, PacketIO.MAX_FRAME_BYTES + 1);
            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> assertThrows(IOException.class, () -> PacketIO.read(pair.input)));
        }
    }

    @Test void rejectsUnknownVersionAndMagicBeforePayload() throws Exception {
        try (var pair = new Wire()) {
            header(new DataOutputStream(pair.outgoing.getOutputStream()), 2, 1, 1);
            assertThrows(IOException.class, () -> PacketIO.read(pair.input));
        }
        try (var pair = new Wire()) {
            pair.outgoing.getOutputStream().write(new byte[8]);
            assertThrows(IOException.class, () -> PacketIO.read(pair.input));
        }
    }

    @Test void rejectsUnlistedClassesAndHeaderPayloadTypeMismatch() throws Exception {
        try (var pair = new Wire()) {
            rawPacket(pair.outgoing, new Packet(PacketType.ECHO_REQUEST, new HashMap<>()), PacketType.ECHO_REQUEST);
            assertThrows(IOException.class, () -> PacketIO.read(pair.input));
        }
        try (var pair = new Wire()) {
            rawPacket(pair.outgoing, Packet.request(PacketType.ECHO_REQUEST, "hello"), PacketType.PING);
            assertThrows(IOException.class, () -> PacketIO.read(pair.input));
        }
    }

    @Test void manyIndependentFramesDoNotExhaustStreamFilterBudgets() throws Exception {
        try (var pair = new Wire(); var output = PacketIO.openOutput(pair.outgoing)) {
            for (int i = 0; i < 1500; i++) {
                PacketIO.write(output, Packet.request(PacketType.ECHO_REQUEST, "x".repeat(900)));
                assertEquals(PacketType.ECHO_REQUEST, PacketIO.read(pair.input).getType());
            }
        }
    }

    @Test void limitsAuthenticationAcrossConnectionsAndResetsAfterWindow() {
        AtomicLong now = new AtomicLong();
        AuthenticationLimiter limiter = new AuthenticationLimiter(now::get);
        for (int i = 0; i < 10; i++) assertTrue(limiter.allow("ip" + i, "SameUser", false));
        assertFalse(limiter.allow("another", "sameuser", false));
        now.addAndGet(java.util.concurrent.TimeUnit.MINUTES.toNanos(1));
        assertTrue(limiter.allow("another", "sameuser", false));
        limiter = new AuthenticationLimiter(now::get);
        for (int i = 0; i < 30; i++) assertTrue(limiter.allow("same-ip", "user" + i, false));
        assertFalse(limiter.allow("same-ip", "newuser", false));
        AuthenticationLimiter registration = new AuthenticationLimiter(now::get);
        for (int i = 0; i < 10; i++) assertTrue(registration.allow("same-ip", "user" + i, true));
        assertFalse(registration.allow("same-ip", "newuser", true));
    }

    private static void rawPacket(Socket socket, Packet packet, PacketType headerType) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var objects = new ObjectOutputStream(bytes)) { objects.writeObject(packet); }
        var raw = new DataOutputStream(socket.getOutputStream());
        header(raw, Packet.VERSION, headerType.ordinal() + 1, bytes.size());
        raw.write(bytes.toByteArray()); raw.flush();
    }

    private static void header(DataOutputStream raw, int version, int type, int length) throws IOException {
        raw.writeShort(PacketIO.MAGIC); raw.writeByte(version); raw.writeByte(type); raw.writeInt(length); raw.flush();
    }

    private static final class Wire implements AutoCloseable {
        final ServerSocket server = new ServerSocket(0);
        final Socket outgoing = new Socket("127.0.0.1", server.getLocalPort());
        final Socket incoming = server.accept();
        final PacketIO.Input input = PacketIO.openInput(incoming);
        Wire() throws Exception {}
        @Override public void close() throws IOException { outgoing.close(); incoming.close(); server.close(); }
    }
}
