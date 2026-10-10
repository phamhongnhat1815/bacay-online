package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * Loại bài trong Ba Cây (thứ tự tăng dần theo độ mạnh).
 *
 * <ul>
 *   <li>{@code DIEM(0)} — Điểm thường (mod 10, 9 &gt; 8 &gt; ... &gt; 1 &gt; 0)</li>
 *   <li>{@code BO_DOI(1)} — 3 lá J/Q/K bất kỳ</li>
 *   <li>{@code LIENG(2)} — 3 lá liên tiếp (A-2-3 đặc biệt; Q-K-A hợp lệ; K-A-2 không hợp lệ)</li>
 *   <li>{@code SAP(3)} — 3 lá cùng rank (bộ sám)</li>
 * </ul>
 *
 * {@code strength} dùng để so sánh: SAP.strength &gt; LIENG.strength &gt; BO_DOI.strength &gt; DIEM.strength.
 */
public enum HandType implements Serializable {

    DIEM(0),
    BO_DOI(1),
    LIENG(2),
    SAP(3);

    @Serial
    private static final long serialVersionUID = 1L;

    private final int strength;

    HandType(int strength) {
        this.strength = strength;
    }

    /** Giá trị so sánh giữa các loại bài: SAP=3 &gt; LIENG=2 &gt; BO_DOI=1 &gt; DIEM=0. */
    public int getStrength() { return strength; }
}
