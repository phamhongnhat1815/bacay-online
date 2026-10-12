package com.bacay.shared.model;

/**
 * Loại bài trong game 3 Cây.
 * Thứ tự so sánh (mạnh → yếu): SÁP > LIÊNG > BO_DOI > DIEM.
 * Ordinal của enum KHÔNG dùng để so sánh; dùng {@link #strength()} thay thế.
 */
public enum HandType {
    /** Ba lá cùng rank (A-A-A cao nhất). */
    SAP(4, "Sáp"),
    /** Dây liên tiếp (A-2-3 đến Q-K-A cao nhất). */
    LIENG(3, "Liêng"),
    /** Cả 3 lá thuộc {J, Q, K}, không cần thứ tự. */
    BO_DOI(2, "Bộ đôi"),
    /** Tổng điểm lấy hàng đơn vị; 9 cao nhất. */
    DIEM(1, "Điểm");

    private final int strength;
    private final String displayName;

    HandType(int strength, String displayName) {
        this.strength    = strength;
        this.displayName = displayName;
    }

    /** Độ mạnh tuyệt đối để so sánh loại bài (cao hơn = thắng). */
    public int strength()       { return strength; }
    public String displayName() { return displayName; }
}
