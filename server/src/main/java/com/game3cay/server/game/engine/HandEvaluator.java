package com.game3cay.server.game.engine;

import com.game3cay.shared.model.Card;
import com.game3cay.shared.model.Hand;
import com.game3cay.shared.model.HandType;
import com.game3cay.shared.model.Rank;

import java.util.List;

/**
 * Xác định loại bài và tính điểm cho 3 lá bài trong Ba Cây.
 *
 * <p>Thứ tự ưu tiên kiểm tra (theo Game_Rules.md Mục 2):
 * <b>SAP → LIENG → BO_DOI → DIEM</b>
 * Mỗi tay bài chỉ thuộc đúng một loại.
 */
public class HandEvaluator {

    private HandEvaluator() {}

    /**
     * Đánh giá bộ 3 lá bài và trả về {@link Hand} với loại bài và điểm.
     *
     * @param cards đúng 3 lá bài
     * @return Hand đã phân loại
     */
    public static Hand evaluate(List<Card> cards) {
        if (cards == null || cards.size() != 3) {
            throw new IllegalArgumentException("Phải cung cấp đúng 3 lá bài, nhận: "
                    + (cards == null ? "null" : cards.size()));
        }
        if (isSap(cards))   return new Hand(cards, HandType.SAP,    calcSapScore(cards));
        if (isLieng(cards)) return new Hand(cards, HandType.LIENG,  calcLiengScore(cards));
        if (isBoDoi(cards)) return new Hand(cards, HandType.BO_DOI, calcBdScore(cards));
        return new Hand(cards, HandType.DIEM, calcDiem(cards));
    }

    // ─── Phân loại ─────────────────────────────────────────────────────────

    /** SAP: 3 lá cùng Rank (ví dụ: K-K-K, A-A-A). */
    static boolean isSap(List<Card> cards) {
        return cards.get(0).rank() == cards.get(1).rank()
            && cards.get(1).rank() == cards.get(2).rank();
    }

    /**
     * LIENG: 3 lá liên tiếp theo thứ tự bài Tiến Lên.
     * <ul>
     *   <li>A-2-3 hợp lệ (Liêng thấp nhất)</li>
     *   <li>Q-K-A hợp lệ (Liêng cao nhất)</li>
     *   <li>K-A-2 KHÔNG hợp lệ</li>
     * </ul>
     */
    static boolean isLieng(List<Card> cards) {
        List<Integer> ranks = cards.stream()
            .map(c -> c.rank().getRankValue())
            .sorted()
            .toList();
        // Trường hợp thông thường: 3 lá tăng dần liên tiếp
        if (ranks.get(2) - ranks.get(1) == 1 && ranks.get(1) - ranks.get(0) == 1) return true;
        // Trường hợp đặc biệt: A-2-3 → sau sort: [2, 3, 14]
        return ranks.equals(List.of(2, 3, 14));
    }

    /**
     * BO_DOI: 3 lá ∈ {J, Q, K} bất kỳ kết hợp.
     * Lưu ý: SAP (J-J-J, Q-Q-Q, K-K-K) đã bị lọc trước ở trên.
     */
    static boolean isBoDoi(List<Card> cards) {
        return cards.stream().allMatch(c ->
            c.rank() == Rank.JACK || c.rank() == Rank.QUEEN || c.rank() == Rank.KING);
    }

    // ─── Tính điểm ─────────────────────────────────────────────────────────

    /**
     * DIEM: Tổng pointValue của 3 lá, lấy mod 10.
     * Ace=1, 2-9=mặt bài, 10/J/Q/K=0.
     */
    static int calcDiem(List<Card> cards) {
        return cards.stream()
            .mapToInt(c -> c.rank().getPointValue())
            .sum() % 10;
    }

    /**
     * Score SAP: rankValue của 1 lá (vì 3 lá giống nhau).
     * A-A-A = 14, K-K-K = 13, ..., 2-2-2 = 2.
     */
    static int calcSapScore(List<Card> cards) {
        return cards.get(0).rank().getRankValue();
    }

    /**
     * Score LIENG: rankValue của "lá đại diện":
     * <ul>
     *   <li>A-2-3 → score = 3 (Liêng thấp nhất)</li>
     *   <li>Các Liêng khác → score = rankValue lá cao nhất trong tổ</li>
     * </ul>
     */
    static int calcLiengScore(List<Card> cards) {
        List<Integer> ranks = cards.stream()
            .map(c -> c.rank().getRankValue())
            .sorted()
            .toList();
        // A-2-3 đặc biệt: [2, 3, 14] → dùng 3 làm score
        if (ranks.equals(List.of(2, 3, 14))) return 3;
        return ranks.get(2); // Lá cao nhất
    }

    /**
     * Score BO_DOI: rankValue của lá cao nhất trong 3 lá (J=11, Q=12, K=13).
     */
    static int calcBdScore(List<Card> cards) {
        return cards.stream()
            .mapToInt(c -> c.rank().getRankValue())
            .max()
            .getAsInt();
    }
}
