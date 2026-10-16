package com.bacay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Một lá bài. Bất biến (immutable).
 *
 * <p>Không được gửi qua mạng trừ khi là phần của {@code DealCardPayload}
 * hoặc {@code RoundResult} (sau khi lật bài). Server KHÔNG BAO GIỜ
 * broadcast bài chưa lật qua {@code GAME_UPDATED}.
 */
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

    /** Điểm đóng góp của lá bài vào tổng điểm (J/Q/K/10 = 0, A = 1, còn lại theo mặt số). */
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
