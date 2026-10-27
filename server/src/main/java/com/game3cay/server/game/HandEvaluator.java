package com.game3cay.server.game;

import com.game3cay.shared.model.Card;
import com.game3cay.shared.model.Hand;
import com.game3cay.shared.model.HandType;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public final class HandEvaluator implements Comparator<Hand> {
    private static final Comparator<Card> CARD_COMPARATOR_ORDER =
            Comparator.comparingInt((Card c) -> c.suit().compareValue())
                    .thenComparingInt(c -> c.rank().rankValue())
                    .reversed();

    public Hand evaluate(List<Card> cards) {
        if (cards == null || cards.size() != 3 || new HashSet<>(cards).size() != 3) {
            throw new IllegalArgumentException("Tay bài Ba Cây phải gồm đúng 3 lá bài khác nhau");
        }

        int[] sortedRanks = cards.stream()
                .mapToInt(c -> c.rank().rankValue())
                .sorted()
                .toArray();

        HandType type;
        if (sortedRanks[0] == sortedRanks[2]) {
            type = HandType.SAP;
        } else if (getStraightHighRank(sortedRanks) > 0) {
            type = HandType.LIENG;
        } else if (cards.stream().allMatch(c -> c.rank().isFaceCard())) {
            type = HandType.BO_DOI;
        } else {
            type = HandType.DIEM;
        }

        int score = cards.stream().mapToInt(Card::pointValue).sum() % 10;
        return new Hand(cards, type, score);
    }

    private int getStraightHighRank(int[] sortedRanks) {
        if (sortedRanks[0] == 2 && sortedRanks[1] == 3 && sortedRanks[2] == 14) {
            return 3;
        }
        if (sortedRanks[1] == sortedRanks[0] + 1 && sortedRanks[2] == sortedRanks[1] + 1) {
            return sortedRanks[2];
        }
        return 0;
    }

    private int getHandTypeValue(Hand hand) {
        int[] sortedRanks = hand.cards().stream()
                .mapToInt(c -> c.rank().rankValue())
                .sorted()
                .toArray();

        return switch (hand.handType()) {
            case SAP -> sortedRanks[0];
            case LIENG -> getStraightHighRank(sortedRanks);
            case DIEM -> hand.score();
            case BO_DOI -> 0;
        };
    }

    @Override
    public int compare(Hand handA, Hand handB) {
        Objects.requireNonNull(handA, "handA không được null");
        Objects.requireNonNull(handB, "handB không được null");

        int comparison = Integer.compare(handA.handType().strength(), handB.handType().strength());
        if (comparison != 0) {
            return comparison;
        }

        comparison = Integer.compare(getHandTypeValue(handA), getHandTypeValue(handB));
        if (comparison != 0) {
            return comparison;
        }

        List<Card> sortedCardsA = handA.cards().stream().sorted(CARD_COMPARATOR_ORDER).toList();
        List<Card> sortedCardsB = handB.cards().stream().sorted(CARD_COMPARATOR_ORDER).toList();

        for (int i = 0; i < 3; i++) {
            Card cardA = sortedCardsA.get(i);
            Card cardB = sortedCardsB.get(i);

            comparison = Integer.compare(cardA.suit().compareValue(), cardB.suit().compareValue());
            if (comparison == 0) {
                comparison = Integer.compare(cardA.rank().rankValue(), cardB.rank().rankValue());
            }
            if (comparison != 0) {
                return comparison;
            }
        }

        return 0;
    }
}
