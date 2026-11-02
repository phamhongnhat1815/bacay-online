package com.game3cay.admin.controller;

import com.game3cay.admin.gui.*;
import com.game3cay.shared.model.*;
import com.game3cay.shared.network.*;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real controller, sender/reader and Swing panels against delayed wire replies. */
class AdminControllerIntegrationTest {
    private String previousTls;
    private View view;
    private AdminController controller;

    @BeforeEach void prepare() throws Exception {
        previousTls = System.getProperty("BACAY_TLS_ENABLED");
        System.setProperty("BACAY_TLS_ENABLED", "false");
        SwingUtilities.invokeAndWait(() -> {
            view = new View();
            controller = new AdminController(view);
        });
    }

    @AfterEach void close() throws Exception {
        SwingUtilities.invokeAndWait(() -> view.logout.run());
        if (previousTls == null) System.clearProperty("BACAY_TLS_ENABLED");
        else System.setProperty("BACAY_TLS_ENABLED", previousTls);
    }

    @Test void oldSearchRepliesCannotReplaceTheMostRecentlyRequestedFilter() throws Exception {
        try (var server = new ServerSocket(0); var peer = login(server, "first")) {
            SwingUtilities.invokeAndWait(() -> {
                controller.loadUsersPage(new AdminUserFilterRequest("alice", "ALL", 0, 20));
                controller.loadUsersPage(new AdminUserFilterRequest("bob", "BLOCKED", 0, 20));
                controller.loadDashboardStats();
            });
            Packet oldSearch = peer.read(PacketType.ADMIN_USERS_PAGE_REQ);
            Packet newSearch = peer.read(PacketType.ADMIN_USERS_PAGE_REQ);
            Packet barrier = peer.read(PacketType.ADMIN_STATS_REQ);
            peer.users(oldSearch, "alice", 0, 1);
            peer.reply(barrier, PacketType.ADMIN_STATS_RESP, new AdminStatsSnapshot(99, 0, 0, BigDecimal.ZERO, 0));
            awaitEdt(() -> labels(view.dashboard).contains("99"));
            SwingUtilities.invokeAndWait(() -> assertEquals(0, table(view.users).getRowCount(), "Old search must stay hidden while newer filter is pending"));
            peer.users(newSearch, "bob", 0, 1);
            awaitEdt(() -> table(view.users).getRowCount() == 1);
            SwingUtilities.invokeAndWait(() -> assertEquals("bob", table(view.users).getValueAt(0, 1)));
        }
    }

