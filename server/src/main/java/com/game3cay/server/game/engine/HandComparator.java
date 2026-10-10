package com.game3cay.server.game.engine;

import com.game3cay.shared.model.Card;
import com.game3cay.shared.model.Hand;

import java.util.Comparator;
import java.util.List;

/**
 * So sánh 2 tay bài Ba Cây theo đúng Game_Rules.md Mục 3.
 *
 * <p>Thứ tự so sánh:
 * <ol>
 *   <li>Loại bài: SAP(3) &gt; LIENG(2) &gt; BO_DOI(1) &gt; DIEM(0)</li>
 *   <li>Điểm trong cùng loại: cao hơn thắng</li>
 *   <li>So chất tiebreak: sắp xếp 3 lá giảm dần theo rank, so lần lượt chất lá cao nhất → thứ 2 → thứ 3.
 *       Rô(4) &gt; Cơ(3) &gt; Bích(2) &gt; Chuồn(1)</li>
 * </ol>
 *
 * <p>Vì bộ bài 52 lá duy nhất nên bước so chất <b>luôn phân định được người thắng</b>.
 */
public class HandComparator implements Comparator<Hand> {

    @Override
    public int compare(Hand h1, Hand h2) {
        // Bước 1: So loại bài
        int typeCmp = Integer.compare(h1.type().getStrength(), h2.type().getStrength());
        if (typeCmp != 0) return typeCmp;

        // Bước 2: So điểm trong cùng loại
        int scoreCmp = Integer.compare(h1.score(), h2.score());
        if (scoreCmp != 0) return scoreCmp;

        // Bước 3: So chất (tiebreak)
        return compareBySuit(h1.cards(), h2.cards());
    }

    /**
     * Sắp xếp 3 lá của mỗi người giảm dần theo Rank (sử dụng Card.compareTo),
     * sau đó so chất lần lượt từ lá mạnh nhất.
     */
    private int compareBySuit(List<Card> c1, List<Card> c2) {
        List<Card> sorted1 = c1.stream()
            .sorted(Comparator.reverseOrder())
            .toList();
        List<Card> sorted2 = c2.stream()
            .sorted(Comparator.reverseOrder())
            .toList();

        for (int i = 0; i < 3; i++) {
            int cmp = Integer.compare(
                sorted1.get(i).suit().getCompareValue(),
                sorted2.get(i).suit().getCompareValue()
            );
            if (cmp != 0) return cmp;
        }
        // Không bao giờ xảy ra với bộ bài 52 lá duy nhất
        return 0;
    }
}
