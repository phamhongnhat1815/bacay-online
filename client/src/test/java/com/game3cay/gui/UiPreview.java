package com.game3cay.gui;

import com.game3cay.shared.model.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Offline visual QA. Run with client/test-classes, client/classes and shared/classes on the classpath. */
public final class UiPreview {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]); Files.createDirectories(output);
        Assets.preload();
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = null;
            try {
                Theme.install();
                frame = new MainFrame();
                for (int width : new int[]{1100, 1440}) {
                    int height = width == 1100 ? 700 : 900;
                    frame.showSignedOut();
                    capture(frame.getContentPane(), width, height, output.resolve("login-" + width + ".png"));
                    AuthPanel auth = components(frame.getContentPane(), AuthPanel.class).getFirst();
                    var mode = AuthPanel.class.getDeclaredMethod("switchMode", boolean.class);
                    mode.setAccessible(true); mode.invoke(auth, true);
                    capture(frame.getContentPane(), width, height, output.resolve("register-" + width + ".png"));
                    mode.invoke(auth, false);
                    frame.showSignedIn("Khánh · Cùng bạn bè", 1);
                    frame.setBalance(new BigDecimal("12500"));
                    frame.showLobby(); frame.lobby().setRooms(rooms()); frame.setOperationEnabled(true);
                    capture(frame.getContentPane(), width, height, output.resolve("lobby-" + width + ".png"));
                    RoomSnapshot waiting = room("WAITING");
                    frame.showRoom(waiting, 1); frame.setOperationEnabled(true);
                    frame.room().appendChat(new ChatMessage(2L, "Minh Anh", "Cả nhà sẵn sàng chưa?", "TEXT", System.currentTimeMillis()));
                    capture(frame.getContentPane(), width, height, output.resolve("waiting-" + width + ".png"));
                    frame.showRoom(room("PLAYING"), 1);
                    var game = frame.room().game(); game.start("preview", 1);
                    game.mine(new PlayerStateSnapshot("preview", List.of(new Card(Rank.ACE, Suit.RO), new Card(Rank.KING, Suit.CO),
                            new Card(Rank.QUEEN, Suit.BICH)), new BigDecimal("12500"), BigDecimal.TEN, false, false, 1));
                    game.bets(new BetStateSnapshot("preview", 1L, new BigDecimal("20"), new BigDecimal("90"), List.of(), List.of(), 1));
                    game.game(new GameStateSnapshot("preview", "BETTING", 1L, List.of(1L, 2L, 3L), 0, 1));
                    // Capture the settled hand, independently of machine speed during the deal animation.
                    var dealtAt = GameTablePanel.class.getDeclaredField("dealtAt");
                    dealtAt.setAccessible(true); dealtAt.setLong(game.table(), 0);
                    capture(frame.getContentPane(), width, height, output.resolve("playing-" + width + ".png"));
                    frame.room().clear();
                    var fullRoom = new ArrayList<>(waiting.players());
                    for (int seat = 4; seat <= 8; seat++) fullRoom.add(new RoomSnapshot.PlayerSlot(seat, "Người chơi " + seat, seat, false, "PLAYING"));
                    frame.showRoom(new RoomSnapshot(3, "3", "Bàn 03", "WAITING", 1, 2, 8, BigDecimal.TEN, fullRoom, 0), 1);
                    frame.setOperationEnabled(true);
                    capture(frame.getContentPane(), width, height, output.resolve("full-table-" + width + ".png"));
                    frame.room().clear();
                }
                var points = new PointExchangeDialog(frame);
                try {
                    points.context(1, room("WAITING"), new BigDecimal("12500"), java.util.Set.of());
                    points.requests(new PageResult<>(List.of(), 0, 20, 0, 0));
                    capture(points.getContentPane(), 1006, 550, output.resolve("points.png"));
                    long now = System.currentTimeMillis();
                    points.requests(new PageResult<>(List.of(new PointRequestItem("preview-request", 2, "Minh Anh", 1, "Khánh", 3,
                            new BigDecimal("100"), "Cho mình xin ít điểm chơi tiếp nhé", PointRequestStatus.PENDING, now, now + 120000, null)), 0, 20, 1, 1));
                    capture(points.getContentPane(), 1006, 550, output.resolve("points-request.png"));
                } finally { points.dispose(); }
            } catch (Exception e) { throw new RuntimeException(e); }
            finally { if (frame != null) frame.dispose(); }
        });
    }
    private static void capture(Container root, int w, int h, Path path) throws Exception {
        root.setSize(w, h); for (int i = 0; i < 3; i++) layout(root);
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics(); root.printAll(g); g.dispose();
        ImageIO.write(image, "png", path.toFile());
    }
    private static void layout(Container c) {
        c.doLayout(); for (Component child : c.getComponents()) if (child instanceof Container nested) layout(nested);
    }
    private static <T> List<T> components(Container root, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Component c : root.getComponents()) {
            if (type.isInstance(c)) result.add(type.cast(c));
            if (c instanceof Container nested) result.addAll(components(nested, type));
        }
        return result;
    }
    private static List<RoomSummary> rooms() {
        List<RoomSummary> result = new ArrayList<>();
        for (int i = 1; i <= 20; i++) result.add(new RoomSummary(i, "" + i, "Bàn " + i,
                i == 4 ? "PLAYING" : i == 7 ? "RESULT" : "WAITING", i == 2 ? 4 : i == 3 ? 3 : i == 4 ? 6 : i == 6 ? 8 : 0,
                8, BigDecimal.valueOf(i < 5 ? 10 : i < 11 ? 50 : 100)));
        return result;
    }
    private static RoomSnapshot room(String status) {
        return new RoomSnapshot(3, "3", "Bàn 03", status, 1, 2, 8, BigDecimal.TEN,
                List.of(new RoomSnapshot.PlayerSlot(1, "Khánh", 1, false, "PLAYING"),
                        new RoomSnapshot.PlayerSlot(2, "Minh Anh", 2, true, "PLAYING"),
                        new RoomSnapshot.PlayerSlot(3, "Hoàng Nam", 3, false, "PLAYING")), 0);
    }
}
