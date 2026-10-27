package com.game3cay.shared.model;

public enum Rank {
    TWO  (2,  2,  "2"),
    THREE(3,  3,  "3"),
    FOUR (4,  4,  "4"),
    FIVE (5,  5,  "5"),
    SIX  (6,  6,  "6"),
    SEVEN(7,  7,  "7"),
    EIGHT(8,  8,  "8"),
    NINE (9,  9,  "9"),
    TEN  (10, 0,  "10"),
    JACK (11, 0,  "J"),
    QUEEN(12, 0,  "Q"),
    KING (13, 0,  "K"),
    ACE  (14, 1,  "A");

    private final int rankValue;

    private final int pointValue;
    private final String displayName;

    Rank(int rankValue, int pointValue, String displayName) {
        this.rankValue   = rankValue;
        this.pointValue  = pointValue;
        this.displayName = displayName;
    }

    public int rankValue()    { return rankValue; }
    public int pointValue()   { return pointValue; }
    public String displayName() { return displayName; }

    public boolean isFaceCard() {
        return this == JACK || this == QUEEN || this == KING;
    }
}
