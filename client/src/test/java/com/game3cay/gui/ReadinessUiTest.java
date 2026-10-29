package com.game3cay.gui;

import com.game3cay.shared.model.GameStateSnapshot;
import com.game3cay.shared.model.RoomSnapshot;
import java.awt.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReadinessUiTest {
    @BeforeAll static void prepare() throws Exception {
        Assets.preload(); SwingUtilities.invokeAndWait(Theme::install);
    }

    @Test void countdownStaysBelowTheTableAndHidesOnStartBeforeTheRoomSnapshotArrives() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (int width : new int[]{1084, 1424}) {
                RoomPanel panel = new RoomPanel();
                try {
                    panel.render(snapshot(true, "WAITING", 4, 1), 1); panel.setActionsEnabled(true);
                    panel.setSize(width, 570); UiLayoutTest.layout(panel);
                    JComponent bar = readyBar(panel);
                    assertTrue(bar.isVisible());
                    Rectangle bounds = SwingUtilities.convertRectangle(bar.getParent(), bar.getBounds(), panel.game());
                    Rectangle table = panel.game().table().getBounds();
                    assertTrue(bounds.y >= table.y + table.height, "Ready controls must never cover the table");
                    JButton pause = UiLayoutTest.button(panel, "Tạm nghỉ");
                    assertTrue(pause.isEnabled());
                    assertTrue(pause.getWidth() >= pause.getPreferredSize().width);
                    assertTrue(UiLayoutTest.components(panel, JCheckBox.class).isEmpty(), "No duplicate auto-ready control");
                    assertEquals(0, panel.game().table().getComponentCount());
                    panel.game().start("new-round", 1);
                    assertFalse(bar.isVisible(), "START_GAME must immediately hide readiness, even before the next room snapshot");
                    panel.game().game(new GameStateSnapshot("new-round", "BETTING", 1L, List.of(1L, 2L), 0, 1));
                    assertFalse(bar.isVisible());
                } finally { panel.clear(); }
            }
        });
    }

    @Test void snapshotsDoNotSendReadyRequestsOrUndoThePlayersPause() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RoomPanel panel = new RoomPanel();
            AtomicInteger actions = new AtomicInteger(); panel.onReady(actions::incrementAndGet);
            try {
                panel.render(snapshot(true, "WAITING", 5, 1), 1); panel.setActionsEnabled(true);
                assertEquals(0, actions.get());
                UiLayoutTest.button(panel, "Tạm nghỉ").doClick(0);
                assertEquals(1, actions.get());
                for (int version = 2; version <= 10; version++) panel.render(snapshot(false, "WAITING", 0, version), 1);
                assertEquals(1, actions.get(), "Rendering waiting snapshots must not auto-click ready again");
                JButton resume = UiLayoutTest.button(panel, "Chơi tiếp");
                assertTrue(resume.isEnabled());
                panel.setActionsEnabled(false); assertFalse(resume.isEnabled());
                panel.setActionsEnabled(true); resume.doClick(0);
                assertEquals(2, actions.get());
            } finally { panel.clear(); }
        });
    }

    @Test void spectatorCannotConfirmReadinessAndTheBarIsAbsentOutsideWaiting() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RoomPanel panel = new RoomPanel();
            try {
                RoomSnapshot room = new RoomSnapshot(1, "1", "Bàn 1", "WAITING", 2, 2, 8, BigDecimal.TEN,
                        List.of(new RoomSnapshot.PlayerSlot(1, "One", 1, false, "SPECTATOR"),
                                new RoomSnapshot.PlayerSlot(2, "Two", 2, true, "PLAYING")), 1, 0);
                panel.render(room, 1); panel.setActionsEnabled(true);
                assertFalse(UiLayoutTest.button(panel, "Chơi tiếp").isEnabled());
                assertTrue(readyBar(panel).isVisible());
                panel.render(snapshot(true, "RESULT", 0, 2), 1);
                assertFalse(readyBar(panel).isVisible());
                panel.clear(); assertFalse(readyBar(panel).isVisible());
            } finally { panel.clear(); }
        });
    }

    private static JComponent readyBar(RoomPanel panel) {
        return UiLayoutTest.components(panel, JComponent.class).stream()
                .filter(c -> "round-ready-bar".equals(c.getName())).findFirst().orElseThrow();
    }
    private static RoomSnapshot snapshot(boolean ready, String phase, int countdown, int version) {
        return new RoomSnapshot(1, "1", "Bàn 1", phase, 1, 2, 8, BigDecimal.TEN,
                List.of(new RoomSnapshot.PlayerSlot(1, "One", 1, ready, "PLAYING"),
                        new RoomSnapshot.PlayerSlot(2, "Two", 2, true, "PLAYING")), version, countdown);
    }
}
