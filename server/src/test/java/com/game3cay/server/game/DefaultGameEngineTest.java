package com.game3cay.server.game;

import static org.junit.jupiter.api.Assertions.*;

import com.game3cay.shared.model.*;
import com.game3cay.shared.network.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;

class DefaultGameEngineTest {
  final HandEvaluator eval = new HandEvaluator();

  private Card c(Rank r, Suit s) {
    return new Card(r, s);
  }

  private List<RoundParticipant> players(int count, int balance) {
    var list = new ArrayList<RoundParticipant>();
    for (int i = 1; i <= count; i++)
      list.add(
          new RoundParticipant(i, "p" + i, "Người " + i, BigDecimal.valueOf(balance).setScale(2)));
    return list;
  }

  private DefaultGameEngine engine(int n, int balance) throws Exception {
    var e = new DefaultGameEngine(new Random(42));
    e.initRound("round", 1, players(n, balance), BigDecimal.ONE);
    return e;
  }

  @Test
  void sapBeatsTenAndTenBeatsNine() {
    var sap = eval.evaluate(List.of(c(Rank.TWO, Suit.CO), c(Rank.TWO, Suit.BICH), c(Rank.TWO, Suit.CHUON)));
    var ten = eval.evaluate(List.of(c(Rank.TWO, Suit.RO), c(Rank.THREE, Suit.CO), c(Rank.FIVE, Suit.BICH)));
    var nine = eval.evaluate(List.of(c(Rank.FOUR, Suit.RO), c(Rank.FIVE, Suit.CO), c(Rank.TEN, Suit.BICH)));
    assertEquals(HandType.SAP, sap.handType());
    assertEquals(HandType.DIEM, ten.handType());
    assertEquals(10, ten.score());
    assertEquals(9, nine.score());
    assertTrue(eval.compare(sap, ten) > 0);
    assertTrue(eval.compare(ten, nine) > 0);
    var twenty = eval.evaluate(List.of(c(Rank.TWO, Suit.CO), c(Rank.EIGHT, Suit.RO), c(Rank.TEN, Suit.CO)));
    assertEquals(10, twenty.score());
    var tens = eval.evaluate(List.of(c(Rank.TEN, Suit.RO), c(Rank.TEN, Suit.CO), c(Rank.TEN, Suit.BICH)));
    assertEquals(HandType.SAP, tens.handType());
    assertTrue(eval.compare(tens, ten) > 0);
    var aces = eval.evaluate(List.of(c(Rank.ACE, Suit.RO), c(Rank.ACE, Suit.CO), c(Rank.ACE, Suit.BICH)));
    assertTrue(eval.compare(aces, tens) > 0);
  }

