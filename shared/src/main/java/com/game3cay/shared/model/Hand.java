package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

public final class Hand implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final List<Card> cards;
    private final HandType handType;
    private final int score;

    public Hand(List<Card> cards, HandType handType, int score) {
        if (cards == null || cards.size() != 3) {
            throw new IllegalArgumentException("Hand phải có đúng 3 lá bài");
        }
        this.cards    = List.copyOf(cards);
        this.handType = Objects.requireNonNull(handType);
        this.score    = score;
    }

    public List<Card> cards()    { return cards; }
    public HandType handType()   { return handType; }

    public int score()           { return score; }

    @Override
    public String toString() {
        return cards + " [" + handType.displayName() +
               (handType == HandType.DIEM ? "/" + score : "") + "]";
    }
}
