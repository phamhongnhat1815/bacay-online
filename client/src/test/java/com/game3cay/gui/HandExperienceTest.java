package com.game3cay.gui;

import com.game3cay.shared.model.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HandExperienceTest {
    private static final List<Card> CARDS = List.of(new Card(Rank.ACE, Suit.RO),
            new Card(Rank.ACE, Suit.CO), new Card(Rank.ACE, Suit.BICH));

    @BeforeAll static void prepare() throws Exception {
        Assets.preload(); SwingUtilities.invokeAndWait(Theme::install);
    }

    @Test void networkUpdatesCannotRevealTheHandAndEveryNewRoundStartsFaceDown() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GamePanel game = new GamePanel();
            try {
                game.start("one", 1); game.setActionsEnabled(true);
                game.mine(mine("one", 1));
                HandRevealState state = revealState(game);
                assertEquals(0, state.count());
                state.reveal(1);
                game.start("one", 1);
                assertEquals(1, state.count(), "A duplicate round-start event must not reset progress");
                game.mine(mine("one", 2));
                game.game(new GameStateSnapshot("one", "BETTING", 1L, List.of(1L, 2L), 0, 2));
                assertEquals(1, state.count(), "A balance/turn snapshot must preserve the viewing choice");
                UiLayoutTest.button(game, "Mở hết").doClick(0);
                assertTrue(state.complete());
                UiLayoutTest.button(game, "Ẩn bài").doClick(0);
                assertTrue(state.covered());
                game.start("two", 1); game.mine(mine("two", 1));
                assertEquals(0, state.count());
                assertFalse(state.visible(0));
            } finally { game.clear(); }
        });
    }

    @Test void shortDragAndClosingDoNotRevealCardsWhileAFullDragOpensOnlyTheChosenCard() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            HandRevealState state = new HandRevealState();
            AtomicInteger closes = new AtomicInteger();
            CardSqueezePanel panel = panel(state, closes::incrementAndGet);
            drag(panel, 2, .25);
            assertEquals(0, state.count());
            drag(panel, 2, .8);
            assertEquals(1, state.count()); assertTrue(state.revealed(2));
            UiLayoutTest.button(panel, "Trở về bàn").doClick(0);
            assertEquals(1, closes.get()); assertEquals(1, state.count());
            CardSqueezePanel reopened = panel(state, () -> {});
            assertEquals(1, state.count());
            assertFalse(state.revealed(0)); assertFalse(state.revealed(1));
            assertTrue(UiLayoutTest.components(reopened, JLabel.class).stream().noneMatch(l -> l.getText().contains("SÁP")),
                    "Hand classification must not spoil the unopened cards");
            UiLayoutTest.button(reopened, "Mở cả 3 lá").doClick(0);
            assertEquals(3, state.count());
            assertTrue(UiLayoutTest.components(reopened, JLabel.class).stream().anyMatch(l -> l.getText().contains("SÁP")));
        });
    }

    @Test void staleSqueezeActionsCannotRevealCardsInTheFollowingRound() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            HandRevealState state = new HandRevealState();
            CardSqueezePanel oldPanel = panel(state, () -> {});
            state.reset();
            UiLayoutTest.button(oldPanel, "Mở cả 3 lá").doClick(0);
            drag(oldPanel, 0, .8);
            assertEquals(0, state.count(), "Queued input from an old window must not affect a new round");
        });
    }

    @Test void draggingDownCannotRevealACard() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            HandRevealState state = new HandRevealState();
            CardSqueezePanel panel = panel(state, () -> {});
            drag(panel, 0, -.85);
            assertEquals(0, state.count(), "Only an upward pull should reveal a card");
        });
    }

    @Test void upwardPullRevealsTheBottomGraduallyWithoutSpoilingTheCoveredTop() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            HandRevealState state = new HandRevealState();
            CardSqueezePanel first = panel(state, () -> {});
            CardSqueezePanel other = new CardSqueezePanel(List.of(new Card(Rank.KING, Suit.CO),
                    new Card(Rank.QUEEN, Suit.RO), new Card(Rank.JACK, Suit.BICH)), new HandRevealState(), () -> {}, () -> {});
            other.setSize(684, 510); UiLayoutTest.layout(other);
            pull(first, 0, .25, false); pull(other, 0, .25, false);
            Rectangle r = first.cardBounds(0);
            BufferedImage a = canvasImage(first), b = canvasImage(other);
            assertArrayEquals(a.getRGB(r.x + 4, r.y + 4, r.width - 8, r.height / 2, null, 0, r.width - 8),
                    b.getRGB(r.x + 4, r.y + 4, r.width - 8, r.height / 2, null, 0, r.width - 8),
                    "Private values must not be painted through the covered upper portion");
            int bottom = r.y + r.height * 4 / 5;
            assertFalse(java.util.Arrays.equals(a.getRGB(r.x + 4, bottom, r.width - 8, r.height / 5 - 4, null, 0, r.width - 8),
                    b.getRGB(r.x + 4, bottom, r.width - 8, r.height / 5 - 4, null, 0, r.width - 8)),
                    "The lower edge should already expose the actual face");
            pull(first, 0, .45, false);
            assertFalse(java.util.Arrays.equals(a.getRGB(r.x, r.y, r.width, r.height, null, 0, r.width),
                    canvasImage(first).getRGB(r.x, r.y, r.width, r.height, null, 0, r.width)));
            assertEquals(0, state.count(), "A partial pull must not permanently open the card");
        });
    }

    @Test void faceDownTablePaintingDoesNotDependOnThePrivateCardValues() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            HandRevealState state = new HandRevealState();
            GameTablePanel table = new GameTablePanel(state);
            table.room(new RoomSnapshot(1, "1", "Bàn", "PLAYING", 1, 2, 8, BigDecimal.TEN,
                    List.of(new RoomSnapshot.PlayerSlot(1, "One", 1, false, "PLAYING")), 0), 1);
            table.mine(mine("one", 1));
            int[] before = handPixels(table);
            table.mine(new PlayerStateSnapshot("one", List.of(new Card(Rank.TWO, Suit.RO),
                    new Card(Rank.THREE, Suit.CO), new Card(Rank.FOUR, Suit.BICH)), BigDecimal.TEN, BigDecimal.ZERO, false, false, 2));
            assertArrayEquals(before, handPixels(table), "Receiving private card values must not paint their faces");
            state.reveal(0);
            assertFalse(java.util.Arrays.equals(before, handPixels(table)));
            table.clear();
        });
    }

    @Test void chipsKeepTransparentPaddingBelowTheirCompleteRoundedBase() {
        for (int scale : new int[]{1, 2}) {
            BufferedImage image = new BufferedImage(96 * scale, 72 * scale, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            try { g.scale(scale, scale); GameIcons.chips(g, 0, 0, 96, 72); }
            finally { g.dispose(); }
            int bottom = -1;
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
                if ((image.getRGB(x, y) >>> 24) > 0) bottom = y;
            assertTrue(bottom >= 60 * scale, "The cylinder base should be present");
            assertTrue(bottom < image.getHeight() - 2 * scale, "The drawing must not be cut off at its bottom edge");
        }
    }

    @Test void anUpdateWithinTheSameTurnCannotRestartItsCountdown() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GameTablePanel table = new GameTablePanel();
            table.state(new GameStateSnapshot("one", "BETTING", 1L, List.of(1L, 2L), 0, 1));
            try {
                var field = GameTablePanel.class.getDeclaredField("turnAt"); field.setAccessible(true);
                field.setLong(table, System.nanoTime() - 10_000_000_000L);
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            table.state(new GameStateSnapshot("one", "BETTING", 1L, List.of(1L, 2L), 0, 2));
            assertTrue(table.remainingTurnSeconds() <= 20, "A resync must not promise an extra 30 seconds");
            table.state(new GameStateSnapshot("one", "BETTING", 2L, List.of(1L, 2L), 0, 3));
            assertTrue(table.remainingTurnSeconds() >= 29);
            table.clear();
        });
    }

    private static HandRevealState revealState(GamePanel panel) {
        try {
            var field = GamePanel.class.getDeclaredField("reveals"); field.setAccessible(true);
            return (HandRevealState) field.get(panel);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static PlayerStateSnapshot mine(String round, long version) {
        return new PlayerStateSnapshot(round, CARDS, new BigDecimal("1000"), BigDecimal.TEN, false, false, version);
    }
    private static CardSqueezePanel panel(HandRevealState state, Runnable close) {
        var panel = new CardSqueezePanel(CARDS, state, () -> {}, close);
        panel.setSize(684, 510); UiLayoutTest.layout(panel); return panel;
    }
    private static void drag(CardSqueezePanel panel, int index, double distance) {
        pull(panel, index, distance, true);
    }
    private static JComponent canvas(CardSqueezePanel panel) {
        return UiLayoutTest.components(panel, JComponent.class).stream()
                .filter(c -> "card-squeeze-canvas".equals(c.getName())).findFirst().orElseThrow();
    }
    private static BufferedImage canvasImage(CardSqueezePanel panel) {
        JComponent canvas = canvas(panel);
        BufferedImage image = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try { canvas.paint(g); } finally { g.dispose(); }
        return image;
    }
    private static void pull(CardSqueezePanel panel, int index, double distance, boolean release) {
        JComponent canvas = canvas(panel);
        Rectangle r = panel.cardBounds(index);
        int x = r.x + r.width / 2, y = r.y + r.height - 10, end = y - (int) (r.height * distance);
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 1, MouseEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 2, MouseEvent.BUTTON1_DOWN_MASK, x, end, 0, false, MouseEvent.NOBUTTON));
        if (release) canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 3, 0, x, end, 1, false, MouseEvent.BUTTON1));
    }
    private static int[] handPixels(GameTablePanel table) {
        table.setSize(1100, 680);
        BufferedImage image = new BufferedImage(1100, 680, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try { table.paint(g); } finally { g.dispose(); }
        return image.getRGB(400, 410, 300, 140, null, 0, 300);
    }
}
