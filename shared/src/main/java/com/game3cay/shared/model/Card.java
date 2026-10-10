package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * Một lá bài trong bộ bài 52 lá.
 *
 * <p>Immutable record: {@code Card(Rank rank, Suit suit)}.
 * Implements {@link Comparable} để sắp xếp lá bài theo độ mạnh
 * (so rank trước, rồi so chất khi rank bằng nhau).
 *
 * <p>Phải {@link Serializable} để truyền qua ObjectStream (PacketIO).
 */
public record Card(Rank rank, Suit suit) implements Comparable<Card>, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * So sánh 2 lá bài:
     * <ol>
     *   <li>Rank cao hơn → lá mạnh hơn.</li>
     *   <li>Rank bằng nhau → so chất (Rô &gt; Cơ &gt; Bích &gt; Chuồn).</li>
     * </ol>
     */
    @Override
    public int compareTo(Card other) {
        int rankCmp = Integer.compare(this.rank.getRankValue(), other.rank.getRankValue());
        if (rankCmp != 0) return rankCmp;
        return Integer.compare(this.suit.getCompareValue(), other.suit.getCompareValue());
    }

    @Override
    public String toString() {
        return rank.name() + "_" + suit.name();
    }
}
