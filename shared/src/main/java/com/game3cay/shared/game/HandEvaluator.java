package com.game3cay.shared.game;

import com.game3cay.shared.model.Card;
import com.game3cay.shared.model.Hand;
import com.game3cay.shared.model.HandType;
import com.game3cay.shared.model.Rank;
import com.game3cay.shared.model.Suit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Pure rules shared by the authoritative server and UI hand descriptions. */
public class HandEvaluator implements Comparator<Hand> {
    private static final int HAND_SIZE = 3;
    private static final Comparator<Card> CARD_STRENGTH = Comparator
            .comparingInt((Card c) -> c.suit().compareValue())
            .thenComparingInt(HandEvaluator::tieBreakRank);

    private static int tieBreakRank(Card card) {
        // Only the diamond ace is high when breaking ties; other aces are below two.
        return card.rank() == Rank.ACE && card.suit() != Suit.RO ? 1 : card.rank().rankValue();
    }

    public Hand evaluate(List<Card> cards) {
        if (cards == null || cards.size() != HAND_SIZE || cards.stream().anyMatch(Objects::isNull)
                || new HashSet<>(cards).size() != HAND_SIZE) {
            throw new IllegalArgumentException("Tay bài phải gồm đúng 3 lá bài khác nhau");
        }
        boolean sameRank = cards.stream().allMatch(card -> card.rank() == cards.getFirst().rank());
        HandType type = sameRank ? HandType.SAP : HandType.DIEM;
        int score = cards.stream().mapToInt(Card::pointValue).sum() % 10;
        return new Hand(cards, type, score == 0 ? 10 : score);
    }

    private static int value(Hand hand) {
        return switch (hand.handType()) {
            case SAP -> hand.cards().getFirst().rank().rankValue();
            case DIEM -> hand.score();
            case LIENG, BO_DOI -> throw new IllegalArgumentException("Loại bài chỉ dùng cho lịch sử cũ");
        };
    }

    @Override
    public int compare(Hand a, Hand b) {
        Objects.requireNonNull(a);
        Objects.requireNonNull(b);
        int result = Integer.compare(a.handType().strength(), b.handType().strength());
        if (result != 0) {
            return result;
        }
        result = Integer.compare(value(a), value(b));
        if (result != 0) {
            return result;
        }
        List<Card> sortedA = a.cards().stream().sorted(CARD_STRENGTH.reversed()).toList();
        List<Card> sortedB = b.cards().stream().sorted(CARD_STRENGTH.reversed()).toList();
        for (int i = 0; i < HAND_SIZE; i++) {
            result = CARD_STRENGTH.compare(sortedA.get(i), sortedB.get(i));
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }
}
