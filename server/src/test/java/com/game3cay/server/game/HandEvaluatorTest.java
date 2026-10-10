package com.game3cay.server.game;

import com.game3cay.server.game.engine.HandEvaluator;
import com.game3cay.shared.model.Card;
import com.game3cay.shared.model.Hand;
import com.game3cay.shared.model.HandType;
import com.game3cay.shared.model.Rank;
import com.game3cay.shared.model.Suit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HandEvaluator Tests (G03 - Bước 2-3)")
class HandEvaluatorTest {

    // ─── Helper ────────────────────────────────────────────────────────────

    private static Card c(Rank r, Suit s) { return new Card(r, s); }

    // ─── SAP ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("K-K-K nhận diện đúng là SAP")
    void detectSap_KKK() {
        List<Card> cards = List.of(c(Rank.KING, Suit.BICH), c(Rank.KING, Suit.CO), c(Rank.KING, Suit.CHUON));
        Hand h = HandEvaluator.evaluate(cards);
        assertEquals(HandType.SAP, h.type());
        assertEquals(13, h.score()); // KING rankValue = 13
    }

    @Test
    @DisplayName("A-A-A là SAP cao nhất (score = 14)")
    void detectSap_AAA_isHighest() {
        List<Card> cards = List.of(c(Rank.ACE, Suit.RO), c(Rank.ACE, Suit.CO), c(Rank.ACE, Suit.BICH));
        Hand h = HandEvaluator.evaluate(cards);
        assertEquals(HandType.SAP, h.type());
        assertEquals(14, h.score()); // ACE rankValue = 14
    }

    @Test
    @DisplayName("2-2-2 là SAP thấp nhất (score = 2)")
    void detectSap_222_isLowest() {
        List<Card> cards = List.of(c(Rank.TWO, Suit.BICH), c(Rank.TWO, Suit.CO), c(Rank.TWO, Suit.RO));
        Hand h = HandEvaluator.evaluate(cards);
        assertEquals(HandType.SAP, h.type());
        assertEquals(2, h.score());
    }

    @Test
    @DisplayName("J-J-J là SAP (không phải BO_DOI vì SAP ưu tiên hơn)")
    void detectSap_JJJ_notBoDoi() {
        List<Card> cards = List.of(c(Rank.JACK, Suit.BICH), c(Rank.JACK, Suit.CO), c(Rank.JACK, Suit.RO));
        Hand h = HandEvaluator.evaluate(cards);
        assertEquals(HandType.SAP, h.type());
    }

    // ─── LIENG ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A-2-3 là LIENG hợp lệ (Liêng thấp nhất, score = 3)")
    void detectLieng_A23_isValid() {
        List<Card> cards = List.of(c(Rank.ACE, Suit.BICH), c(Rank.TWO, Suit.CO), c(Rank.THREE, Suit.RO));
        Hand h = HandEvaluator.evaluate(cards);
        assertEquals(HandType.LIENG, h.type());
        assertEquals(3, h.score()); // A-2-3 đặc biệt → score = 3
    }

    @Test
    @DisplayName("Q-K-A là LIENG hợp lệ (Liêng cao nhất, score = 14)")
    void detectLieng_QKA_isValid() {
        List<Card> cards = List.of(c(Rank.QUEEN, Suit.BICH), c(Rank.KING, Suit.CO), c(Rank.ACE, Suit.RO));
        Hand h = HandEvaluator.evaluate(cards);
        assertEquals(HandType.LIENG, h.type());
        assertEquals(14, h.score()); // Lá cao nhất = ACE = 14
    }

    @Test
    @DisplayName("5-6-7 là LIENG hợp lệ (score = 7)")
    void detectLieng_567_isValid() {
        List<Card> cards = List.of(c(Rank.FIVE, Suit.CHUON), c(Rank.SIX, Suit.BICH), c(Rank.SEVEN, Suit.RO));
        Hand h = HandEvaluator.evaluate(cards);
        assertEquals(HandType.LIENG, h.type());
        assertEquals(7, h.score());
    }

    @Test
    @DisplayName("K-A-2 KHÔNG phải LIENG theo luật Ba Cây")
    void detectLieng_KA2_isInvalid_becomesDiem() {
        // K=13, A=14, 2=2 → sort=[2,13,14] → không liên tiếp, không phải A-2-3 đặc biệt
        List<Card> cards = List.of(c(Rank.KING, Suit.BICH), c(Rank.ACE, Suit.CO), c(Rank.TWO, Suit.RO));
        Hand h = HandEvaluator.evaluate(cards);
        // K(0) + A(1) + 2(2) = 3 điểm
        assertEquals(HandType.DIEM, h.type());
        assertEquals(3, h.score());
    }

