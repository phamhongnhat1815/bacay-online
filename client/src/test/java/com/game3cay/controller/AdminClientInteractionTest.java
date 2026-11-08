package com.game3cay.controller;

import com.game3cay.gui.*;
import com.game3cay.shared.model.*;
import com.game3cay.shared.network.*;
import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Runs the real client controller and socket transport against controlled server event sequences. */
class AdminClientInteractionTest {
    @BeforeAll static void loadAssets() { Assets.preload(); }

    @Test void adminBalancePushesUpdateOnlySelfOutsideBettingAndOldPlayerSnapshotsCannotRewindBalance() throws Exception {
        try (Fixture f = new Fixture()) {
            f.login();
            f.peer.send(Packet.serverPush(PacketType.PROFILE_RESULT, profile(2, "9000")));
            f.peer.send(Packet.serverPush(PacketType.PROFILE_RESULT, profile(1, "1200")));
            await(() -> new BigDecimal("1200").equals(f.view.balance));
            f.enterRoom();
            f.peer.send(Packet.serverPush(PacketType.PROFILE_RESULT, profile(1, "1300")));
            await(() -> new BigDecimal("1300").equals(f.view.balance));
            f.startRound();
            await(() -> new BigDecimal("1290").equals(f.view.balance));
            f.peer.send(Packet.serverPush(PacketType.PROFILE_RESULT, profile(1, "9000")));
            f.barrier("betting profile ignored");
            assertEquals(new BigDecimal("1290"), f.view.balance);
            f.peer.send(Packet.serverPush(PacketType.PLAYER_STATE, player("1250", 3)));
            f.peer.send(Packet.serverPush(PacketType.PLAYER_STATE, player("1290", 1)));
            f.barrier("stale snapshot ignored");
            assertEquals(new BigDecimal("1250"), f.view.balance);
            f.peer.send(Packet.serverPush(PacketType.ROUND_RESULT, new RoundResult("round-1",
                    List.of(new PlayerResult(1, "player", "Player", null, 1, "WIN", BigDecimal.TEN)),
                    List.of(), List.of(), "FINISHED", System.currentTimeMillis())));
            f.peer.send(Packet.serverPush(PacketType.ROOM_UPDATED, room("RESULT", 12)));
            f.peer.send(Packet.serverPush(PacketType.PROFILE_RESULT, profile(1, "1400")));
            f.barrier("settled adjustment");
            assertEquals(new BigDecimal("1400"), f.view.balance);
            f.peer.send(Packet.serverPush(PacketType.PROFILE_RESULT, profile(2, "9999")));
            f.barrier("other account ignored");
            assertEquals(new BigDecimal("1400"), f.view.balance);
        }
    }

    @Test void forcedLeaveDuringJoinDiscardsItsLateReplyAndReleasesControls() throws Exception {
        try (Fixture f = new Fixture()) {
            f.login();
            f.clickJoin();
            Incoming join = f.wire.next(PacketType.JOIN_ROOM);
            f.peer.send(Packet.serverPush(PacketType.LEAVE_ROOM, "Bàn không còn cho phép tham gia."));
            Incoming rooms = f.wire.next(PacketType.ROOM_LIST);
            f.peer.send(new Packet(PacketType.ROOM_UPDATED, join.packet.getRequestId(), room("WAITING", 10)));
            rooms.reply(PacketType.ROOM_LIST_RESULT, summaries());
            f.barrier("after forced leave");
            assertFalse(f.view.inRoom);
            assertTrue(f.view.enabled);
            assertTrue(f.view.signedIn);
        }
    }

    @Test void lateProfileAndUpdateRepliesKeepAnAdminBalancePushWhileStillApplyingOtherProfileFields() throws Exception {
        try (Fixture f = new Fixture()) {
            f.login();
            onEdt(f.view.openProfile);
            Incoming profileRead = f.wire.next(PacketType.GET_PROFILE);
            f.peer.send(Packet.serverPush(PacketType.PROFILE_RESULT, profile(1, "1100")));
            profileRead.reply(PacketType.PROFILE_RESULT, profile(1, "1000"));
            f.barrier("profile read after adjustment");
            assertEquals(new BigDecimal("1100"), f.view.balance);
            assertNotNull(f.view.editedProfile);
            assertEquals("Player 1", f.view.editedProfile.displayName());
            assertEquals(new BigDecimal("1100"), f.view.editedProfile.balance());

            onEdt(() -> f.view.saveProfile.accept(new UpdateProfileRequest("Updated player", "updated@example.com", null)));
            Incoming update = f.wire.next(PacketType.UPDATE_PROFILE);
            f.peer.send(Packet.serverPush(PacketType.PROFILE_RESULT, profile(1, "1200")));
            update.reply(PacketType.PROFILE_RESULT, new ProfileResult(1, "player", "Updated player", "updated@example.com",
                    null, new BigDecimal("1100"), 0, 0, 0));
            f.barrier("profile update after adjustment");
            assertEquals(new BigDecimal("1200"), f.view.balance);
            assertEquals("Updated player", f.view.accountName);
            assertTrue(f.view.enabled);
        }
    }