    @Test void broadcastingWithoutAConnectionReleasesTheSendButton() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTextField message = components(view.dashboard, JTextField.class).getFirst();
            JButton send = components(view.dashboard, JButton.class).stream()
                    .filter(button -> button.getText().equals("GỬI THÔNG BÁO TẤT CẢ")).findFirst().orElseThrow();
            message.setText("Thông báo khi đường truyền đã ngắt");
            message.postActionEvent();
            assertTrue(send.isEnabled());
            assertEquals("Thông báo khi đường truyền đã ngắt", message.getText());
            assertTrue(labels(view.dashboard).stream().anyMatch(text -> text.contains("Chưa kết nối tới máy chủ")));
        });
    }

    @Test void logoutWhileReadsArePendingCannotDamageTheNextLogin() throws Exception {
        try (var server = new ServerSocket(0); var oldPeer = login(server, "first")) {
            SwingUtilities.invokeAndWait(() -> controller.loadUsersPage(new AdminUserFilterRequest("old", "ALL", 0, 20)));
            oldPeer.read(PacketType.ADMIN_USERS_PAGE_REQ);
            SwingUtilities.invokeAndWait(() -> {
                view.logout.run();
                enterLogin(server.getLocalPort(), "second");
            });
            try (var nextPeer = acceptLogin(server, "second")) {
                awaitEdt(() -> view.console && "second".equals(view.name));
                SwingUtilities.invokeAndWait(() -> {
                    assertEquals("Sẵn sàng", view.status);
                    assertTrue(view.console);
                    assertEquals(0, table(view.users).getRowCount());
                });
                controllerRefreshOnEdt();
                Packet refresh = nextPeer.read(PacketType.ADMIN_STATS_REQ);
                nextPeer.reply(refresh, PacketType.ADMIN_STATS_RESP, new AdminStatsSnapshot(77, 0, 0, BigDecimal.ZERO, 0));
                awaitEdt(() -> labels(view.dashboard).contains("77"));
            }
        }
    }

    @Test void terminalBlockReturnsToLoginAndKeepsTheServerReasonAfterDisconnect() throws Exception {
        try (var server = new ServerSocket(0); var peer = login(server, "first")) {
            peer.push(Packet.error(null, ErrorCode.USER_BLOCKED, "Tài khoản đã bị khóa bởi quản trị viên"));
            peer.close();
            awaitEdt(() -> !view.console && labels(view.auth).contains("Tài khoản đã bị khóa bởi quản trị viên"));
            SwingUtilities.invokeAndWait(() -> assertTrue(labels(view.auth).contains("Tài khoản đã bị khóa bởi quản trị viên")));
            try (var nextPeer = login(server, "second")) {
                awaitEdt(() -> view.console && "second".equals(view.name));
                SwingUtilities.invokeAndWait(() -> assertFalse(labels(view.auth).contains("Tài khoản đã bị khóa bởi quản trị viên")));
            }
        }
    }

    @Test void updatingAUserKeepsTheFilterAndRepairsAnEmptyLastPage() throws Exception {
        try (var server = new ServerSocket(0); var peer = login(server, "first")) {
            SwingUtilities.invokeAndWait(() -> controller.loadUsersPage(new AdminUserFilterRequest("target", "ACTIVE", 1, 20)));
            peer.users(peer.read(PacketType.ADMIN_USERS_PAGE_REQ), "target", 1, 2);
            awaitEdt(() -> view.users.getCurrentPage() == 1);
            SwingUtilities.invokeAndWait(() -> controller.handleLockUser(new AdminLockUserRequest(9, true)));
            Packet lock = peer.read(PacketType.ADMIN_LOCK_USER_REQ);
            peer.reply(lock, PacketType.ACK, null);
            Packet filtered = peer.read(PacketType.ADMIN_USERS_PAGE_REQ);
            assertEquals(new AdminUserFilterRequest("target", "ACTIVE", 1, 20), filtered.getData());
            peer.reply(filtered, PacketType.ADMIN_USERS_PAGE_RESP, new PageResult<>(List.of(), 1, 20, 20, 1));
            peer.reply(peer.read(PacketType.ADMIN_STATS_REQ), PacketType.ADMIN_STATS_RESP, new AdminStatsSnapshot(1, 0, 0, BigDecimal.ZERO, 0));
            peer.reply(peer.read(PacketType.ADMIN_ROOMS_MONITOR_REQ), PacketType.ADMIN_ROOMS_MONITOR_RESP, List.of());
            Packet repaired = peer.read(PacketType.ADMIN_USERS_PAGE_REQ);
            assertEquals(new AdminUserFilterRequest("target", "ACTIVE", 0, 20), repaired.getData());
            peer.users(repaired, "target", 0, 1);
            awaitEdt(() -> view.users.getCurrentPage() == 0 && table(view.users).getRowCount() == 1);
        }
    }

    private void controllerRefreshOnEdt() throws Exception {
        SwingUtilities.invokeAndWait(controller::loadDashboardStats);
    }

    private Peer login(ServerSocket server, String name) throws Exception {
        SwingUtilities.invokeAndWait(() -> enterLogin(server.getLocalPort(), name));
        return acceptLogin(server, name);
    }

    private Peer acceptLogin(ServerSocket server, String name) throws Exception {
        server.setSoTimeout(5000);
        Peer peer = new Peer(server.accept());
        Packet login = peer.read(PacketType.LOGIN);
        peer.reply(login, PacketType.AUTH_RESULT, new AuthResult(true, 1L, name, name, BigDecimal.ZERO, "ADMIN", null, "ok"));
        peer.reply(peer.read(PacketType.ADMIN_STATS_REQ), PacketType.ADMIN_STATS_RESP, new AdminStatsSnapshot(1, 0, 0, BigDecimal.ZERO, 0));
        peer.reply(peer.read(PacketType.ADMIN_USERS_PAGE_REQ), PacketType.ADMIN_USERS_PAGE_RESP, PageResult.empty(0, 20));
        peer.reply(peer.read(PacketType.ADMIN_ROOMS_MONITOR_REQ), PacketType.ADMIN_ROOMS_MONITOR_RESP, List.of());
        peer.reply(peer.read(PacketType.ADMIN_LEDGER_PAGE_REQ), PacketType.ADMIN_LEDGER_PAGE_RESP, PageResult.empty(0, 20));
        awaitEdt(() -> view.console && name.equals(view.name));
        return peer;
    }

    private void enterLogin(int port, String name) {
        List<JTextField> fields = components(view.auth, JTextField.class);
        fields.get(0).setText("127.0.0.1");
        fields.get(1).setText(String.valueOf(port));
        fields.get(2).setText(name);
        fields.get(3).setText("password123");
        components(view.auth, JButton.class).stream().filter(b -> b.getText().equals("ĐĂNG NHẬP ADMIN")).findFirst().orElseThrow().doClick(0);
    }

    private static void awaitEdt(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(4);
        boolean[] done = {false};
        do {
            SwingUtilities.invokeAndWait(() -> done[0] = condition.getAsBoolean());
            if (done[0]) return;
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        fail("Timed out waiting for admin UI state");
    }

    private static JTable table(Container root) { return components(root, JTable.class).getFirst(); }
    private static List<String> labels(Container root) { return components(root, JLabel.class).stream().map(JLabel::getText).toList(); }
    private static <T extends Component> List<T> components(Container root, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) result.add(type.cast(child));
            if (child instanceof Container nested) result.addAll(components(nested, type));
        }
        return result;
    }

    private static final class Peer implements AutoCloseable {
        final Socket socket;
        final PacketIO.Input input;
        final PacketIO.Output output;
        Peer(Socket socket) throws Exception {
            this.socket = socket;
            input = PacketIO.openInput(socket);
            input.setIdleTimeoutMillis(5000);
            output = PacketIO.openOutput(socket);
            push(Packet.serverPush(PacketType.SERVER_NOTICE, "ready"));
        }
        Packet read(PacketType type) throws Exception {
            Packet packet = PacketIO.read(input);
            assertEquals(type, packet.getType());
            return packet;
        }
        void reply(Packet request, PacketType type, Object data) throws Exception { push(new Packet(type, request.getRequestId(), data)); }
        void users(Packet request, String name, int page, int pages) throws Exception {
            reply(request, PacketType.ADMIN_USERS_PAGE_RESP, new PageResult<>(List.of(new AdminUserItem(9, name, name, null,
                    BigDecimal.TEN, "ACTIVE", "USER", 0, 0, 0, 1)), page, 20, (long) pages * 20, pages));
        }
        void push(Packet packet) throws Exception { PacketIO.write(output, packet); }
        public void close() throws Exception { socket.close(); }
    }

    private static final class View implements AdminView {
        final AdminAuthPanel auth = new AdminAuthPanel();
        final DashboardPanel dashboard = new DashboardPanel();
        final UsersPanel users = new UsersPanel();
        final RoomsPanel rooms = new RoomsPanel();
        final LedgerPanel ledger = new LedgerPanel();
        Runnable logout;
        boolean console;
        String name;
        String status = "Sẵn sàng";
        public AdminAuthPanel getAuthPanel() { return auth; }
        public DashboardPanel getDashboardPanel() { return dashboard; }
        public UsersPanel getUsersPanel() { return users; }
        public RoomsPanel getRoomsPanel() { return rooms; }
        public LedgerPanel getLedgerPanel() { return ledger; }
        public void setOnLogout(Runnable callback) { logout = callback; }
        public void setOnChangePassword(Consumer<ChangePasswordRequest> callback) {}
        public void showAuthScreen() {
            console = false;
            auth.reset(); dashboard.reset(); users.reset();
            rooms.updateRooms(List.of()); ledger.updatePage(PageResult.empty(0, 20));
            status = "Sẵn sàng";
        }
        public void showConsoleScreen(String name, String role) { console = true; this.name = name; }
        public void setStatusMessage(String text, boolean error) { status = text; }
        public Frame window() { return null; }
    }
}
