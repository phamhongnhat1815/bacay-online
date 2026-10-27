package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public final class Card implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Rank rank;
    private final Suit suit;

    public Card(Rank rank, Suit suit) {
        this.rank = Objects.requireNonNull(rank, "rank");
        this.suit = Objects.requireNonNull(suit, "suit");
    }

    public Rank rank() { return rank; }
    public Suit suit() { return suit; }

    public int pointValue() { return rank.pointValue(); }

    @Override
    public String toString() {
        return rank.displayName() + suit.symbol();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Card card)) return false;
        return rank == card.rank && suit == card.suit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(rank, suit);
    }
}