    @Test void initialJoinIsNotReportedAsReconnectEvenWhenItsRoomPushArrivesBeforeReply() throws Exception {
        try (Fixture f = new Fixture()) {
            f.login();
            f.clickJoin();
            Incoming join = f.wire.next(PacketType.JOIN_ROOM);
            f.peer.send(Packet.serverPush(PacketType.ROOM_UPDATED, room("WAITING", 10)));
            join.reply(PacketType.ROOM_UPDATED, room("WAITING", 10));
            await(() -> f.view.enabled && f.view.message.startsWith("Đã vào Bàn 01"));
            assertTrue(f.view.inRoom);
            assertTrue(f.view.message.contains("Chơi tiếp"));
            assertFalse(f.view.message.contains("khôi phục"));
        }
    }

    @Test void forcedLeaveWhileReadyClearsReconnectRoomAndIgnoresLateRoomReply() throws Exception {
        try (Fixture f = new Fixture()) {
            f.login();
            f.enterRoom();
            onEdt(() -> button(f.view.room, "Chơi tiếp").doClick(0));
            Incoming ready = f.wire.next(PacketType.SET_READY);
            f.peer.send(Packet.serverPush(PacketType.LEAVE_ROOM, "Không đủ điểm cược."));
            Incoming rooms = f.wire.next(PacketType.ROOM_LIST);
            f.peer.send(new Packet(PacketType.ROOM_UPDATED, ready.packet.getRequestId(), room("WAITING", 11)));
            rooms.reply(PacketType.ROOM_LIST_RESULT, summaries());
            f.barrier("ready reply discarded");
            assertFalse(f.view.inRoom);
            f.peer.socket.close();
            Incoming login = f.wire.next(PacketType.LOGIN);
            f.peer = login.peer;
            login.reply(PacketType.AUTH_RESULT, auth());
            Incoming refresh = f.wire.next(PacketType.ROOM_LIST);
            refresh.reply(PacketType.ROOM_LIST_RESULT, summaries());
            f.barrier("reconnected at lobby");
            assertFalse(f.view.inRoom);
            assertTrue(f.view.enabled);
        }
    }

    @Test void genuineReconnectStillRestoresTheRoomAndAcceptsItsFreshAuthoritativeSnapshot() throws Exception {
        try (Fixture f = new Fixture()) {
            f.login();
            f.enterRoom();
            f.peer.socket.close();
            Incoming login = f.wire.next(PacketType.LOGIN);
            f.peer = login.peer;
            login.reply(PacketType.AUTH_RESULT, auth());
            Incoming join = f.wire.next(PacketType.JOIN_ROOM);
            assertEquals(1, ((JoinRoomRequest) join.packet.getData()).roomId());
            join.reply(PacketType.ROOM_UPDATED, room("WAITING", 1));
            await(() -> f.view.enabled && f.view.inRoom && f.view.message.contains("Đã khôi phục kết nối"));
        }
    }

    @Test void accountLockPushReturnsToLoginAndDoesNotReconnectAfterSocketCloses() throws Exception {
        try (Fixture f = new Fixture()) {
            f.login();
            f.enterRoom();
            f.peer.send(Packet.error(null, ErrorCode.USER_BLOCKED, "Tài khoản đã bị quản trị viên khóa."));
            await(() -> !f.view.signedIn && f.view.message.contains("quản trị viên khóa"));
            f.peer.socket.close();
            assertNull(f.wire.incoming.poll(1800, TimeUnit.MILLISECONDS), "A blocked account must not automatically log in again");
            assertEquals(1, f.wire.connectionCount.get());
            assertFalse(f.view.inRoom);
            assertTrue(f.view.enabled);
        }
    }

