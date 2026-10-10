package com.game3cay.server.game;

import com.game3cay.server.game.engine.HandComparator;
import com.game3cay.server.game.engine.HandEvaluator;
import com.game3cay.shared.model.Card;
import com.game3cay.shared.model.Hand;
import com.game3cay.shared.model.Rank;
import com.game3cay.shared.model.Suit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HandComparator Tests (G03 - Bước 4-5)")
class HandComparatorTest {

    private static final HandComparator CMP = new HandComparator();

    private static Card c(Rank r, Suit s) { return new Card(r, s); }

    private static Hand eval(Card... cards) {
        return HandEvaluator.evaluate(List.of(cards));
    }

    // ─── So sánh khác loại bài ─────────────────────────────────────────────

    @Test
    @DisplayName("SAP thắng LIENG")
    void sap_beats_lieng() {
        Hand sap  = eval(c(Rank.TWO, Suit.BICH), c(Rank.TWO, Suit.CO), c(Rank.TWO, Suit.CHUON));
        Hand lieng = eval(c(Rank.QUEEN, Suit.BICH), c(Rank.KING, Suit.CO), c(Rank.ACE, Suit.RO));
        assertTrue(CMP.compare(sap, lieng) > 0, "SAP phải thắng LIENG");
        assertTrue(CMP.compare(lieng, sap) < 0, "LIENG phải thua SAP");
    }

    @Test
    @DisplayName("LIENG thắng BO_DOI")
    void lieng_beats_bodoi() {
        Hand lieng = eval(c(Rank.ACE, Suit.BICH), c(Rank.TWO, Suit.CO), c(Rank.THREE, Suit.RO));
        Hand bodoi = eval(c(Rank.JACK, Suit.BICH), c(Rank.JACK, Suit.CO), c(Rank.QUEEN, Suit.RO));
        assertTrue(CMP.compare(lieng, bodoi) > 0, "LIENG phải thắng BO_DOI");
    }

    @Test
    @DisplayName("BO_DOI thắng DIEM 9 điểm")
    void bodoi_beats_diem() {
        // J-J-Q là BO_DOI (score = 12)
        Hand bodoi = eval(c(Rank.JACK, Suit.BICH), c(Rank.JACK, Suit.CO), c(Rank.QUEEN, Suit.RO));
        // 4+6+9=19 → 9 điểm DIEM (không phải SAP, không phải LIENG, không phải BO_DOI)
        Hand diem  = eval(c(Rank.FOUR, Suit.BICH), c(Rank.SIX, Suit.CO), c(Rank.NINE, Suit.RO));
        assertTrue(CMP.compare(bodoi, diem) > 0, "BO_DOI phải thắng DIEM 9 điểm");
    }

    @Test
    @DisplayName("SAP thắng BO_DOI")
    void sap_beats_bodoi() {
        Hand sap   = eval(c(Rank.TWO, Suit.BICH), c(Rank.TWO, Suit.CO), c(Rank.TWO, Suit.CHUON));
        Hand bodoi = eval(c(Rank.JACK, Suit.BICH), c(Rank.JACK, Suit.CO), c(Rank.QUEEN, Suit.RO));
        assertTrue(CMP.compare(sap, bodoi) > 0);
    }

    @Test
    @DisplayName("SAP thắng DIEM dù điểm 9")
    void sap_beats_diem9() {
        Hand sap  = eval(c(Rank.TWO, Suit.BICH), c(Rank.TWO, Suit.CO), c(Rank.TWO, Suit.CHUON));
        Hand diem = eval(c(Rank.NINE, Suit.BICH), c(Rank.FOUR, Suit.CO), c(Rank.SIX, Suit.RO)); // 9+4+6=19→9
        assertTrue(CMP.compare(sap, diem) > 0);
    }

    // ─── So sánh cùng loại SAP ─────────────────────────────────────────────

    @Test
    @DisplayName("A-A-A thắng K-K-K (cùng SAP, score cao hơn)")
    void sameSap_higherRank_wins() {
        Hand sapA = eval(c(Rank.ACE, Suit.BICH), c(Rank.ACE, Suit.CO), c(Rank.ACE, Suit.CHUON));
        Hand sapK = eval(c(Rank.KING, Suit.BICH), c(Rank.KING, Suit.CO), c(Rank.KING, Suit.RO));
        assertTrue(CMP.compare(sapA, sapK) > 0, "A-A-A phải thắng K-K-K");
    }

    @Test
    @DisplayName("K-K-K thắng Q-Q-Q (cùng SAP)")
    void sameSap_KKK_beats_QQQ() {
        Hand sapK = eval(c(Rank.KING, Suit.BICH), c(Rank.KING, Suit.CO), c(Rank.KING, Suit.CHUON));
        Hand sapQ = eval(c(Rank.QUEEN, Suit.BICH), c(Rank.QUEEN, Suit.CO), c(Rank.QUEEN, Suit.RO));
        assertTrue(CMP.compare(sapK, sapQ) > 0);
    }

    // ─── So sánh cùng loại LIENG ───────────────────────────────────────────

