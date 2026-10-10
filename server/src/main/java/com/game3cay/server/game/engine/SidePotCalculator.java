package com.game3cay.server.game.engine;

import com.game3cay.server.game.model.PlayerState;
import com.game3cay.shared.model.SidePot;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Tính danh sách Side Pot khi có người chơi ALL_IN.
 *
 * <p><b>Thuật toán:</b>
 * <ol>
 *   <li>Lấy danh sách người chưa fold, sắp xếp theo {@code currentBet} tăng dần.</li>
 *   <li>Với mỗi "mốc" tạo bởi người all-in, cộng đóng góp từ tất cả người có
 *       {@code currentBet >= prevLevel}.</li>
 *   <li>Chỉ người chưa fold mới được tranh pot ({@code eligiblePlayerIds}).</li>
 * </ol>
 *
 * <p>Ví dụ: A all-in 50, B cược 100, C cược 100:
 * <ul>
 *   <li>Pot 1: 150 (= 50×3), eligible = [A,B,C]</li>
 *   <li>Pot 2: 100 (= 50×2), eligible = [B,C]</li>
 * </ul>
 */
public class SidePotCalculator {

    /**
     * Tính danh sách pot phụ từ trạng thái người chơi hiện tại.
     *
     * @param players tất cả người chơi trong ván (kể cả đã fold)
     * @return danh sách {@link SidePot} theo thứ tự mức cược tăng dần;
     *         trả về danh sách rỗng nếu không có ai all-in
     */
    public static List<SidePot> calculate(List<PlayerState> players) {
        // Chỉ xét người chưa fold để tìm mốc all-in
        List<PlayerState> active = players.stream()
                .filter(p -> !p.isFolded())
                .sorted(Comparator.comparing(PlayerState::getCurrentBet))
                .toList();

        // Không có ai all-in → không cần chia pot phụ
        boolean hasAllIn = active.stream().anyMatch(PlayerState::isAllIn);
        if (!hasAllIn) return List.of();

        List<SidePot> pots = new ArrayList<>();
        BigDecimal prevLevel = BigDecimal.ZERO;

        for (PlayerState allInCandidate : active) {
            BigDecimal level = allInCandidate.getCurrentBet();
            if (level.compareTo(prevLevel) <= 0) continue; // Đã xử lý mốc này

            BigDecimal potAmount = BigDecimal.ZERO;
            List<Long> eligible = new ArrayList<>();

            // Duyệt TẤT CẢ người (kể cả fold) để cộng đóng góp vào pot
            for (PlayerState p : players) {
                BigDecimal contribution = p.getCurrentBet().subtract(prevLevel);
                if (contribution.signum() <= 0) continue;

                // Mỗi người đóng góp tối đa (level - prevLevel) cho pot này
                BigDecimal actualContrib = contribution.min(level.subtract(prevLevel));
                potAmount = potAmount.add(actualContrib);

                // Chỉ người chưa fold mới đủ điều kiện tranh pot
                if (!p.isFolded()) {
                    eligible.add(p.getUserId());
                }
            }

            if (potAmount.signum() > 0) {
                pots.add(new SidePot(potAmount, List.copyOf(eligible)));
            }

            prevLevel = level;
        }

        // Phần dư trên prevLevel → main pot thuộc tất cả người chưa fold còn lại
        BigDecimal remainderAmount = BigDecimal.ZERO;
        List<Long> remainderEligible = new ArrayList<>();

        for (PlayerState p : players) {
            BigDecimal contribution = p.getCurrentBet().subtract(prevLevel);
            if (contribution.signum() > 0) {
                remainderAmount = remainderAmount.add(contribution);
                if (!p.isFolded()) {
                    remainderEligible.add(p.getUserId());
                }
            }
        }

        if (remainderAmount.signum() > 0 && !remainderEligible.isEmpty()) {
            pots.add(new SidePot(remainderAmount, List.copyOf(remainderEligible)));
        }

        return pots;
    }
}
