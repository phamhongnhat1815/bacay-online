package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * Enum 13 giá trị quân bài.
 *
 * <ul>
 *   <li>{@code rankValue} — dùng để so sánh độ mạnh bài (2..14, Ace = 14 cao nhất)</li>
 *   <li>{@code pointValue} — dùng để tính điểm Ba Cây (A=1, 2-9=face, 10/J/Q/K=0)</li>
 * </ul>
 */
public enum Rank implements Serializable {

    TWO(2, 2),
    THREE(3, 3),
    FOUR(4, 4),
    FIVE(5, 5),
    SIX(6, 6),
    SEVEN(7, 7),
    EIGHT(8, 8),
    NINE(9, 9),
    TEN(10, 0),
    JACK(11, 0),
    QUEEN(12, 0),
    KING(13, 0),
    ACE(14, 1);

    @Serial
    private static final long serialVersionUID = 1L;

    private final int rankValue;
    private final int pointValue;

    Rank(int rankValue, int pointValue) {
        this.rankValue = rankValue;
        this.pointValue = pointValue;
    }

    /** Giá trị dùng để so sánh độ mạnh (2..14). */
    public int getRankValue() { return rankValue; }

    /** Điểm Ba Cây của lá bài này (A=1, 10/J/Q/K=0, còn lại bằng mặt bài). */
    public int getPointValue() { return pointValue; }
}
