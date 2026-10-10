package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * Enum 4 chất bài.
 *
 * <p>Thứ tự mạnh yếu (theo Game_Rules.md Mục 3):
 * <b>Rô (4) &gt; Cơ (3) &gt; Bích (2) &gt; Chuồn (1)</b>.
 * {@code compareValue} càng cao thì chất càng mạnh.
 */
public enum Suit implements Serializable {

    CHUON(1),
    BICH(2),
    CO(3),
    RO(4);

    @Serial
    private static final long serialVersionUID = 1L;

    private final int compareValue;

    Suit(int compareValue) {
        this.compareValue = compareValue;
    }

    /** Giá trị so sánh chất: Rô=4 &gt; Cơ=3 &gt; Bích=2 &gt; Chuồn=1. */
    public int getCompareValue() { return compareValue; }
}