    @Test void globalAdminAnnouncementRemainsInRoomChatAfterOtherStatusMessages() throws Exception {
        try (Fixture f = new Fixture()) {
            f.login();
            f.enterRoom();
            f.barrier("Bảo trì lúc 22 giờ");
            f.startRound();
            await(() -> new BigDecimal("1290").equals(f.view.balance));
            onEdt(() -> {
                JTextPane chat = component(f.view.room, JTextPane.class);
                assertNotNull(chat);
                assertTrue(chat.getText().contains("Bảo trì lúc 22 giờ"));
                assertTrue(chat.getText().contains("HỆ THỐNG"));
            });
        }
    }

    private static RoomSnapshot room(String status, long version) {
        return new RoomSnapshot(1, "R1", "Bàn 01", status, 1, 2, 8, BigDecimal.TEN,
                List.of(new RoomSnapshot.PlayerSlot(1, "Player", 1, false, "PLAYING"),
                        new RoomSnapshot.PlayerSlot(2, "Other", 2, false, "PLAYING")), version);
    }

    private static List<RoomSummary> summaries() {
        return List.of(new RoomSummary(1, "R1", "Bàn 01", "WAITING", 1, 8, BigDecimal.TEN));
    }

    private static AuthResult auth() {
        return new AuthResult(true, 1L, "player", "Player", new BigDecimal("1000"), "USER", null, "OK");
    }

    private static ProfileResult profile(long id, String balance) {
        return new ProfileResult(id, "player" + id, "Player " + id, null, null, new BigDecimal(balance), 0, 0, 0);
    }

    private static PlayerStateSnapshot player(String balance, long version) {
        return new PlayerStateSnapshot("round-1", List.of(new Card(Rank.ACE, Suit.RO), new Card(Rank.SEVEN, Suit.CO),
                new Card(Rank.NINE, Suit.BICH)), new BigDecimal(balance), BigDecimal.TEN, false, false, version);
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assertTrue(condition.getAsBoolean(), "Client did not reach the expected state");
    }

    private static void onEdt(Runnable action) throws Exception { SwingUtilities.invokeAndWait(action); }

