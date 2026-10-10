package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Bộ 3 lá bài đã được đánh giá của một người chơi.
 *
 * <ul>
 *   <li>{@code cards} — 3 lá bài cụ thể</li>
 *   <li>{@code type} — loại bài (SAP / LIENG / BO_DOI / DIEM)</li>
 *   <li>{@code score} — giá trị số trong loại (dùng để so sánh tiebreak).
 *       Ý nghĩa theo {@link HandType}:
 *       <ul>
 *         <li>DIEM: tổng điểm mod 10 (0..9)</li>
 *         <li>BO_DOI: rankValue lá cao nhất trong 3 lá (J=11, Q=12, K=13)</li>
 *         <li>LIENG: rankValue lá "cao nhất theo luật" (A-2-3 → 3, còn lại → lá lớn nhất)</li>
 *         <li>SAP: rankValue của rank chung (A-A-A = 14)</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * Phải {@link Serializable} để đưa vào {@code PlayerResult} gửi qua socket (PacketIO).
 */
public record Hand(List<Card> cards, HandType type, int score) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Tạo Hand với danh sách bài bất biến (defensive copy). */
    public Hand {
        cards = List.copyOf(cards);
    }
}
