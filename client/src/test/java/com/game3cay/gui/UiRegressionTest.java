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

    @Test void invalidManualRaiseStaysInTheUiInsteadOfReachingTheSocket() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GamePanel panel = new GamePanel();
            var sent = new java.util.ArrayList<GameActionRequest>();
            try {
                panel.start("raise-input", 1);
                panel.mine(new PlayerStateSnapshot("raise-input", List.of(new Card(Rank.ACE, Suit.RO),
                        new Card(Rank.TWO, Suit.CO), new Card(Rank.THREE, Suit.BICH)),
                        new BigDecimal("990"), BigDecimal.TEN, false, false, 1));
                panel.bets(new BetStateSnapshot("raise-input", 1L, BigDecimal.TEN,
                        new BigDecimal("20"), List.of(), List.of(), 1));
                panel.game(new GameStateSnapshot("raise-input", "BETTING", 1L, List.of(1L, 2L), 0, 1));
                panel.onAction(sent::add);
                panel.setActionsEnabled(true);
                var amount = UiLayoutTest.components(panel, javax.swing.JTextField.class).getFirst();
                var raise = UiLayoutTest.button(panel, "Tố đến");
                assertTrue(raise.isEnabled());
                for (String invalid : List.of("1E2", "0", "NaN", "20.001", "1000000000000000")) {
                    amount.setText(invalid);
                    raise.doClick(0);
                    assertTrue(sent.isEmpty(), "Invalid raise reached network callback: " + invalid);
                }
                amount.setText("100");
                raise.doClick(0);
                assertEquals(1, sent.size());
                assertEquals(new BigDecimal("100"), sent.getFirst().raiseTo());
            } finally { panel.clear(); }
        });
    }

    @Test void squeezeUsesPointsForSequencesAndShowsTenPoints() {
        assertEquals("6 ĐIỂM", CardSqueezeDialog.evaluateHandTitle(List.of(
                new Card(Rank.ACE, Suit.RO), new Card(Rank.TWO, Suit.CO), new Card(Rank.THREE, Suit.BICH))));
        assertEquals("10 ĐIỂM", CardSqueezeDialog.evaluateHandTitle(List.of(
                new Card(Rank.TWO, Suit.RO), new Card(Rank.THREE, Suit.CO), new Card(Rank.FIVE, Suit.BICH))));
        assertEquals("SÁP 7", CardSqueezeDialog.evaluateHandTitle(List.of(
                new Card(Rank.SEVEN, Suit.RO), new Card(Rank.SEVEN, Suit.CO), new Card(Rank.SEVEN, Suit.BICH))));
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
