package com.game3cay.shared.game;

import com.game3cay.shared.model.Card;
import com.game3cay.shared.model.Hand;
import com.game3cay.shared.model.HandType;
import com.game3cay.shared.model.Rank;
import com.game3cay.shared.model.Suit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HandEvaluatorTest {
    private final HandEvaluator evaluator = new HandEvaluator();

    @Test
    void invalidHandsAreRejectedBeforeEvaluation() {
        Card card = new Card(Rank.ACE, Suit.RO);
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(null));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(List.of(card)));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(List.of(card, card, card)));
        assertThrows(IllegalArgumentException.class,
                () -> evaluator.evaluate(Arrays.asList(card, null, new Card(Rank.TWO, Suit.CO))));
    }

    @Test
    void cardOrderDoesNotChangeStrengthOrMutateCallerList() {
        List<Card> cards = List.of(new Card(Rank.ACE, Suit.RO), new Card(Rank.TWO, Suit.BICH),
                new Card(Rank.SEVEN, Suit.CO));
        Hand expected = evaluator.evaluate(cards);
        for (int first = 0; first < 3; first++) {
            for (int second = 0; second < 3; second++) {
                if (first == second) {
                    continue;
                }
                var permutation = new ArrayList<>(List.of(cards.get(first), cards.get(second),
                        cards.get(3 - first - second)));
                List<Card> before = List.copyOf(permutation);
                Hand hand = evaluator.evaluate(permutation);
                assertEquals(HandType.DIEM, hand.handType());
                assertEquals(10, hand.score());
                assertEquals(0, evaluator.compare(expected, hand));
                assertEquals(before, permutation);
                permutation.clear();
                assertEquals(before, hand.cards());
            }
        }
    }

    @Test
    void comparatorIsAntisymmetricAndKeepsSortedHandsTransitive() {
        List<Card> deck = new ArrayList<>();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                if (!rank.isFaceCard()) {
                    deck.add(new Card(rank, suit));
                }
            }
        }
        Random random = new Random(128);
        List<Hand> hands = new ArrayList<>();
        for (int sample = 0; sample < 100; sample++) {
            Collections.shuffle(deck, random);
            hands.add(evaluator.evaluate(deck.subList(0, 3)));
        }
        hands.sort(evaluator);
        for (int i = 0; i < hands.size(); i++) {
            assertEquals(0, evaluator.compare(hands.get(i), hands.get(i)));
            for (int j = i + 1; j < hands.size(); j++) {
                int forward = Integer.signum(evaluator.compare(hands.get(i), hands.get(j)));
                int backward = Integer.signum(evaluator.compare(hands.get(j), hands.get(i)));
                assertTrue(forward <= 0);
                assertEquals(-forward, backward);
            }
        }
    }
}