  @Test
  void allPlayableHandsUseOnlySapOrOneToTenPoints() {
    var deck = new ArrayList<Card>();
    for (Suit suit : Suit.values())
      for (Rank rank : Rank.values())
        if (!rank.isFaceCard()) deck.add(c(rank, suit));
    assertEquals(40, deck.size());
    var scores = new HashSet<Integer>();
    int sapCount = 0;
    for (int i = 0; i < deck.size(); i++)
      for (int j = i + 1; j < deck.size(); j++)
        for (int k = j + 1; k < deck.size(); k++) {
          var cards = List.of(deck.get(i), deck.get(j), deck.get(k));
          var hand = eval.evaluate(cards);
          boolean sameRank = cards.stream().map(Card::rank).distinct().count() == 1;
          assertEquals(sameRank ? HandType.SAP : HandType.DIEM, hand.handType());
          int sum = cards.stream().mapToInt(card -> card.rank() == Rank.ACE ? 1 : card.rank().rankValue()).sum();
          assertEquals((sum - 1) % 10 + 1, hand.score());
          if (sameRank) sapCount++;
          else scores.add(hand.score());
        }
    assertEquals(40, sapCount);
    assertEquals(Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), scores);
  }

  @Test
  void suitOrderBreaksEqualPoints() {
    var a =
        eval.evaluate(
            List.of(c(Rank.FOUR, Suit.RO), c(Rank.FIVE, Suit.CO), c(Rank.TEN, Suit.BICH)));
    var b =
        eval.evaluate(
            List.of(c(Rank.FOUR, Suit.CO), c(Rank.FIVE, Suit.BICH), c(Rank.TEN, Suit.CHUON)));
    assertTrue(eval.compare(a, b) > 0);
  }

  @Test
  void equalPointsUseHighestSuitThenThatCardsRankWithAceHighest() {
    var suits = List.of(Suit.RO, Suit.CO, Suit.CHUON, Suit.BICH);
    for (int i = 0; i < suits.size() - 1; i++) {
      var higherSuit = eval.evaluate(List.of(c(Rank.TWO, suits.get(i)), c(Rank.THREE, Suit.BICH), c(Rank.FIVE, Suit.BICH)));
      var lowerSuit = eval.evaluate(List.of(c(Rank.TWO, suits.get(i + 1)), c(Rank.THREE, Suit.BICH), c(Rank.FIVE, Suit.BICH)));
      assertEquals(10, higherSuit.score());
      assertEquals(10, lowerSuit.score());
      assertTrue(eval.compare(higherSuit, lowerSuit) > 0);
      assertTrue(eval.compare(lowerSuit, higherSuit) < 0);
    }
    var ace = eval.evaluate(List.of(c(Rank.ACE, Suit.RO), c(Rank.TWO, Suit.BICH), c(Rank.SEVEN, Suit.BICH)));
    var ten = eval.evaluate(List.of(c(Rank.TEN, Suit.RO), c(Rank.TWO, Suit.CO), c(Rank.EIGHT, Suit.CO)));
    assertEquals(ace.score(), ten.score());
    assertTrue(eval.compare(ace, ten) > 0);
    // Within the highest suit, use its highest card even if it appears last in the input.
    var nine = eval.evaluate(List.of(c(Rank.ACE, Suit.BICH), c(Rank.TEN, Suit.BICH), c(Rank.NINE, Suit.RO)));
    var twoDiamonds = eval.evaluate(List.of(c(Rank.TWO, Suit.RO), c(Rank.TEN, Suit.CO), c(Rank.EIGHT, Suit.RO)));
    assertEquals(nine.score(), twoDiamonds.score());
    assertTrue(eval.compare(nine, twoDiamonds) > 0);
  }

  @Test
  void eightPlayersReceiveUniqueCardsFromTheFortyCardDeck() throws Exception {
    var seen = new HashSet<Card>();
    for (int seed = 0; seed < 100; seed++) {
      var e = new DefaultGameEngine(new Random(seed));
      e.initRound("round", 1, players(8, 100), BigDecimal.ONE);
      var cards = new HashSet<Card>();
      for (long id = 1; id <= 8; id++) cards.addAll(e.getPlayerCards("round", id));
      assertEquals(24, cards.size());
      assertTrue(cards.stream().noneMatch(card -> card.rank().isFaceCard()));
      assertEquals(2L, e.getPublicSnapshot("round").currentTurnUserId());
      seen.addAll(cards);
    }
    assertEquals(40, seen.size());
  }

  @Test
  void wrongTurnAndInvalidRaiseDoNotMutate() throws Exception {
    var e = engine(2, 100);
    var before = e.getPublicSnapshot("round");
    assertEquals(
        ErrorCode.NOT_YOUR_TURN,
        assertThrows(
                GameEngineException.class, () -> e.handleAction("round", 1, BetAction.CALL, null))
            .getErrorCode());
    assertThrows(
        GameEngineException.class,
        () -> e.handleAction("round", 2, BetAction.RAISE, BigDecimal.ONE));
    assertEquals(before, e.getPublicSnapshot("round"));
    assertEquals(
        ErrorCode.STALE_ROUND,
        assertThrows(
                GameEngineException.class, () -> e.handleAction("old", 2, BetAction.CALL, null))
            .getErrorCode());
  }

  @Test
  void everyoneMustActAndLateActionFails() throws Exception {
    var e = engine(3, 100);
    e.handleAction("round", 2, BetAction.CALL, null);
    assertTrue(e.isRoundActive("round"));
    e.handleAction("round", 3, BetAction.CALL, null);
    assertTrue(e.isRoundActive("round"));
    e.handleAction("round", 1, BetAction.CALL, null);
    assertFalse(e.isRoundActive("round"));
    assertThrows(GameEngineException.class, () -> e.handleAction("round", 2, BetAction.CALL, null));
  }

  @Test
  void validRaiseReopensOthersAndSettlesAfterAllCalls() throws Exception {
    var e = engine(3, 100);
    e.handleAction("round", 2, BetAction.RAISE, new BigDecimal("10.00"));
    assertEquals(3L, e.getPublicSnapshot("round").currentTurnUserId());
    e.handleAction("round", 3, BetAction.CALL, null);
    e.handleAction("round", 1, BetAction.RAISE, new BigDecimal("20.00"));
    assertEquals(2L, e.getPublicSnapshot("round").currentTurnUserId());
    e.handleAction("round", 2, BetAction.CALL, null);
    assertTrue(e.isRoundActive("round"));
    e.handleAction("round", 3, BetAction.CALL, null);
    assertFalse(e.isRoundActive("round"));
    assertEquals(new BigDecimal("60.00"), e.getBetSnapshot("round").mainPot());
    for (long uid = 1; uid <= 3; uid++)
      assertEquals(new BigDecimal("20.00"), e.getPlayerSnapshot("round", uid).myCurrentBet());
  }

  @Test
  void singleSurvivorDoesNotRevealCards() throws Exception {
    var e = engine(2, 100);
    e.handleAction("round", 2, BetAction.FOLD, null);
    assertTrue(e.getRoundResult("round").playerResults().stream().allMatch(p -> p.hand() == null));
    assertEquals(
        0,
        e.getRoundResult("round").playerResults().stream()
            .map(PlayerResult::profit)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .signum());
  }

  @Test
  void leavingTwoPlayerRoundFoldsAndDoesNotRefund() throws Exception {
    var e = engine(2, 100);
    var result = e.handlePlayerLeft("round", 2).roundResult();
    assertEquals("FINISHED", result.roundStatus());
    assertEquals(new BigDecimal("99.00"), e.getPlayerSnapshot("round", 2).myBalance());
    assertEquals(new BigDecimal("101.00"), e.getPlayerSnapshot("round", 1).myBalance());
    assertEquals("FOLD", result.playerResults().get(1).result());
  }

  @Test
  void explicitCancellationRefundsEveryParticipant() throws Exception {
    var e = engine(3, 100);
    e.handleAction("round", 2, BetAction.RAISE, new BigDecimal("30.00"));
    var result = e.cancelRound("round").roundResult();
    assertEquals("CANCELLED", result.roundStatus());
    assertTrue(result.playerResults().stream().allMatch(p -> p.profit().signum() == 0));
    for (long id = 1; id <= 3; id++) {
      assertEquals(new BigDecimal("100.00"), e.getPlayerSnapshot("round", id).myBalance());
    }
  }

  @Test
  void offTurnDepartureDoesNotSkipCurrentPlayer() throws Exception {
    var e = engine(4, 100);
    assertEquals(2L, e.getPublicSnapshot("round").currentTurnUserId());
    e.handlePlayerLeft("round", 4);
    assertEquals(2L, e.getPublicSnapshot("round").currentTurnUserId());
  }

  @Test
  void consecutiveCardsUseNormalPointsAndSuitTieBreaks() {
    var a = eval.evaluate(List.of(c(Rank.FOUR, Suit.RO), c(Rank.FIVE, Suit.BICH), c(Rank.SIX, Suit.CHUON)));
    var b = eval.evaluate(List.of(c(Rank.FOUR, Suit.CHUON), c(Rank.FIVE, Suit.CHUON), c(Rank.SIX, Suit.CO)));
    assertEquals(HandType.DIEM, a.handType());
    assertEquals(5, a.score());
    assertTrue(eval.compare(a, b) > 0);
    var lowA = eval.evaluate(List.of(c(Rank.ACE, Suit.RO), c(Rank.TWO, Suit.BICH), c(Rank.THREE, Suit.CHUON)));
    var lowB = eval.evaluate(List.of(c(Rank.ACE, Suit.CHUON), c(Rank.TWO, Suit.CHUON), c(Rank.THREE, Suit.CO)));
    assertEquals(HandType.DIEM, lowA.handType());
    assertEquals(6, lowA.score());
    assertTrue(eval.compare(lowA, lowB) > 0);
    var ten = eval.evaluate(List.of(c(Rank.TWO, Suit.CO), c(Rank.THREE, Suit.BICH), c(Rank.FIVE, Suit.CHUON)));
    assertTrue(eval.compare(ten, lowA) > 0);
  }

  @Test
  void threeUnequalAllInsProduceLayersAndConservePoints() throws Exception {
    var e = new DefaultGameEngine(new Random(5));
    var people =
        List.of(
            new RoundParticipant(1, "a", "A", new BigDecimal("10.00")),
            new RoundParticipant(2, "b", "B", new BigDecimal("20.00")),
            new RoundParticipant(3, "c", "C", new BigDecimal("30.00")));
    e.initRound("r", 1, people, BigDecimal.ONE);
    e.handleAction("r", 2, BetAction.ALL_IN, null);
    e.handleAction("r", 3, BetAction.ALL_IN, null);
    e.handleAction("r", 1, BetAction.ALL_IN, null);
    var r = e.getRoundResult("r");
    assertEquals(
        List.of(new BigDecimal("30.00"), new BigDecimal("20.00"), new BigDecimal("10.00")),
        r.sidePots().stream().map(SidePot::amount).toList());
    assertEquals(
        0,
        r.playerResults().stream()
            .map(PlayerResult::profit)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .signum());
    assertTrue(e.getPlayerSnapshot("r", 1).myBalance().compareTo(new BigDecimal("30.00")) <= 0);
  }

  @Test
  void randomRoundsNeverCreatePointsOrNegativeBalances() throws Exception {
    var random = new Random(77);
    for (int sample = 0; sample < 300; sample++) {
      var e = new DefaultGameEngine(random);
      int count = 2 + random.nextInt(7);
      String id = "random" + sample;
      e.initRound(id, 1, players(count, 100), BigDecimal.ONE);
      for (int actions = 0; e.isRoundActive(id) && actions < 500; actions++) {
        var state = e.getPublicSnapshot(id);
        long uid = state.currentTurnUserId();
        var action =
            switch (random.nextInt(4)) {
              case 0 -> BetAction.FOLD;
              case 1 -> BetAction.ALL_IN;
              default -> BetAction.CALL;
            };
        e.handleAction(id, uid, action, null);
      }
      assertFalse(e.isRoundActive(id));
      var result = e.getRoundResult(id);
      assertEquals(
          0,
          result.playerResults().stream()
              .map(PlayerResult::profit)
              .reduce(BigDecimal.ZERO, BigDecimal::add)
              .signum());
      for (long uid = 1; uid <= count; uid++)
        assertTrue(e.getPlayerSnapshot(id, uid).myBalance().signum() >= 0);
    }
  }

  @Test
  void sidePotsWithoutSurvivorsRefundContributorsToTheCent() throws Exception {
    var e = new DefaultGameEngine(new Random(19));
    var people = new ArrayList<>(players(4, 100));
    people.set(0, new RoundParticipant(1, "p1", "Người 1", new BigDecimal("10.00")));
    e.initRound("refund", 1, people, BigDecimal.ONE);
    e.handleAction("refund", 2, BetAction.RAISE, new BigDecimal("20.01"));
    e.handleAction("refund", 3, BetAction.CALL, null);
    e.handleAction("refund", 4, BetAction.RAISE, new BigDecimal("30.02"));
    e.handleAction("refund", 1, BetAction.ALL_IN, null);
    e.handleAction("refund", 2, BetAction.FOLD, null);
    e.handlePlayerLeft("refund", 4);
    e.handleAction("refund", 3, BetAction.FOLD, null);

    var result = e.getRoundResult("refund");
    assertEquals("FINISHED", result.roundStatus());
    assertEquals(List.of(new BigDecimal("40.00"), new BigDecimal("30.03"), new BigDecimal("10.01")),
        result.sidePots().stream().map(SidePot::amount).toList());
    assertEquals(new BigDecimal("40.00"), e.getPlayerSnapshot("refund", 1).myBalance());
    for (long id = 2; id <= 4; id++) {
      assertEquals(new BigDecimal("90.00"), e.getPlayerSnapshot("refund", id).myBalance());
    }
    assertTrue(result.playerResults().stream().allMatch(p -> p.hand() == null));
    assertEquals(0, result.playerResults().stream().map(PlayerResult::profit)
        .reduce(BigDecimal.ZERO, BigDecimal::add).signum());
  }
}
