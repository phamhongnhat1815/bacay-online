package com.game3cay.server.game;

import com.game3cay.shared.model.Card;
import com.game3cay.shared.model.Hand;
import com.game3cay.shared.model.HandType;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Bộ đánh giá và so sánh tay bài 3 lá theo luật Ba Cây của nhóm.
 * <p>
 * <b>1. Thứ bậc loại bài:</b>
 * SÁP &gt; LIÊNG &gt; BỒ ĐỘI &gt; ĐIỂM
 * <ul>
 *   <li><b>Sáp:</b> 3 lá cùng rank (A-A-A cao nhất).</li>
 *   <li><b>Liêng:</b> 3 lá có rank liên tiếp (A-2-3 thấp nhất, Q-K-A cao nhất).</li>
 *   <li><b>Bồ đội:</b> cả 3 lá đều thuộc {J, Q, K} và không phải Sáp hay Liêng.</li>
 *   <li><b>Điểm:</b> Tổng điểm lấy theo hàng đơn vị (A=1; 2-9 theo số; 10/J/Q/K=0; 9 điểm cao nhất).</li>
 * </ul>
 *
 * <b>2. Phân định khi cùng loại bài:</b>
 * <ul>
 *   <li>Cùng Sáp: So rank của bộ ba (A cao nhất).</li>
 *   <li>Cùng Liêng: So lá bài cao nhất trong sảnh (Q-K-A &gt; ... &gt; A-2-3).</li>
 *   <li>Cùng Điểm: So điểm số (9 &gt; 8 &gt; ... &gt; 0).</li>
 *   <li>Nếu vẫn bằng nhau (hoặc cùng Bồ đội): Sắp xếp các lá theo chất (Rô &gt; Cơ &gt; Bích &gt; Chuồn),
 *       trong cùng chất so Rank (A &gt; K &gt; ... &gt; 2), sau đó so sánh lần lượt từng lá bài từ cao xuống thấp.</li>
 * </ul>
 */
public final class HandEvaluator implements Comparator<Hand> {

    private static final Comparator<Card> CARD_COMPARATOR_ORDER =
            Comparator.comparingInt((Card c) -> c.suit().compareValue())
                    .thenComparingInt(c -> c.rank().rankValue())
                    .reversed();

    /**
     * Phân tích và tạo đối tượng {@link Hand} từ danh sách 3 lá bài.
     *
     * @param cards danh sách 3 lá bài khác nhau
     * @return đối tượng Hand đã được phân loại và tính điểm
     * @throws IllegalArgumentException nếu cards không đúng 3 lá bài khác nhau
     */
    public Hand evaluate(List<Card> cards) {
        if (cards == null || cards.size() != 3 || new HashSet<>(cards).size() != 3) {
            throw new IllegalArgumentException("Tay bài Ba Cây phải gồm đúng 3 lá bài khác nhau");
        }

        int[] sortedRanks = cards.stream()
                .mapToInt(c -> c.rank().rankValue())
                .sorted()
                .toArray();

        HandType type;
        if (sortedRanks[0] == sortedRanks[2]) {
            type = HandType.SAP;
        } else if (getStraightHighRank(sortedRanks) > 0) {
            type = HandType.LIENG;
        } else if (cards.stream().allMatch(c -> c.rank().isFaceCard())) {
            type = HandType.BO_DOI;
        } else {
            type = HandType.DIEM;
        }

        int score = cards.stream().mapToInt(Card::pointValue).sum() % 10;
        return new Hand(cards, type, score);
    }

    /**
     * Xác định giá trị rank cao nhất của sảnh (Liêng).
     * <p>
     * - A-2-3: rank tương ứng là 2, 3, 14; tính rank cao nhất là 3 (thấp nhất trong các loại Liêng).
     * - Các sảnh liên tiếp khác: trả về rank cao nhất.
     *
     * @param sortedRanks mảng rank đã sắp xếp tăng dần
     * @return giá trị rank cao nhất của Liêng, hoặc 0 nếu không phải Liêng
     */
    private int getStraightHighRank(int[] sortedRanks) {
        if (sortedRanks[0] == 2 && sortedRanks[1] == 3 && sortedRanks[2] == 14) {
            return 3;
        }
        if (sortedRanks[1] == sortedRanks[0] + 1 && sortedRanks[2] == sortedRanks[1] + 1) {
            return sortedRanks[2];
        }
        return 0;
    }

    private int getHandTypeValue(Hand hand) {
        int[] sortedRanks = hand.cards().stream()
                .mapToInt(c -> c.rank().rankValue())
                .sorted()
                .toArray();

        return switch (hand.handType()) {
            case SAP -> sortedRanks[0];
            case LIENG -> getStraightHighRank(sortedRanks);
            case DIEM -> hand.score();
            case BO_DOI -> 0;
        };
    }

    @Override
    public int compare(Hand handA, Hand handB) {
        Objects.requireNonNull(handA, "handA không được null");
        Objects.requireNonNull(handB, "handB không được null");

        // 1. So sánh độ mạnh tuyệt đối của loại bài (SAP > LIENG > BO_DOI > DIEM)
        int comparison = Integer.compare(handA.handType().strength(), handB.handType().strength());
        if (comparison != 0) {
            return comparison;
        }

        // 2. So sánh giá trị nội bộ trong cùng loại bài
        comparison = Integer.compare(getHandTypeValue(handA), getHandTypeValue(handB));
        if (comparison != 0) {
            return comparison;
        }

        // 3. Phân định hòa bằng chất và rank của từng lá bài:
        // Sắp xếp bài theo chất giảm dần (Rô > Cơ > Bích > Chuồn), trong cùng chất A > K > ... > 2
        List<Card> sortedCardsA = handA.cards().stream().sorted(CARD_COMPARATOR_ORDER).toList();
        List<Card> sortedCardsB = handB.cards().stream().sorted(CARD_COMPARATOR_ORDER).toList();

        for (int i = 0; i < 3; i++) {
            Card cardA = sortedCardsA.get(i);
            Card cardB = sortedCardsB.get(i);

            comparison = Integer.compare(cardA.suit().compareValue(), cardB.suit().compareValue());
            if (comparison == 0) {
                comparison = Integer.compare(cardA.rank().rankValue(), cardB.rank().rankValue());
            }
            if (comparison != 0) {
                return comparison;
            }
        }

        return 0;
    }
}
