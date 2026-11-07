package com.game3cay.shared.model;

public enum Suit {
    CHUON(2, "Chuồn", "♣"),
    BICH(1, "Bích",  "♠"),
    CO  (3, "Cơ",    "♥"),
    RO  (4, "Rô",    "♦");

    private final int compareValue;
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
