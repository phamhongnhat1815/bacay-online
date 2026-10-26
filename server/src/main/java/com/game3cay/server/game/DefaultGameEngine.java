package com.game3cay.server.game;

import com.game3cay.shared.model.*;
import com.game3cay.shared.network.ErrorCode;
import java.math.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Không có socket/UI/JDBC. RoomManager tuần tự hóa thao tác của từng ván. */
public final class DefaultGameEngine implements GameEngine {
  private static final BigDecimal ZERO = new BigDecimal("0.00");

  private final Map<String, Round> rounds = new ConcurrentHashMap<>();
  private final Random random;
  private final HandEvaluator evaluator = new HandEvaluator();

  public DefaultGameEngine() {
    this(new SecureRandom());
  }

  public DefaultGameEngine(Random random) {
    this.random = Objects.requireNonNull(random);
  }

  private static final class Player {
    final RoundParticipant info;
    final List<Card> cards;
    final BigDecimal initial;

    BigDecimal bet = ZERO, balance;
    boolean folded, left;

    Player(RoundParticipant p, List<Card> c) {
      info = p;
      cards = List.copyOf(c);
      initial = p.balance();
      balance = initial;
    }

    boolean canAct() {
      return !folded && !left && balance.signum() > 0;
    }
  }

  private static final class Round {
    final String id;
    final List<Player> players = new ArrayList<>();
    final LinkedHashSet<Long> pending = new LinkedHashSet<>();
    final List<BetRecord> history = new ArrayList<>();

    BigDecimal high;
    Long turn;
    long version = 1;
    String phase = "BETTING";
    RoundResult result;

    Round(String id, BigDecimal ante) {
      this.id = id;
      high = ante;
    }
  }

  private static boolean money(BigDecimal n) {
    return n != null
        && n.signum() >= 0
        && n.scale() <= 2
        && n.scale() >= 0
        && n.precision() <= 15;
  }

  @Override
  public GameStateSnapshot initRound(
      String id,
      long roomId,
      List<RoundParticipant> people,
      BigDecimal ante) throws GameEngineException {
    if (id == null
        || id.isBlank()
        || roomId <= 0
        || people == null
        || people.size() < 2
        || people.size() > 8
        || !money(ante)
        || ante.signum() == 0) {
      throw fail(ErrorCode.INVALID_REQUEST, "Ván không hợp lệ");
    }

    if (new HashSet<>(people.stream().map(RoundParticipant::userId).toList())
            .size()
        != people.size()) {
      throw fail(ErrorCode.INVALID_REQUEST, "Trùng người chơi");
    }

    for (var p : people) {
      if (p.userId() <= 0
          || !money(p.balance())
          || p.balance().compareTo(ante) < 0) {
        throw fail(
            ErrorCode.INVALID_BET_AMOUNT,
            "Có người không đủ điểm cơ bản");
      }
    }

    var deck = new ArrayList<Card>();

    for (var s : Suit.values()) {
      for (var r : Rank.values()) {
        deck.add(new Card(r, s));
      }
    }

    Collections.shuffle(deck, random);

    Round round = new Round(id, ante);

    for (int i = 0; i < people.size(); i++) {
      var p = new Player(people.get(i), deck.subList(i * 3, i * 3 + 3));
      round.players.add(p);
      pay(round, p, ante, BetAction.ANTE);

      if (p.canAct()) {
        round.pending.add(p.info.userId());
      }
    }

    chooseNext(round, 0);

    if (rounds.putIfAbsent(id, round) != null) {
      throw fail(ErrorCode.STALE_ROUND, "roundId đã tồn tại");
    }

    if (round.pending.isEmpty()) {
      finish(round, false);
    }

    return snapshot(round);
  }

  private void pay(Round r, Player p, BigDecimal amount, BetAction action) {
    p.bet = p.bet.add(amount);
    p.balance = p.balance.subtract(amount);

    r.history.add(
        new BetRecord(
            p.info.userId(),
            null,
            action,
            p.bet,
            amount,
            BigDecimal.ONE,
            BigDecimal.ONE,
            r.history.size() + 1,
            System.currentTimeMillis()));
  }

  private Round round(String id) throws GameEngineException {
    var r = rounds.get(id);

    if (r == null) {
      throw fail(
          ErrorCode.STALE_ROUND,
          "Ván không tồn tại hoặc đã kết thúc");
    }

    return r;
  }

  private Player player(Round r, long id) throws GameEngineException {
    return r.players.stream()
        .filter(p -> p.info.userId() == id)
        .findFirst()
        .orElseThrow(
            () -> fail(ErrorCode.NOT_IN_ROOM, "Không tham gia ván này"));
  }

