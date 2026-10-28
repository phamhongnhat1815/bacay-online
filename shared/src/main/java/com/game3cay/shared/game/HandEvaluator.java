package com.game3cay.shared.game;

import com.game3cay.shared.model.Card;
import com.game3cay.shared.model.Hand;
import com.game3cay.shared.model.HandType;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Pure rules shared by the authoritative server and UI hand descriptions. */
public class HandEvaluator implements Comparator<Hand> {
    private static final Comparator<Card> CARD_ORDER = Comparator
            .comparingInt((Card c) -> c.suit().compareValue())
            .thenComparingInt(c -> c.rank().rankValue()).reversed();

    public Hand evaluate(List<Card> cards) {
        if (cards == null || cards.size() != 3 || cards.stream().anyMatch(Objects::isNull)
                || new HashSet<>(cards).size() != 3) {
            throw new IllegalArgumentException("Tay bài phải gồm đúng 3 lá bài khác nhau");
        }
        int[] ranks = cards.stream().mapToInt(c -> c.rank().rankValue()).sorted().toArray();
        HandType type;
        if (ranks[0] == ranks[2]) type = HandType.SAP;
        else if (straightHigh(ranks) > 0) type = HandType.LIENG;
        else if (cards.stream().allMatch(c -> c.rank().isFaceCard())) type = HandType.BO_DOI;
        else type = HandType.DIEM;
        return new Hand(cards, type, cards.stream().mapToInt(Card::pointValue).sum() % 10);
    }

    private static int straightHigh(int[] ranks) {
        if (ranks[0] == 2 && ranks[1] == 3 && ranks[2] == 14) return 3;
        return ranks[1] == ranks[0] + 1 && ranks[2] == ranks[1] + 1 ? ranks[2] : 0;
    }

    private static int value(Hand hand) {
        int[] ranks = hand.cards().stream().mapToInt(c -> c.rank().rankValue()).sorted().toArray();
        return switch (hand.handType()) {
            case SAP -> ranks[0];
            case LIENG -> straightHigh(ranks);
            case DIEM -> hand.score();
            case BO_DOI -> 0;
        };
    }

    @Override
    public int compare(Hand a, Hand b) {
        Objects.requireNonNull(a);
        Objects.requireNonNull(b);
        int result = Integer.compare(a.handType().strength(), b.handType().strength());
        if (result != 0) return result;
        result = Integer.compare(value(a), value(b));
        if (result != 0) return result;
        if (a.handType() == HandType.LIENG) {
            int high = value(a); // A-2-3 is headed by 3, not A.
            int suitA = a.cards().stream().filter(c -> c.rank().rankValue() == high)
                    .findFirst().orElseThrow().suit().compareValue();
            int suitB = b.cards().stream().filter(c -> c.rank().rankValue() == high)
                    .findFirst().orElseThrow().suit().compareValue();
            return Integer.compare(suitA, suitB);
        }
        List<Card> sortedA = a.cards().stream().sorted(CARD_ORDER).toList();
        List<Card> sortedB = b.cards().stream().sorted(CARD_ORDER).toList();
        for (int i = 0; i < 3; i++) {
            result = -CARD_ORDER.compare(sortedA.get(i), sortedB.get(i));
            if (result != 0) return result;
        }
        return 0;
    }
}
