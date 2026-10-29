package com.game3cay.gui;

/** Private viewing choices for one round. Network snapshots never reveal cards. */
final class HandRevealState {
    private int mask;
    private boolean covered;
    private long generation;

    boolean revealed(int index) { return index >= 0 && index < 3 && (mask & (1 << index)) != 0; }
    boolean visible(int index) { return !covered && revealed(index); }
    int count() { return Integer.bitCount(mask); }
    boolean complete() { return mask == 7; }
    boolean covered() { return covered; }
    long generation() { return generation; }
    void reveal(int index) {
        if (index < 0 || index > 2) throw new IllegalArgumentException("Invalid card index");
        mask |= 1 << index;
        covered = false;
    }
    void revealAll() { mask = 7; covered = false; }
    void openOrToggleCover() {
        if (!complete()) revealAll();
        else covered = !covered;
    }
    void reset() { mask = 0; covered = false; generation++; }
}