  @Override
  public ActionResult handleAction(
      String id,
      long userId,
      BetAction action,
      BigDecimal target) throws GameEngineException {
    var r = round(id);
    var p = player(r, userId);

    if (r.result != null) {
      throw fail(ErrorCode.INVALID_STATE, "Ván đã kết thúc");
    }

    if (!Objects.equals(r.turn, userId)) {
      throw fail(ErrorCode.NOT_YOUR_TURN, "Chưa đến lượt bạn");
    }

    if (action == null
        || action == BetAction.ANTE
        || action == BetAction.ACCEPT) {
      throw fail(ErrorCode.INVALID_REQUEST, "Hành động không hỗ trợ");
    }

    if (r.history.size() >= 1000 && action != BetAction.FOLD) {
      throw fail(
          ErrorCode.INVALID_STATE,
          "Ván đạt giới hạn hành động; chọn Úp hoặc chờ hết lượt");
    }

    BigDecimal amount = ZERO;
    boolean raise = false;

    switch (action) {
      case FOLD -> {}

      case CALL -> amount = r.high.subtract(p.bet).min(p.balance);

      case ALL_IN -> {
        amount = p.balance;
        raise = p.bet.add(amount).compareTo(r.high) > 0;
      }

      case RAISE -> {
        if (!money(target)
            || target.compareTo(r.high) <= 0
            || target.compareTo(p.bet.add(p.balance)) > 0) {
          throw fail(
              ErrorCode.INVALID_BET_AMOUNT,
              "Tổng mức tố phải lớn hơn mức bàn và không vượt số điểm có");
        }

        amount = target.subtract(p.bet);
        raise = true;
      }

      default ->
          throw fail(
              ErrorCode.INVALID_REQUEST,
              "Hành động không hỗ trợ");
    }

    if (action == BetAction.FOLD) {
      p.folded = true;
    }

    pay(r, p, amount, action);
    r.pending.remove(userId);

    if (raise) {
      r.high = p.bet;
      r.pending.clear();

      for (var other : r.players) {
        if (other != p && other.canAct()) {
          r.pending.add(other.info.userId());
        }
      }
    }

    r.version++;
    advance(r, r.players.indexOf(p));

    return result(r);
  }

  private void advance(Round r, int previous) {
    r.pending.removeIf(
        id ->
            r.players.stream()
                .noneMatch(p -> p.info.userId() == id && p.canAct()));

    if (r.players.stream().filter(p -> !p.folded && !p.left).count() <= 1
        || r.pending.isEmpty()) {
      finish(r, false);
    } else {
      chooseNext(r, previous);
    }
  }

  private void chooseNext(Round r, int previous) {
    r.turn = null;

    for (int i = 1; i <= r.players.size(); i++) {
      var p = r.players.get((previous + i) % r.players.size());

      if (r.pending.contains(p.info.userId())) {
        r.turn = p.info.userId();
        break;
      }
    }
  }

  @Override
  public ActionResult handlePlayerLeft(String id, long userId)
      throws GameEngineException {
    var r = round(id);
    var p = player(r, userId);

    if (r.result != null || p.left) {
      return result(r);
    }

    p.left = true;
    p.folded = true;
    r.pending.remove(userId);
    r.version++;

    // Mất kết nối/rời bàn là úp bài, kể cả bàn chỉ có hai người.
    if (Objects.equals(r.turn, userId)) {
      advance(r, r.players.indexOf(p));
    } else if (r.pending.isEmpty()
        || r.players.stream().filter(x -> !x.folded && !x.left).count() <= 1) {
      finish(r, false);
    }

    return result(r);
  }

  private List<SidePot> pots(Round r) {
    var levels =
        r.players.stream()
            .map(p -> p.bet)
            .filter(n -> n.signum() > 0)
            .distinct()
            .sorted()
            .toList();

    var pots = new ArrayList<SidePot>();
    BigDecimal prev = ZERO;

    for (var level : levels) {
      var contributors =
          r.players.stream()
              .filter(p -> p.bet.compareTo(level) >= 0)
              .toList();

      var eligible =
          contributors.stream()
              .filter(p -> !p.folded && !p.left)
              .map(p -> p.info.userId())
              .toList();

      pots.add(
          new SidePot(
              level.subtract(prev)
                  .multiply(BigDecimal.valueOf(contributors.size())),
              eligible));

      prev = level;
    }

    return List.copyOf(pots);
  }

