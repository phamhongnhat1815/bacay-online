package com.game3cay.server.game.engine;

import com.game3cay.shared.model.Card;
import com.game3cay.shared.model.Rank;
import com.game3cay.shared.model.Suit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bộ bài 52 lá (13 Rank × 4 Suit).
 *
 * <p>Mỗi ván tạo một {@code Deck} mới, xáo và chia.
 * Engine không tái sử dụng Deck giữa các ván.
 */
public class Deck {

    private final List<Card> cards;

    /** Tạo bộ bài đủ 52 lá (chưa xáo). */
    public Deck() {
        cards = new ArrayList<>(52);
        for (Rank r : Rank.values()) {
            for (Suit s : Suit.values()) {
                cards.add(new Card(r, s));
            }
        }
    }

    /** Xáo bài ngẫu nhiên. */
    public void shuffle() {
        Collections.shuffle(cards);
    }

    /**
     * Rút 1 lá từ đỉnh bộ bài.
     *
     * @throws IllegalStateException nếu bộ bài đã hết lá.
     */
    public Card deal() {
        if (cards.isEmpty()) {
            throw new IllegalStateException("Bộ bài đã hết lá");
        }
        return cards.removeLast();
    }

    /**
     * Rút {@code n} lá liên tiếp.
     *
     * @param n số lá cần rút (phải &gt; 0 và &le; {@link #remaining()}).
     * @throws IllegalStateException nếu không đủ lá.
     */
    public List<Card> deal(int n) {
        if (n > cards.size()) {
            throw new IllegalStateException(
                    "Bộ bài chỉ còn " + cards.size() + " lá, không đủ để rút " + n);
        }
        List<Card> dealt = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            dealt.add(deal());
        }
        return dealt;
    }

    /** Số lá còn lại trong bộ bài. */
    public int remaining() {
        return cards.size();
    }
}
