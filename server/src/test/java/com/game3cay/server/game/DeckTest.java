package com.game3cay.server.game;

import com.game3cay.server.game.engine.Deck;
import com.game3cay.shared.model.Card;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DeckTest {

    private Deck deck;

    @BeforeEach
    void setUp() {
        deck = new Deck();
    }

    @Test
    void deckHas52Cards() {
        assertEquals(52, deck.remaining());
    }

    @Test
    void deckCardsAreUnique() {
        Set<String> seen = new HashSet<>();
        while (deck.remaining() > 0) {
            Card c = deck.deal();
            assertTrue(seen.add(c.toString()),
                    "Lá bài bị trùng: " + c);
        }
    }

    @Test
    void shuffleDoesNotChangeCount() {
        deck.shuffle();
        assertEquals(52, deck.remaining());
    }

    @Test
    void dealOneCard_reducesRemainingByOne() {
        deck.shuffle();
        deck.deal();
        assertEquals(51, deck.remaining());
    }

    @Test
    void dealNCards_returnsCorrectCount() {
        deck.shuffle();
        List<Card> dealt = deck.deal(3);
        assertEquals(3, dealt.size());
        assertEquals(49, deck.remaining());
    }

    @Test
    void dealNCards_allUnique() {
        deck.shuffle();
        List<Card> hand1 = deck.deal(3);
        List<Card> hand2 = deck.deal(3);

        Set<String> all = new HashSet<>();
        hand1.forEach(c -> all.add(c.toString()));
        hand2.forEach(c -> assertTrue(all.add(c.toString()),
                "Lá bài bị trùng giữa 2 bộ: " + c));
    }

    @Test
    void dealBeyondLimit_throwsIllegalStateException() {
        // Rút hết 52 lá
        deck.deal(52);
        // Lá tiếp theo phải ném exception
        assertThrows(IllegalStateException.class, () -> deck.deal());
    }

    @Test
    void dealNBeyondLimit_throwsIllegalStateException() {
        deck.deal(50);
        // Chỉ còn 2 lá nhưng yêu cầu rút 3
        assertThrows(IllegalStateException.class, () -> deck.deal(3));
    }

    @Test
    void deal8PlayersHands_totalCardsUnique() {
        deck.shuffle();
        Set<String> allDealt = new HashSet<>();
        // 8 người × 3 lá = 24 lá
        for (int i = 0; i < 8; i++) {
            List<Card> hand = deck.deal(3);
            assertEquals(3, hand.size());
            final int playerIndex = i;
            hand.forEach(c -> assertTrue(allDealt.add(c.toString()),
                    "Lá bài bị trùng khi chia cho người " + (playerIndex + 1) + ": " + c));
        }
        assertEquals(24, allDealt.size());
        assertEquals(52 - 24, deck.remaining());
    }

}
