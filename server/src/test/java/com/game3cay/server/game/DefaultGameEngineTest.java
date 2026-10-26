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

    for (int i = 1; i <= count; i++) {
      list.add(
          new RoundParticipant(
              i,
              "p" + i,
              "Người " + i,
              BigDecimal.valueOf(balance).setScale(2)));
    }

    return list;
  }

  private DefaultGameEngine engine(int n, int balance) throws Exception {
    var e = new DefaultGameEngine(new Random(42));
    e.initRound("round", 1, players(n, balance), BigDecimal.ONE);
    return e;
  }

  @Test
  void evaluatesPriorityAndAceStraights() {
    var sap =
        eval.evaluate(
            List.of(
                c(Rank.ACE, Suit.RO),
                c(Rank.ACE, Suit.CO),
                c(Rank.ACE, Suit.BICH)));

    var high =
        eval.evaluate(
            List.of(
                c(Rank.QUEEN, Suit.CHUON),
                c(Rank.KING, Suit.CO),
                c(Rank.ACE, Suit.BICH)));

    var low =
        eval.evaluate(
            List.of(
                c(Rank.ACE, Suit.RO),
                c(Rank.TWO, Suit.CO),
                c(Rank.THREE, Suit.BICH)));

    var face =
        eval.evaluate(
            List.of(
                c(Rank.JACK, Suit.RO),
                c(Rank.JACK, Suit.CO),
                c(Rank.QUEEN, Suit.BICH)));

    var normal =
        eval.evaluate(
            List.of(
                c(Rank.FOUR, Suit.RO),
                c(Rank.FIVE, Suit.CO),
                c(Rank.KING, Suit.BICH)));

    assertEquals(HandType.SAP, sap.handType());
    assertEquals(HandType.LIENG, high.handType());
    assertTrue(eval.compare(sap, high) > 0);
    assertTrue(eval.compare(high, low) > 0);
    assertTrue(eval.compare(low, face) > 0);
    assertTrue(eval.compare(face, normal) > 0);
    assertEquals(9, normal.score());
  }

  @Test
  void suitOrderBreaksEqualPoints() {
    var a =
        eval.evaluate(
            List.of(
                c(Rank.FOUR, Suit.RO),
                c(Rank.FIVE, Suit.CO),
                c(Rank.KING, Suit.BICH)));

    var b =
        eval.evaluate(
            List.of(
                c(Rank.FOUR, Suit.CO),
                c(Rank.FIVE, Suit.BICH),
                c(Rank.KING, Suit.CHUON)));

    assertTrue(eval.compare(a, b) > 0);
  }

  @Test
  void eightPlayersReceiveUniqueCards() throws Exception {
    var e = engine(8, 100);
    var cards = new HashSet<Card>();

    for (long id = 1; id <= 8; id++) {
      cards.addAll(e.getPlayerCards("round", id));
    }

    assertEquals(24, cards.size());
    assertEquals(2L, e.getPublicSnapshot("round").currentTurnUserId());
  }

  @Test
  void wrongTurnAndInvalidRaiseDoNotMutate() throws Exception {
    var e = engine(2, 100);
    var before = e.getPublicSnapshot("round");

    assertEquals(
        ErrorCode.NOT_YOUR_TURN,
        assertThrows(
                GameEngineException.class,
                () -> e.handleAction("round", 1, BetAction.CALL, null))
            .getErrorCode());

    assertThrows(
        GameEngineException.class,
        () -> e.handleAction("round", 2, BetAction.RAISE, BigDecimal.ONE));

    assertEquals(before, e.getPublicSnapshot("round"));

    assertEquals(
        ErrorCode.STALE_ROUND,
        assertThrows(
                GameEngineException.class,
                () -> e.handleAction("old", 2, BetAction.CALL, null))
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

    assertThrows(
        GameEngineException.class,
        () -> e.handleAction("round", 2, BetAction.CALL, null));
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

    assertEquals(
        new BigDecimal("60.00"),
        e.getBetSnapshot("round").mainPot());

    for (long uid = 1; uid <= 3; uid++) {
      assertEquals(
          new BigDecimal("20.00"),
          e.getPlayerSnapshot("round", uid).myCurrentBet());
    }
  }

  @Test
  void singleSurvivorDoesNotRevealCards() throws Exception {
    var e = engine(2, 100);
    e.handleAction("round", 2, BetAction.FOLD, null);

    assertTrue(
        e.getRoundResult("round").playerResults().stream()
            .allMatch(p -> p.hand() == null));

    assertEquals(
        0,
        e.getRoundResult("round").playerResults().stream()
            .map(PlayerResult::profit)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .signum());
  }

  @Test
  void leavingTwoPlayerRoundFoldsAndSettles() throws Exception {
    var e = engine(2, 100);
    var result = e.handlePlayerLeft("round", 2).roundResult();

    assertEquals("FINISHED", result.roundStatus());

    assertEquals(
        new BigDecimal("101.00"),
        e.getPlayerSnapshot("round", 1).myBalance());

    assertEquals(
        new BigDecimal("99.00"),
        e.getPlayerSnapshot("round", 2).myBalance());

    assertEquals(
        "FOLD",
        result.playerResults().stream()
            .filter(p -> p.userId() == 2)
            .findFirst()
            .orElseThrow()
            .result());

    assertTrue(
        result.playerResults().stream().allMatch(p -> p.hand() == null));

    assertEquals(result, e.handlePlayerLeft("round", 2).roundResult());
  }

  @Test
  void leavingOutsideOwnTurnStillLosesCommittedBet() throws Exception {
    var e = engine(2, 100);

    e.handleAction("round", 2, BetAction.RAISE, new BigDecimal("20.00"));

    var result = e.handlePlayerLeft("round", 2).roundResult();

    assertEquals("FINISHED", result.roundStatus());

    assertEquals(
        new BigDecimal("101.00"),
        e.getPlayerSnapshot("round", 1).myBalance());

    assertEquals(
        new BigDecimal("99.00"),
        e.getPlayerSnapshot("round", 2).myBalance());

    assertEquals(
        0,
        result.playerResults().stream()
            .map(PlayerResult::profit)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .signum());
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
        List.of(
            new BigDecimal("30.00"),
            new BigDecimal("20.00"),
            new BigDecimal("10.00")),
        r.sidePots().stream().map(SidePot::amount).toList());

    assertEquals(
        0,
        r.playerResults().stream()
            .map(PlayerResult::profit)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .signum());

    assertTrue(
        e.getPlayerSnapshot("r", 1).myBalance()
                .compareTo(new BigDecimal("30.00"))
            <= 0);
  }

  @Test
  void randomRoundsNeverCreatePointsOrNegativeBalances() throws Exception {
    var random = new Random(77);

    for (int sample = 0; sample < 300; sample++) {
      var e = new DefaultGameEngine(random);
      int count = 2 + random.nextInt(7);
      String id = "random" + sample;

      e.initRound(id, 1, players(count, 100), BigDecimal.ONE);

      for (int actions = 0;
          e.isRoundActive(id) && actions < 500;
          actions++) {
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

      for (long uid = 1; uid <= count; uid++) {
        assertTrue(
            e.getPlayerSnapshot(id, uid).myBalance().signum() >= 0);
      }
    }
  }
}