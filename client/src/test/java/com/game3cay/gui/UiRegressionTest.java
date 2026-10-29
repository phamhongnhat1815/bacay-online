package com.game3cay.gui;

import com.game3cay.shared.model.*;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiRegressionTest {
    @BeforeAll static void preloadImagesOffEdt() {
        assertFalse(SwingUtilities.isEventDispatchThread());
        Assets.preload();
    }

    @Test void allButtonVariantsIncludingUnknownPaintInBothEnabledAndDisabledStates() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (String variant : new String[] {"blue", "gold", "green", "orange", "red", "plain", "navy", "unknown"}) {
                var button = new Theme.Button("Test", variant);
                button.setSize(150, 44);
                var graphics = new BufferedImage(150, 44, BufferedImage.TYPE_INT_ARGB).createGraphics();
                try {
                    assertDoesNotThrow(() -> button.paint(graphics), variant);
                    button.setEnabled(false);
                    assertDoesNotThrow(() -> button.paint(graphics), variant);
                } finally { graphics.dispose(); }
            }
        });
    }

    @Test void waitingSnapshotClearsAnOldRoundEvenWhenItsResultWasMissed() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GamePanel panel = new GamePanel();
            panel.start("old-round", 1);
            panel.game(new GameStateSnapshot("old-round", "BETTING", 1L, List.of(1L, 2L), 0, 1));
            assertTrue(panel.active());
            panel.room(new RoomSnapshot(1, "1", "Bàn 1", "WAITING", 1, 2, 8, BigDecimal.TEN,
                    List.of(new RoomSnapshot.PlayerSlot(1, "One", 1, false, "PLAYING")), 2), 1);
            assertFalse(panel.accepts("old-round"));
            assertFalse(panel.active());
            panel.clear();
        });
    }

    @Test void squeezeUsesServerHandClassificationAndZeroStaysZero() {
        assertTrue(CardSqueezeDialog.evaluateHandTitle(List.of(
                new Card(Rank.JACK, Suit.RO), new Card(Rank.QUEEN, Suit.CO), new Card(Rank.KING, Suit.BICH))).contains("LIÊNG"));
        String zero = CardSqueezeDialog.evaluateHandTitle(List.of(
                new Card(Rank.TWO, Suit.RO), new Card(Rank.THREE, Suit.CO), new Card(Rank.FIVE, Suit.BICH)));
        assertTrue(zero.contains("0 ĐIỂM"));
        assertFalse(zero.contains("10 ĐIỂM"));
    }

    @Test void idleAndHiddenTablesDoNotRunTheAnimationTimer() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GameTablePanel table = new GameTablePanel();
            table.addNotify();
            try {
                assertFalse(table.animationRunning());
                table.state(new GameStateSnapshot("r", "BETTING", 1L, List.of(1L, 2L), 0, 1));
                assertEquals(table.isShowing(), table.animationRunning());
                table.setVisible(false);
                assertFalse(table.animationRunning());
                table.clear();
                assertFalse(table.animationRunning());
            } finally { table.removeNotify(); }
        });
    }
}