    private static JButton button(Container parent, String text) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JButton button && text.equals(button.getText())) return button;
            if (child instanceof Container nested) {
                JButton found = button(nested, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T> T component(Container parent, Class<T> type) {
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) {
                T found = component(nested, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static final class Fixture implements AutoCloseable {
        final Wire wire = new Wire();
        FakeView view;
        Peer peer;
        final String tlsSetting = System.getProperty("BACAY_TLS_ENABLED");

        Fixture() throws Exception {
            System.setProperty("BACAY_TLS_ENABLED", "false");
            onEdt(() -> {
                view = new FakeView(wire.server.getLocalPort());
                new ConnectionController(view);
            });
        }

        void login() throws Exception {
            onEdt(view.submit);
            Incoming login = wire.next(PacketType.LOGIN);
            peer = login.peer;
            login.reply(PacketType.AUTH_RESULT, auth());
            wire.next(PacketType.ROOM_LIST).reply(PacketType.ROOM_LIST_RESULT, summaries());
            await(() -> view.signedIn && view.enabled);
        }

        void clickJoin() throws Exception {
            onEdt(() -> button(view.lobby, "Chơi ngay →").doClick(0));
        }

        void enterRoom() throws Exception {
            clickJoin();
            wire.next(PacketType.JOIN_ROOM).reply(PacketType.ROOM_UPDATED, room("WAITING", 10));
            await(() -> view.inRoom && view.enabled);
        }

        void startRound() throws Exception {
            peer.send(Packet.serverPush(PacketType.START_GAME, new GameStartInfo(1, "round-1", 1)));
            peer.send(Packet.serverPush(PacketType.ROOM_UPDATED, room("PLAYING", 11)));
            peer.send(Packet.serverPush(PacketType.GAME_UPDATED,
                    new GameStateSnapshot("round-1", "BETTING", 1L, List.of(1L, 2L), 0, 1)));
            peer.send(Packet.serverPush(PacketType.PLAYER_STATE, player("1290", 1)));
        }

        void barrier(String message) throws Exception {
            peer.send(Packet.serverPush(PacketType.CHAT_SYSTEM, new RoomChatEvent(0,
                    new ChatMessage(99L, "HỆ THỐNG", message, "SYSTEM", System.currentTimeMillis()))));
            await(() -> message.equals(view.message));
        }

        @Override public void close() throws Exception {
            try { onEdt(view.close); }
            finally {
                wire.close();
                if (tlsSetting == null) System.clearProperty("BACAY_TLS_ENABLED");
                else System.setProperty("BACAY_TLS_ENABLED", tlsSetting);
            }
        }
    }

    private record Incoming(Peer peer, Packet packet) {
        void reply(PacketType type, Object payload) throws Exception {
            peer.send(new Packet(type, packet.getRequestId(), payload));
        }
    }

    private static final class Peer {
        final Socket socket;
        final PacketIO.Output output;
        Peer(Socket socket) throws Exception { this.socket = socket; output = PacketIO.openOutput(socket); }
        void send(Packet packet) throws Exception { PacketIO.write(output, packet); }
    }

    private static final class Wire implements AutoCloseable {
        final ServerSocket server = new ServerSocket(0);
        final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        final BlockingQueue<Incoming> incoming = new LinkedBlockingQueue<>();
        final ConcurrentLinkedQueue<Peer> peers = new ConcurrentLinkedQueue<>();
        final AtomicInteger connectionCount = new AtomicInteger();

        Wire() throws Exception {
            workers.submit(() -> {
                try {
                    while (!server.isClosed()) {
                        Peer peer = new Peer(server.accept());
                        peers.add(peer);
                        connectionCount.incrementAndGet();
                        workers.submit(() -> {
                            try (var input = PacketIO.openInput(peer.socket)) {
                                while (!peer.socket.isClosed()) incoming.add(new Incoming(peer, PacketIO.read(input)));
                            } catch (IOException | ClassNotFoundException ignored) {}
                        });
                    }
                } catch (Exception ignored) {}
            });
        }

        Incoming next(PacketType type) throws Exception {
            Incoming request = incoming.poll(4, TimeUnit.SECONDS);
            assertNotNull(request, "No client request: " + type);
            assertEquals(type, request.packet.getType());
            return request;
        }

        @Override public void close() throws Exception {
            server.close();
            for (Peer peer : peers) peer.socket.close();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    private static final class FakeView implements ClientView {
        final LobbyPanel lobby = new LobbyPanel();
        final RoomPanel room = new RoomPanel();
        final int port;
        Runnable submit, close, openProfile;
        Consumer<UpdateProfileRequest> saveProfile;
        volatile boolean signedIn, inRoom, enabled;
        volatile String message = "";
        volatile String accountName;
        volatile BigDecimal balance;
        volatile ProfileResult editedProfile;

        FakeView(int port) { this.port = port; }
        public JFrame ownerFrame() { return null; }
        public String getHost() { return "127.0.0.1"; }
        public int getPort() { return port; }
        public String getUsername() { return "player"; }
        public char[] getPassword() { return "password123".toCharArray(); }
        public char[] getConfirmPassword() { return new char[0]; }
        public String getDisplayName() { return "Player"; }
        public String getEmail() { return ""; }
        public boolean isRegisterMode() { return false; }
        public void onSubmit(Runnable action) { submit = action; }
        public void onClose(Runnable action) { close = action; }
        public void onLogout(Runnable action) {}
        public void onProfile(Runnable action) { openProfile = action; }
        public void onHistory(Runnable action) {}
        public void onLeaderboard(Runnable action) {}
        public void onChangePassword(Consumer<ChangePasswordRequest> action) {}
        public void clearPasswords() {}
        public void registrationCompleted() {}
        public void showSignedOut() { signedIn = false; showLobby(); }
        public void showSignedIn(String name, long id) { signedIn = true; accountName = name; }
        public void setBalance(BigDecimal value) { balance = value; }
        public void showLobby() { room.clear(); inRoom = false; }
        public void showRoom(RoomSnapshot snapshot, long id) { room.render(snapshot, id); inRoom = true; }
        public LobbyPanel lobby() { return lobby; }
        public RoomPanel room() { return room; }
        public void setOperationEnabled(boolean value) {
            enabled = value;
            lobby.setActionsEnabled(value && signedIn);
            room.setActionsEnabled(value && signedIn);
        }
        public void showMessage(String value) { message = value == null ? "" : value; }
        public boolean confirmLeave() { return true; }
        public void editProfile(ProfileResult profile, boolean editable, Consumer<UpdateProfileRequest> save) {
            editedProfile = profile;
            saveProfile = save;
        }
        public void dispose() { room.clear(); }
    }
}
