package com.game3cay.server.game;

import com.game3cay.server.game.engine.SidePotCalculator;
import com.game3cay.server.game.model.PlayerState;
import com.game3cay.shared.model.SidePot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test cho {@link SidePotCalculator} — G04 Bước 3.
 */
@DisplayName("SidePotCalculator Tests (G04 - Bước 2-3)")
class SidePotCalculatorTest {

    // ─── Helper ──────────────────────────────────────────────────────────────

    /** Tạo PlayerState chưa fold, chưa all-in với currentBet = betAmount. */
    private PlayerState makePlayer(long userId, BigDecimal betAmount) throws Exception {
        PlayerState p = new PlayerState(userId, (int) userId, betAmount.add(BigDecimal.valueOf(1000)));
        p.deductBet(betAmount);
        return p;
    }

    /** Tạo PlayerState ALL_IN (balance vừa đủ = betAmount → deductBet all-in). */
    private PlayerState makeAllInPlayer(long userId, BigDecimal betAmount) throws Exception {
        // balance = betAmount → deductBet sẽ all-in
        PlayerState p = new PlayerState(userId, (int) userId, betAmount);
        p.deductBet(betAmount);
        assertTrue(p.isAllIn(), "Phải là ALL_IN khi bet = balance");
        return p;
    }

    /** Tạo PlayerState đã fold với currentBet = betAmount. */
    private PlayerState makeFoldedPlayer(long userId, BigDecimal betAmount) throws Exception {
        PlayerState p = makePlayer(userId, betAmount);
        p.setFolded(true);
        return p;
    }

    // ─── Test cases ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Không có all-in → trả về danh sách rỗng")
    void noAllIn_returnsEmptyList() throws Exception {
        // 3 người CALL đủ mức 100, không ai all-in
        PlayerState a = makePlayer(1L, BigDecimal.valueOf(100));
        PlayerState b = makePlayer(2L, BigDecimal.valueOf(100));
        PlayerState c = makePlayer(3L, BigDecimal.valueOf(100));

        List<SidePot> pots = SidePotCalculator.calculate(List.of(a, b, c));

        assertTrue(pots.isEmpty(), "Không có all-in → danh sách pot phải rỗng");
    }

    @Test
    @DisplayName("1 người all-in ở mức thấp → 2 pot")
    void oneAllIn_twoLevels() throws Exception {
        // A all-in 50, B cược 100, C cược 100
        // Pot 1 (mức 50): 50×3 = 150, eligible=[A,B,C]
        // Pot 2 (mức 50~100): 50×2 = 100, eligible=[B,C]
        PlayerState a = makeAllInPlayer(1L, BigDecimal.valueOf(50));
        PlayerState b = makePlayer(2L, BigDecimal.valueOf(100));
        PlayerState c = makePlayer(3L, BigDecimal.valueOf(100));

        List<SidePot> pots = SidePotCalculator.calculate(List.of(a, b, c));

        assertEquals(2, pots.size(), "Phải có đúng 2 pot");

        SidePot pot1 = pots.get(0);
        assertEquals(0, BigDecimal.valueOf(150).compareTo(pot1.amount()),
                "Pot 1 phải là 150");
        assertEquals(3, pot1.eligiblePlayerIds().size(), "Pot 1: A, B, C đều eligible");
        assertTrue(pot1.eligiblePlayerIds().containsAll(List.of(1L, 2L, 3L)));

        SidePot pot2 = pots.get(1);
        assertEquals(0, BigDecimal.valueOf(100).compareTo(pot2.amount()),
                "Pot 2 phải là 100");
        assertEquals(2, pot2.eligiblePlayerIds().size(), "Pot 2: chỉ B, C eligible");
        assertTrue(pot2.eligiblePlayerIds().containsAll(List.of(2L, 3L)));
        assertFalse(pot2.eligiblePlayerIds().contains(1L), "A không được tranh pot 2");
    }

    @Test
    @DisplayName("3 người all-in ở mức khác nhau → 3 pot")
    void threeAllIn_differentLevels() throws Exception {
        // A all-in 30, B all-in 60, C cược 100
        // Pot 1 (0~30): 30×3 = 90, eligible=[A,B,C]
        // Pot 2 (30~60): 30×2 = 60, eligible=[B,C]
        // Pot 3 (60~100): 40×1 = 40, eligible=[C]
        PlayerState a = makeAllInPlayer(1L, BigDecimal.valueOf(30));
        PlayerState b = makeAllInPlayer(2L, BigDecimal.valueOf(60));
        PlayerState c = makePlayer(3L, BigDecimal.valueOf(100));

        List<SidePot> pots = SidePotCalculator.calculate(List.of(a, b, c));

        assertEquals(3, pots.size(), "Phải có đúng 3 pot");

        // Pot 1: 90
        assertEquals(0, BigDecimal.valueOf(90).compareTo(pots.get(0).amount()),
                "Pot 1 phải là 90");
        assertEquals(3, pots.get(0).eligiblePlayerIds().size());

        // Pot 2: 60
        assertEquals(0, BigDecimal.valueOf(60).compareTo(pots.get(1).amount()),
                "Pot 2 phải là 60");
        assertEquals(2, pots.get(1).eligiblePlayerIds().size());
        assertTrue(pots.get(1).eligiblePlayerIds().containsAll(List.of(2L, 3L)));

        // Pot 3: 40
        assertEquals(0, BigDecimal.valueOf(40).compareTo(pots.get(2).amount()),
                "Pot 3 phải là 40");
        assertEquals(1, pots.get(2).eligiblePlayerIds().size());
        assertTrue(pots.get(2).eligiblePlayerIds().contains(3L));
    }

    @Test
    @DisplayName("Người fold đóng góp vào pot nhưng không eligible")
    void foldedPlayer_notEligible() throws Exception {
        // A all-in 50, B cược 100, D fold với 30
        // Pot 1 (0~30): 30×2 + 30 (D fold) = 90, eligible=[A,B]
        // Pot 2 (30~50): 20×2 = 40, eligible=[A,B]
        // Pot 3 (50~100): 50×1 = 50, eligible=[B]
        PlayerState a = makeAllInPlayer(1L, BigDecimal.valueOf(50));
        PlayerState b = makePlayer(2L, BigDecimal.valueOf(100));
        PlayerState d = makeFoldedPlayer(4L, BigDecimal.valueOf(30));

        List<SidePot> pots = SidePotCalculator.calculate(List.of(a, b, d));

        // D không eligible trong bất kỳ pot nào
        for (SidePot pot : pots) {
            assertFalse(pot.eligiblePlayerIds().contains(4L),
                    "D đã fold không được tranh bất kỳ pot nào");
        }

        // Tổng tiền các pot = tổng tiền đã cược
        BigDecimal totalBet = BigDecimal.valueOf(50 + 100 + 30); // = 180
        BigDecimal totalPots = pots.stream()
                .map(SidePot::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, totalBet.compareTo(totalPots),
                "Tổng các pot phải = tổng tiền đã cược (180)");
    }
}
