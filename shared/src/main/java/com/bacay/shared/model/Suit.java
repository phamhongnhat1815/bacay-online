package com.bacay.shared.model;

/**
 * Chất bài theo thứ tự so sánh 3 Cây: RÔ > CƠ > BÍCH > CHUỒN (cao → thấp).
 * Ordinal của enum KHÔNG dùng để so sánh; dùng {@link #compareValue()} thay thế.
 */
public enum Suit {
    CHUON(1, "Chuồn", "♣"),
    BICH(2, "Bích",  "♠"),
    CO  (3, "Cơ",    "♥"),
    RO  (4, "Rô",    "♦");

    private final int compareValue; // Giá trị so sánh (cao hơn = mạnh hơn)
    private final String displayName;
    private final String symbol;

    Suit(int compareValue, String displayName, String symbol) {
        this.compareValue = compareValue;
        this.displayName  = displayName;
        this.symbol       = symbol;
    }

    public int compareValue() { return compareValue; }
    public String displayName() { return displayName; }
    public String symbol()      { return symbol; }
}
