package com.bacay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Tay bài 3 lá của một người chơi, kèm kết quả phân tích loại bài.
 *
 * <p>Được tạo bởi {@code HandEvaluator} ở server-side; chỉ gửi qua mạng
 * trong {@code RoundResult} sau khi ván kết thúc.
 */
public final class Hand implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final List<Card> cards;     // Đúng 3 lá, thứ tự không quan trọng
    private final HandType handType;    // Loại bài đã phân tích
    private final int score;            // Điểm tổng (chỉ có ý nghĩa khi handType == DIEM)

    public Hand(List<Card> cards, HandType handType, int score) {
        if (cards == null || cards.size() != 3) {
            throw new IllegalArgumentException("Hand phải có đúng 3 lá bài");
        }
        this.cards    = List.copyOf(cards); // immutable
        this.handType = Objects.requireNonNull(handType);
        this.score    = score;
    }

    public List<Card> cards()    { return cards; }
    public HandType handType()   { return handType; }
    /** Điểm tổng (hàng đơn vị); chỉ có ý nghĩa khi {@code handType == DIEM}. */
    public int score()           { return score; }

    @Override
    public String toString() {
        return cards + " [" + handType.displayName() +
               (handType == HandType.DIEM ? "/" + score : "") + "]";
    }
}