  private void finish(Round r, boolean cancelled) {
    if (r.result != null) {
      return;
    }

    if (r.players.stream().noneMatch(p -> !p.folded && !p.left)) {
      cancelled = true;
    }

    var wins = new HashMap<Long, BigDecimal>();

    for (var p : r.players) {
      wins.put(p.info.userId(), ZERO);
    }

    var pots = pots(r);

    var levels =
        r.players.stream()
            .map(p -> p.bet)
            .filter(n -> n.signum() > 0)
            .distinct()
            .sorted()
            .toList();

    if (!cancelled) {
      for (int i = 0; i < pots.size(); i++) {
        var pot = pots.get(i);
        List<Player> winners;

        if (pot.eligiblePlayerIds().isEmpty()) {
          // Hoàn phần góp không còn đối thủ đủ điều kiện tranh.
          BigDecimal level = levels.get(i);

          winners =
              r.players.stream()
                  .filter(p -> p.bet.compareTo(level) >= 0)
                  .toList();
        } else {
          var eligible =
              r.players.stream()
                  .filter(p -> pot.eligiblePlayerIds().contains(p.info.userId()))
                  .toList();

          var best =
              eligible.stream()
                  .max(
                      (a, b) ->
                          evaluator.compare(
                              evaluator.evaluate(a.cards),
                              evaluator.evaluate(b.cards)))
                  .orElseThrow();

          winners =
              eligible.stream()
                  .filter(
                      p ->
                          evaluator.compare(
                                  evaluator.evaluate(p.cards),
                                  evaluator.evaluate(best.cards))
                              == 0)
                  .toList();
        }

        long cents = pot.amount().movePointRight(2).longValueExact();

        for (int w = 0; w < winners.size(); w++) {
          long part =
              cents / winners.size()
                  + (w < cents % winners.size() ? 1 : 0);

          wins.merge(
              winners.get(w).info.userId(),
              BigDecimal.valueOf(part, 2),
              BigDecimal::add);
        }
      }
    }

    boolean showdown =
        r.players.stream().filter(p -> !p.folded && !p.left).count() > 1;

    var ordered =
        r.players.stream()
            .filter(p -> !p.folded && !p.left)
            .sorted(
                (a, b) ->
                    evaluator.compare(
                        evaluator.evaluate(b.cards),
                        evaluator.evaluate(a.cards)))
            .toList();

    var results = new ArrayList<PlayerResult>();

    for (var p : r.players) {
      BigDecimal profit =
          cancelled ? ZERO : wins.get(p.info.userId()).subtract(p.bet);

      p.balance = p.initial.add(profit);

      int rank =
          cancelled || p.folded
              ? 0
              : 1
                  + (int)
                      ordered.stream()
                          .filter(
                              x ->
                                  evaluator.compare(
                                          evaluator.evaluate(x.cards),
                                          evaluator.evaluate(p.cards))
                                      > 0)
                          .count();

      String outcome =
          cancelled
              ? "CANCELLED"
              : p.folded
                  ? "FOLD"
                  : profit.signum() > 0
                      ? "WIN"
                      : profit.signum() < 0 ? "LOSE" : "DRAW";

      results.add(
          new PlayerResult(
              p.info.userId(),
              p.info.username(),
              p.info.displayName(),
              !cancelled && showdown && !p.folded
                  ? evaluator.evaluate(p.cards)
                  : null,
              rank,
              outcome,
              profit));
    }

    r.phase = cancelled ? "CANCELLED" : "FINISHED";
    r.turn = null;
    r.pending.clear();
    r.version++;

    r.result =
        new RoundResult(
            r.id,
            List.copyOf(results),
            List.copyOf(r.history),
            pots,
            r.phase,
            System.currentTimeMillis());
  }

  private GameStateSnapshot snapshot(Round r) {
    return new GameStateSnapshot(
        r.id,
        r.phase,
        r.turn,
        r.players.stream()
            .filter(p -> !p.folded && !p.left)
            .map(p -> p.info.userId())
            .toList(),
        (int) r.players.stream().filter(p -> p.folded).count(),
        r.version);
  }

  private ActionResult result(Round r) {
    return new ActionResult(
        r.id,
        snapshot(r),
        r.players.stream()
            .filter(p -> !p.left)
            .map(p -> p.info.userId())
            .toList(),
        r.result != null,
        r.result);
  }

  @Override
  public List<Card> getPlayerCards(String id, long uid)
      throws GameEngineException {
    return player(round(id), uid).cards;
  }

  @Override
  public PlayerStateSnapshot getPlayerSnapshot(String id, long uid)
      throws GameEngineException {
    var r = round(id);
    var p = player(r, uid);

    return new PlayerStateSnapshot(
        id,
        p.cards,
        p.balance,
        p.bet,
        p.folded,
        p.balance.signum() == 0 && !p.folded,
        r.version);
  }

  @Override
  public BetStateSnapshot getBetSnapshot(String id)
      throws GameEngineException {
    var r = round(id);

    return new BetStateSnapshot(
        id,
        r.turn,
        r.high,
        r.players.stream().map(p -> p.bet).reduce(ZERO, BigDecimal::add),
        pots(r),
        r.players.stream()
            .map(
                p ->
                    new BetStateSnapshot.PlayerBetInfo(
                        p.info.userId(),
                        p.info.displayName(),
                        p.bet,
                        p.folded,
                        p.balance.signum() == 0 && !p.folded))
            .toList(),
        r.version);
  }

  @Override
  public GameStateSnapshot getPublicSnapshot(String id)
      throws GameEngineException {
    return snapshot(round(id));
  }

  @Override
  public RoundResult getRoundResult(String id) throws GameEngineException {
    var r = round(id);

    if (r.result == null) {
      throw fail(ErrorCode.INVALID_STATE, "Ván chưa kết thúc");
    }

    return r.result;
  }

  @Override
  public boolean isRoundActive(String id) {
    var r = rounds.get(id);
    return r != null && r.result == null;
  }

  @Override
  public void cleanupRound(String id) {
    rounds.remove(id);
  }

  private static GameEngineException fail(ErrorCode c, String m) {
    return new GameEngineException(c, m);
  }
}