    @Test
    @DisplayName("Q-K-A thắng A-2-3 (cùng LIENG, score 14 > 3)")
    void sameLieng_QKA_beats_A23() {
        Hand liengQKA = eval(c(Rank.QUEEN, Suit.BICH), c(Rank.KING, Suit.CO), c(Rank.ACE, Suit.RO));
        Hand liengA23 = eval(c(Rank.ACE, Suit.BICH), c(Rank.TWO, Suit.CO), c(Rank.THREE, Suit.RO));
        assertTrue(CMP.compare(liengQKA, liengA23) > 0, "Q-K-A phải thắng A-2-3");
    }

    @Test
    @DisplayName("Cùng LIENG 5-6-7, so chất tiebreak: 7♠(Bích) thắng 7♣(Chuồn) — theo ví dụ Game_Rules.md")
    void sameLieng_higherSuit_wins() {
        // Người A: 5♦-6♠-7♣ → sort giảm dần: 7♣, 6♠, 5♦
        Hand handA = eval(c(Rank.FIVE, Suit.RO), c(Rank.SIX, Suit.BICH), c(Rank.SEVEN, Suit.CHUON));
        // Người B: 5♣-6♦-7♠ → sort giảm dần: 7♠, 6♦, 5♣
        Hand handB = eval(c(Rank.FIVE, Suit.CHUON), c(Rank.SIX, Suit.RO), c(Rank.SEVEN, Suit.BICH));
        // So lá cao nhất: 7♣(CHUON=1) vs 7♠(BICH=2) → B thắng
        assertTrue(CMP.compare(handB, handA) > 0, "7♠(Bích) phải thắng 7♣(Chuồn)");
    }

    @Test
    @DisplayName("Cùng LIENG 5-6-7, lá cao bằng nhau, so lá thứ 2: 6♦(Rô=4) thắng 6♣(Chuồn=1)")
    void sameLieng_tiebreakSecondCard() {
        // 7 cùng Bích, so lá thứ 2: 6♦ vs 6♣
        Hand handA = eval(c(Rank.FIVE, Suit.CO), c(Rank.SIX, Suit.RO), c(Rank.SEVEN, Suit.BICH));
        Hand handB = eval(c(Rank.FIVE, Suit.RO), c(Rank.SIX, Suit.CHUON), c(Rank.SEVEN, Suit.BICH));
        // Hmm, cùng lá 7♠ BICH → so lá 2: 6♦(RO=4) vs 6♣(CHUON=1) → A thắng
        assertTrue(CMP.compare(handA, handB) > 0, "6♦(Rô) phải thắng 6♣(Chuồn) khi lá cao bằng nhau");
    }

    // ─── So sánh cùng loại BO_DOI ──────────────────────────────────────────

    @Test
    @DisplayName("J-Q-K thắng J-J-Q (cùng BO_DOI, K=13 > Q=12)")
    void sameBodoi_higherScore_wins() {
        // J-Q-K là LIENG, cần dùng combo không liên tiếp
        // J-K-K (BO_DOI, score=13) vs J-J-Q (BO_DOI, score=12)
        Hand bodoi1 = eval(c(Rank.JACK, Suit.CHUON), c(Rank.KING, Suit.BICH), c(Rank.KING, Suit.CO));
        Hand bodoi2 = eval(c(Rank.JACK, Suit.BICH), c(Rank.JACK, Suit.CO), c(Rank.QUEEN, Suit.RO));
        assertTrue(CMP.compare(bodoi1, bodoi2) > 0, "K-K-J(score=13) phải thắng J-J-Q(score=12)");
    }

    // ─── So sánh cùng loại DIEM ────────────────────────────────────────────

    @Test
    @DisplayName("DIEM 9 thắng DIEM 8")
    void sameDiem_9_beats_8() {
        Hand diem9 = eval(c(Rank.FOUR, Suit.BICH), c(Rank.SIX, Suit.CO), c(Rank.NINE, Suit.RO)); // 4+6+9=19→9
        Hand diem8 = eval(c(Rank.THREE, Suit.BICH), c(Rank.SIX, Suit.CO), c(Rank.NINE, Suit.RO)); // 3+6+9=18→8
        assertTrue(CMP.compare(diem9, diem8) > 0);
    }

    @Test
    @DisplayName("Cùng DIEM 9, so chất tiebreak: Rô > Cơ > Bích > Chuồn")
    void sameDiem_9_suitTiebreak() {
        // Người A: 5♦-5♣-9♣ (Rô lá 5, nhưng sort giảm dần: 9♣, 5♦, 5♣)
        // Người B: 5♦-5♣-9♦ (Rô lá 9, sort: 9♦, 5♦, 5♣)
        // 9♦(Rô=4) > 9♣(Chuồn=1) → B thắng
        Hand handA = eval(c(Rank.FIVE, Suit.RO), c(Rank.FIVE, Suit.BICH), c(Rank.NINE, Suit.CHUON));
        Hand handB = eval(c(Rank.FIVE, Suit.CO), c(Rank.FIVE, Suit.BICH), c(Rank.NINE, Suit.RO));
        // Sort giảm dần cả hai: [9, 5, 5] — lá đầu: 9♣(CHUON=1) vs 9♦(RO=4) → B thắng
        assertTrue(CMP.compare(handB, handA) > 0, "9♦(Rô) phải thắng 9♣(Chuồn)");
    }
}
