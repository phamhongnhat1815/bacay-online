package com.game3cay.gui;

import com.game3cay.shared.model.RoomSnapshot;
import com.game3cay.shared.model.RoomSummary;
import java.awt.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiLayoutTest {
    @BeforeAll static void prepare() throws Exception {
        Assets.preload();
        SwingUtilities.invokeAndWait(Theme::install);
    }

    @Test void lobbyFiltersKeepRoomIdentityAndQuickPlayUsesAllRooms() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            LobbyPanel lobby = new LobbyPanel();
            lobby.setRooms(rooms()); lobby.setActionsEnabled(true);
            AtomicLong joined = new AtomicLong();
            lobby.onJoin(joined::set);
            JTextField search = components(lobby, JTextField.class).getFirst();
            search.setText("Bàn 03");
            List<JButton> cards = components(lobby, JButton.class).stream()
                    .filter(b -> b.getAccessibleContext().getAccessibleName().matches("Bàn \\d+") && b.isVisible()).toList();
            assertEquals(1, cards.size());
            cards.getFirst().doClick(0); assertEquals(3, joined.get());
            button(lobby, "Chơi ngay →").doClick(0);
            assertEquals(2, joined.get(), "Quick play must find the most occupied joinable room even outside the filter");
            search.setText("999");
            button(lobby, "Xóa bộ lọc").doClick(0);
            assertEquals("", search.getText());
            button(lobby, "Bàn trống").doClick(0);
            assertFalse(components(lobby, JButton.class).stream()
                    .filter(b -> "Bàn 2".equals(b.getAccessibleContext().getAccessibleName())).findFirst().orElseThrow().isVisible());
        });
    }

    @Test void lobbyCardsAndHeaderStayUsableAtMinimumAndNormalWindowSizes() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (int width : new int[]{1084, 1424}) {
                LobbyPanel lobby = new LobbyPanel(); lobby.setRooms(rooms());
                JPanel root = new JPanel(new BorderLayout());
                var points = new Theme.Button("Yêu cầu điểm (99)", "plain");
                var header = new SessionHeader(Theme.label("Tên hiển thị dài của người chơi", 12, Theme.TEXT),
                        Theme.label("1.000.000 điểm", 20, Theme.GOLD), new Theme.Button("", "plain"),
                        new Theme.Button("", "plain"), points, new Theme.Button("Hồ sơ", "plain"),
                        new Theme.Button("Luật chơi", "plain"), new Theme.Button("Âm: Bật", "plain"),
                        new Theme.Button("Đăng xuất", "plain"));
                root.add(header, BorderLayout.NORTH); root.add(lobby, BorderLayout.CENTER);
                root.setSize(width, 660); layout(root);
                for (JButton card : components(lobby, JButton.class)) {
                    if (card.getAccessibleContext().getAccessibleName().matches("Bàn \\d+")) {
                        assertTrue(card.getWidth() >= 224, "Room cards must remain readable");
                        assertEquals(166, card.getHeight());
                    }
                }
                Rectangle bounds = SwingUtilities.convertRectangle(points.getParent(), points.getBounds(), header);
                assertTrue(header.contains(bounds.x + bounds.width - 1, bounds.y + bounds.height - 1), "Header navigation must fit");
            }
        });
    }

    @Test void waitingControlsFitWithoutScalingSwingTextAndReadinessIsAccurate() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RoomPanel room = new RoomPanel();
            room.render(waitingRoom(), 1); room.setActionsEnabled(true);
            room.setSize(1084, 570); layout(room);
            JButton ready = button(room, "Chơi tiếp"), start = button(room, "Bắt đầu ngay");
            assertTrue(ready.isEnabled()); assertFalse(start.isEnabled());
            assertTrue(start.getWidth() >= start.getPreferredSize().width);
            assertTrue(ready.getWidth() >= ready.getPreferredSize().width);
            assertTrue(components(room, JLabel.class).stream().noneMatch(l -> l.getText().startsWith("Tất cả đã sẵn sàng")));
            assertTrue(components(room, JLabel.class).stream().anyMatch(l -> l.getText().contains("Bấm Chơi tiếp")));
            assertEquals(0, room.game().table().getComponentCount(), "The painted table must have no readiness overlay");
            room.clear();
        });
    }

    static List<RoomSummary> rooms() {
        List<RoomSummary> rows = new ArrayList<>();
        for (int i = 1; i <= 20; i++) rows.add(new RoomSummary(i, "" + i, "Bàn " + i,
                i == 4 ? "PLAYING" : "WAITING", i == 2 ? 4 : i == 3 ? 2 : i == 4 ? 6 : 0, 8, BigDecimal.TEN));
        return rows;
    }
    static RoomSnapshot waitingRoom() {
        return new RoomSnapshot(3, "3", "Bàn 03", "WAITING", 1, 2, 8, BigDecimal.TEN,
                List.of(new RoomSnapshot.PlayerSlot(1, "Khánh", 1, false, "PLAYING"),
                        new RoomSnapshot.PlayerSlot(2, "Minh Anh", 2, true, "PLAYING"),
                        new RoomSnapshot.PlayerSlot(3, "Hoàng Nam", 3, false, "PLAYING")), 0);
    }
    static void layout(Container root) { for (int i = 0; i < 3; i++) layoutTree(root); }
    private static void layoutTree(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) if (child instanceof Container container) layoutTree(container);
    }
    static JButton button(Container root, String text) {
        return components(root, JButton.class).stream().filter(b -> text.equals(b.getText())).findFirst().orElseThrow();
    }
    static <T extends Component> List<T> components(Container root, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) result.add(type.cast(child));
            if (child instanceof Container container) result.addAll(components(container, type));
        }
        return result;
    }
}