    @Test
    @DisplayName("J-Q-K là LIENG hợp lệ (score = 13)")
    void detectLieng_JQK_isValid() {
        List<Card> cards = List.of(c(Rank.JACK, Suit.CHUON), c(Rank.QUEEN, Suit.BICH), c(Rank.KING, Suit.CO));
        Hand h = HandEvaluator.evaluate(cards);
        assertEquals(HandType.LIENG, h.type());
        assertEquals(13, h.score());
    }

    // ─── BO_DOI ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("J-Q-K là BO_DOI — nhưng trường hợp này đã là LIENG (J-Q-K liên tiếp)")
    void detectLieng_JQK_notBoDoi() {
        List<Card> cards = List.of(c(Rank.JACK, Suit.CHUON), c(Rank.QUEEN, Suit.BICH), c(Rank.KING, Suit.RO));
        // J-Q-K là Liêng (liên tiếp), LIENG > BO_DOI
        Hand h = HandEvaluator.evaluate(cards);
        assertEquals(HandType.LIENG, h.type());
    }

    @Test
    @DisplayName("J-J-Q là BO_DOI (score = 12, lá cao nhất là Q)")
    void detectBoDoi_JJQ() {
        List<Card> cards = List.of(c(Rank.JACK, Suit.BICH), c(Rank.JACK, Suit.CO), c(Rank.QUEEN, Suit.RO));
        Hand h = HandEvaluator.evaluate(cards);
        assertEquals(HandType.BO_DOI, h.type());
        assertEquals(12, h.score()); // QUEEN = 12
    }

    @Test
    @DisplayName("Q-K-K là BO_DOI (score = 13)")
    void detectBoDoi_QKK() {
        List<Card> cards = List.of(c(Rank.QUEEN, Suit.CHUON), c(Rank.KING, Suit.BICH), c(Rank.KING, Suit.CO));
        Hand h = HandEvaluator.evaluate(cards);
        assertEquals(HandType.BO_DOI, h.type());
        assertEquals(13, h.score());
    }

    @Test
    @DisplayName("K-K-K là SAP (không phải BO_DOI dù toàn lá hình)")
    void detectBoDoi_notWhenSap_KKK() {
        List<Card> cards = List.of(c(Rank.KING, Suit.BICH), c(Rank.KING, Suit.CO), c(Rank.KING, Suit.RO));
        Hand h = HandEvaluator.evaluate(cards);
        assertEquals(HandType.SAP, h.type()); // SAP ưu tiên hơn BO_DOI
    }

    // ─── DIEM ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("5-5-9 = 5+5+9=19 → 9 điểm")
    void detectDiem_9() {
        List<Card> cards = List.of(c(Rank.FIVE, Suit.BICH), c(Rank.FIVE, Suit.CO), c(Rank.NINE, Suit.RO));
        Hand h = HandEvaluator.evaluate(cards);
        assertEquals(HandType.DIEM, h.type());
        assertEquals(9, h.score());
    }

    @Test
    @DisplayName("10-10-K = 0+0+0 = 0 điểm (không phải LIENG, không phải BO_DOI vì có 10)")
    void detectDiem_0() {
        List<Card> cards = List.of(c(Rank.TEN, Suit.BICH), c(Rank.TEN, Suit.CO), c(Rank.KING, Suit.RO));
        // TEN(0)+TEN(0)+KING(0) = 0 điểm; 10-10-K không phải SAP (khác rank), không LIENG, không BO_DOI
        Hand h = HandEvaluator.evaluate(cards);
        assertEquals(HandType.DIEM, h.type());
        assertEquals(0, h.score());
    }

    @Test
    @DisplayName("A-10-K = 1+0+0 = 1 điểm")
    void detectDiem_1() {
        List<Card> cards = List.of(c(Rank.ACE, Suit.BICH), c(Rank.TEN, Suit.CO), c(Rank.KING, Suit.RO));
        Hand h = HandEvaluator.evaluate(cards);
        assertEquals(HandType.DIEM, h.type());
        assertEquals(1, h.score());
    }

    @Test
    @DisplayName("7-8-4 = 7+8+4=19 → 9 điểm")
    void detectDiem_784_gives9() {
        List<Card> cards = List.of(c(Rank.SEVEN, Suit.BICH), c(Rank.EIGHT, Suit.CO), c(Rank.FOUR, Suit.RO));
        Hand h = HandEvaluator.evaluate(cards);
        assertEquals(HandType.DIEM, h.type());
        assertEquals(9, h.score());
    }

    @Test
    @DisplayName("3-5-K = 3+5+0 = 8 điểm")
    void detectDiem_35K_gives8() {
        List<Card> cards = List.of(c(Rank.THREE, Suit.BICH), c(Rank.FIVE, Suit.CO), c(Rank.KING, Suit.RO));
        Hand h = HandEvaluator.evaluate(cards);
        assertEquals(HandType.DIEM, h.type());
        assertEquals(8, h.score());
    }
}
