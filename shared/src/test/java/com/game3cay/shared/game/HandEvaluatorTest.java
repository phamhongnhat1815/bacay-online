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
    void onlyDiamondAceIsHighWhenSortingCardsWithinEachSuit() {
        for (Suit suit : Suit.values()) {
            Hand aceHand = evaluator.evaluate(List.of(new Card(Rank.ACE, suit),
                    new Card(Rank.TWO, suit), new Card(Rank.THREE, suit)));
            Hand eightHand = evaluator.evaluate(List.of(new Card(Rank.TWO, suit),
                    new Card(Rank.SIX, suit), new Card(Rank.EIGHT, suit)));
            assertEquals(6, aceHand.score());
            assertEquals(aceHand.score(), eightHand.score());
            int expected = suit == Suit.RO ? 1 : -1;
            assertEquals(expected, Integer.signum(evaluator.compare(aceHand, eightHand)), suit.name());
            assertEquals(-expected, Integer.signum(evaluator.compare(eightHand, aceHand)), suit.name());
        }
    }

    @Test
    void heartAndClubAcesLoseToTwoOfTheSameSuit() {
        for (Suit suit : List.of(Suit.CO, Suit.CHUON)) {
            Hand aceHand = evaluator.evaluate(List.of(new Card(Rank.ACE, suit),
                    new Card(Rank.NINE, Suit.BICH), new Card(Rank.SEVEN, Suit.BICH)));
            Hand twoHand = evaluator.evaluate(List.of(new Card(Rank.TWO, suit),
                    new Card(Rank.EIGHT, Suit.BICH), new Card(Rank.SEVEN, Suit.BICH)));
            assertEquals(aceHand.score(), twoHand.score());
            assertTrue(evaluator.compare(aceHand, twoHand) < 0, suit.name());
            assertTrue(evaluator.compare(twoHand, aceHand) > 0, suit.name());
        }
    }

    @Test
    void lowAceRuleAlsoAppliesAfterMatchingHigherCards() {
        Card common = new Card(Rank.NINE, Suit.RO);
        Hand aceHand = evaluator.evaluate(List.of(common, new Card(Rank.ACE, Suit.CO),
                new Card(Rank.EIGHT, Suit.BICH)));
        Hand twoHand = evaluator.evaluate(List.of(common, new Card(Rank.TWO, Suit.CO),
                new Card(Rank.SEVEN, Suit.BICH)));
        assertEquals(aceHand.score(), twoHand.score());
        assertTrue(evaluator.compare(aceHand, twoHand) < 0);
        assertTrue(evaluator.compare(twoHand, aceHand) > 0);
    }

    @Test
    void threeAcesWithoutDiamondStillBeatThreeNines() {
        Hand aces = evaluator.evaluate(List.of(new Card(Rank.ACE, Suit.CO),
                new Card(Rank.ACE, Suit.CHUON), new Card(Rank.ACE, Suit.BICH)));
        Hand nines = evaluator.evaluate(List.of(new Card(Rank.NINE, Suit.RO),
                new Card(Rank.NINE, Suit.CO), new Card(Rank.NINE, Suit.CHUON)));
        assertEquals(HandType.SAP, aces.handType());
        assertTrue(evaluator.compare(aces, nines) > 0);
    }

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
                if (rank != Rank.TEN && !rank.isFaceCard()) {
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
