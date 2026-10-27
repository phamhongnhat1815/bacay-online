package com.game3cay.server.game;

import com.game3cay.shared.model.BetAction;
import com.game3cay.shared.model.BetRecord;
import com.game3cay.shared.model.BetStateSnapshot;
import com.game3cay.shared.model.Card;
import com.game3cay.shared.model.GameStateSnapshot;
import com.game3cay.shared.model.PlayerResult;
import com.game3cay.shared.model.PlayerStateSnapshot;
import com.game3cay.shared.model.Rank;
import com.game3cay.shared.model.RoundResult;
import com.game3cay.shared.model.SidePot;
import com.game3cay.shared.model.Suit;
import com.game3cay.shared.network.ErrorCode;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cài đặt mặc định của {@link GameEngine} cho trò chơi Ba Cây.
 * <p>
 * Thuần túy tính toán logic trò chơi trong bộ nhớ, không phụ thuộc vào Socket, UI hay JDBC.
 * Tất cả đồng bộ hóa truy cập của từng ván được bảo đảm bởi RoomManager.
 */
public final class DefaultGameEngine implements GameEngine {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final Map<String, Round> rounds = new ConcurrentHashMap<>();
    private final Random random;
    private final HandEvaluator evaluator = new HandEvaluator();

    public DefaultGameEngine() {
        this(new SecureRandom());
    }

    public DefaultGameEngine(Random random) {
        this.random = Objects.requireNonNull(random, "random không được null");
    }

    /**
     * Trạng thái nội bộ của một người chơi trong ván bài.
     */
    private static final class Player {
        final RoundParticipant info;
        final List<Card> cards;
        final BigDecimal initialBalance;
        BigDecimal bet = ZERO;
        BigDecimal balance;
        boolean folded;
        boolean left;

        Player(RoundParticipant participant, List<Card> cards) {
            this.info = participant;
            this.cards = List.copyOf(cards);
            this.initialBalance = participant.balance();
            this.balance = initialBalance;
        }

        boolean canAct() {
            return !folded && !left && balance.signum() > 0;
        }
    }

    /**
     * Trạng thái nội bộ của một ván bài đang diễn ra.
     */
    private static final class Round {
        final String id;
        final List<Player> players = new ArrayList<>();
        final LinkedHashSet<Long> pending = new LinkedHashSet<>();
        final List<BetRecord> history = new ArrayList<>();
        BigDecimal highBet;
        Long turnUserId;
        long version = 1;
        String phase = "BETTING";
        RoundResult result;

        Round(String id, BigDecimal ante) {
            this.id = id;
            this.highBet = ante;
        }
    }

    private static boolean isValidMoney(BigDecimal amount) {
        return amount != null
                && amount.signum() >= 0
                && amount.scale() <= 2
                && amount.scale() >= 0
                && amount.precision() <= 15;
    }

    @Override
    public GameStateSnapshot initRound(String roundId, long roomId, List<RoundParticipant> participants, BigDecimal ante)
            throws GameEngineException {
        if (roundId == null || roundId.isBlank()
                || roomId <= 0
                || participants == null
                || participants.size() < 2
                || participants.size() > 8
                || !isValidMoney(ante)
                || ante.signum() == 0) {
            throw fail(ErrorCode.INVALID_REQUEST, "Thông số khởi tạo ván bài không hợp lệ");
        }

        List<Long> userIds = participants.stream().map(RoundParticipant::userId).toList();
        if (new HashSet<>(userIds).size() != participants.size()) {
            throw fail(ErrorCode.INVALID_REQUEST, "Danh sách người chơi chứa tài khoản trùng lặp");
        }

        for (RoundParticipant p : participants) {
            if (p.userId() <= 0 || !isValidMoney(p.balance()) || p.balance().compareTo(ante) < 0) {
                throw fail(ErrorCode.INVALID_BET_AMOUNT, "Có người chơi không đủ điểm cược cơ bản: " + p.displayName());
            }
        }

        // Tạo bộ bài 52 lá và xáo trộn ngẫu nhiên
        List<Card> deck = new ArrayList<>(52);
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                deck.add(new Card(rank, suit));
            }
        }
        Collections.shuffle(deck, random);

        Round round = new Round(roundId, ante);
        for (int i = 0; i < participants.size(); i++) {
            List<Card> dealtCards = deck.subList(i * 3, i * 3 + 3);
            Player player = new Player(participants.get(i), dealtCards);
            round.players.add(player);
            deductBet(round, player, ante, BetAction.ANTE);

            if (player.canAct()) {
                round.pending.add(player.info.userId());
            }
        }

        // Người chia bài (vị trí đầu tiên) chia xong, lượt tố đầu tiên thuộc về người kế tiếp
        chooseNextTurn(round, 0);

        if (rounds.putIfAbsent(roundId, round) != null) {
            throw fail(ErrorCode.STALE_ROUND, "roundId đã tồn tại trong hệ thống");
        }

        if (round.pending.isEmpty()) {
            finishRound(round, false);
        }

        return createSnapshot(round);
    }

    private void deductBet(Round round, Player player, BigDecimal amount, BetAction action) {
        player.bet = player.bet.add(amount);
        player.balance = player.balance.subtract(amount);
        round.history.add(new BetRecord(
                player.info.userId(),
                null,
                action,
                player.bet,
                amount,
                BigDecimal.ONE,
                BigDecimal.ONE,
                round.history.size() + 1,
                System.currentTimeMillis()
        ));
    }

    private Round requireRound(String roundId) throws GameEngineException {
        Round round = rounds.get(roundId);
        if (round == null) {
            throw fail(ErrorCode.STALE_ROUND, "Ván bài không tồn tại hoặc đã kết thúc");
        }
        return round;
    }

    private Player requirePlayer(Round round, long userId) throws GameEngineException {
        return round.players.stream()
                .filter(p -> p.info.userId() == userId)
                .findFirst()
                .orElseThrow(() -> fail(ErrorCode.NOT_IN_ROOM, "Người chơi không tham gia ván bài này"));
    }

    @Override
    public ActionResult handleAction(String roundId, long userId, BetAction action, BigDecimal targetBet)
            throws GameEngineException {
        Round round = requireRound(roundId);
        Player player = requirePlayer(round, userId);

        if (round.result != null) {
            throw fail(ErrorCode.INVALID_STATE, "Ván bài đã kết thúc");
        }
        if (!Objects.equals(round.turnUserId, userId)) {
            throw fail(ErrorCode.NOT_YOUR_TURN, "Chưa đến lượt hành động của bạn");
        }
        if (action == null || action == BetAction.ANTE || action == BetAction.ACCEPT) {
            throw fail(ErrorCode.INVALID_REQUEST, "Hành động đặt cược không được hỗ trợ");
        }
        if (round.history.size() >= 1000 && action != BetAction.FOLD) {
            throw fail(ErrorCode.INVALID_STATE, "Ván bài đạt giới hạn số lượt hành động; hãy chọn Úp bài");
        }

        BigDecimal addedAmount = ZERO;
        boolean isRaiseAction = false;

        switch (action) {
            case FOLD -> {
                // Không đóng góp thêm điểm
            }
            case CALL -> {
                BigDecimal needed = round.highBet.subtract(player.bet);
                addedAmount = needed.min(player.balance);
            }
            case ALL_IN -> {
                addedAmount = player.balance;
                isRaiseAction = player.bet.add(addedAmount).compareTo(round.highBet) > 0;
            }
            case RAISE -> {
                if (!isValidMoney(targetBet)
                        || targetBet.compareTo(round.highBet) <= 0
                        || targetBet.compareTo(player.bet.add(player.balance)) > 0) {
                    throw fail(
                            ErrorCode.INVALID_BET_AMOUNT,
                            "Tổng mức tố phải lớn hơn mức bàn hiện tại và không vượt quá tổng số điểm bạn có"
                    );
                }
                addedAmount = targetBet.subtract(player.bet);
                isRaiseAction = true;
            }
            default -> throw fail(ErrorCode.INVALID_REQUEST, "Hành động không hỗ trợ");
        }

        if (action == BetAction.FOLD) {
            player.folded = true;
        }

        deductBet(round, player, addedAmount, action);
        round.pending.remove(userId);

        if (isRaiseAction) {
            round.highBet = player.bet;
            round.pending.clear();
            for (Player other : round.players) {
                if (other != player && other.canAct()) {
                    round.pending.add(other.info.userId());
                }
            }
        }

        round.version++;
        advanceTurn(round, round.players.indexOf(player));
        return buildActionResult(round);
    }

    private void advanceTurn(Round round, int previousPlayerIndex) {
        // Loại bỏ những người chơi không còn khả năng hành động
        round.pending.removeIf(id -> round.players.stream()
                .noneMatch(p -> p.info.userId() == id && p.canAct()));

        long remainingActivePlayers = round.players.stream()
                .filter(p -> !p.folded && !p.left)
                .count();

        if (remainingActivePlayers <= 1 || round.pending.isEmpty()) {
            finishRound(round, false);
        } else {
            chooseNextTurn(round, previousPlayerIndex);
        }
    }

    private void chooseNextTurn(Round round, int previousPlayerIndex) {
        round.turnUserId = null;
        int totalPlayers = round.players.size();
        for (int i = 1; i <= totalPlayers; i++) {
            Player nextCandidate = round.players.get((previousPlayerIndex + i) % totalPlayers);
            if (round.pending.contains(nextCandidate.info.userId())) {
                round.turnUserId = nextCandidate.info.userId();
                break;
            }
        }
    }

    @Override
    public ActionResult handlePlayerLeft(String roundId, long userId) throws GameEngineException {
        Round round = requireRound(roundId);
        Player player = requirePlayer(round, userId);

        if (round.result != null) {
            return buildActionResult(round);
        }

        player.left = true;
        player.folded = true;
        round.pending.remove(userId);
        round.version++;

        long connectedPlayers = round.players.stream().filter(p -> !p.left).count();
        if (connectedPlayers < 2) {
            // Không còn đủ 2 người chơi kết nối để duy trì ván bài -> hủy ván, hoàn điểm
            finishRound(round, true);
        } else if (Objects.equals(round.turnUserId, userId)) {
            advanceTurn(round, round.players.indexOf(player));
        } else {
            long remainingActive = round.players.stream().filter(p -> !p.folded && !p.left).count();
            if (remainingActive <= 1) {
                finishRound(round, false);
            }
        }

        return buildActionResult(round);
    }

    private List<SidePot> computeSidePots(Round round) {
        List<BigDecimal> betLevels = round.players.stream()
                .map(p -> p.bet)
                .filter(b -> b.signum() > 0)
                .distinct()
                .sorted()
                .toList();

        List<SidePot> pots = new ArrayList<>();
        BigDecimal previousLevel = ZERO;

        for (BigDecimal level : betLevels) {
            List<Player> contributors = round.players.stream()
                    .filter(p -> p.bet.compareTo(level) >= 0)
                    .toList();

            List<Long> eligiblePlayerIds = contributors.stream()
                    .filter(p -> !p.folded && !p.left)
                    .map(p -> p.info.userId())
                    .toList();

            BigDecimal potAmount = level.subtract(previousLevel)
                    .multiply(BigDecimal.valueOf(contributors.size()));

            pots.add(new SidePot(potAmount, eligiblePlayerIds));
            previousLevel = level;
        }

        return List.copyOf(pots);
    }

    private void finishRound(Round round, boolean cancelled) {
        if (round.result != null) {
            return;
        }

        if (round.players.stream().noneMatch(p -> !p.folded && !p.left)) {
            cancelled = true;
        }

        Map<Long, BigDecimal> winAmounts = new HashMap<>();
        for (Player p : round.players) {
            winAmounts.put(p.info.userId(), ZERO);
        }

        List<SidePot> pots = computeSidePots(round);
        List<BigDecimal> betLevels = round.players.stream()
                .map(p -> p.bet)
                .filter(b -> b.signum() > 0)
                .distinct()
                .sorted()
                .toList();

        if (!cancelled) {
            for (int i = 0; i < pots.size(); i++) {
                SidePot pot = pots.get(i);
                List<Player> potWinners;

                if (pot.eligiblePlayerIds().isEmpty()) {
                    // Không có ai đủ điều kiện tranh pot này -> hoàn trả lại cho những người đã góp đến mức này
                    BigDecimal currentLevel = betLevels.get(i);
                    potWinners = round.players.stream()
                            .filter(p -> p.bet.compareTo(currentLevel) >= 0)
                            .toList();
                } else {
                    List<Player> eligiblePlayers = round.players.stream()
                            .filter(p -> pot.eligiblePlayerIds().contains(p.info.userId()))
                            .toList();

                    Player bestPlayer = eligiblePlayers.stream()
                            .max((a, b) -> evaluator.compare(evaluator.evaluate(a.cards), evaluator.evaluate(b.cards)))
                            .orElseThrow();

                    potWinners = eligiblePlayers.stream()
                            .filter(p -> evaluator.compare(evaluator.evaluate(p.cards), evaluator.evaluate(bestPlayer.cards)) == 0)
                            .toList();
                }

                long totalCents = pot.amount().movePointRight(2).longValueExact();
                int winnerCount = potWinners.size();
                for (int w = 0; w < winnerCount; w++) {
                    long shareCents = totalCents / winnerCount + (w < totalCents % winnerCount ? 1 : 0);
                    BigDecimal shareAmount = BigDecimal.valueOf(shareCents, 2);
                    winAmounts.merge(potWinners.get(w).info.userId(), shareAmount, BigDecimal::add);
                }
            }
        }

        boolean hasShowdown = round.players.stream().filter(p -> !p.folded && !p.left).count() > 1;
        List<Player> orderedWinners = round.players.stream()
                .filter(p -> !p.folded && !p.left)
                .sorted((a, b) -> evaluator.compare(evaluator.evaluate(b.cards), evaluator.evaluate(a.cards)))
                .toList();

        List<PlayerResult> playerResults = new ArrayList<>();
        for (Player p : round.players) {
            BigDecimal profit = cancelled ? ZERO : winAmounts.get(p.info.userId()).subtract(p.bet);
            p.balance = p.initialBalance.add(profit);

            int placement;
            if (cancelled || p.folded) {
                placement = 0;
            } else {
                placement = 1 + (int) orderedWinners.stream()
                        .filter(other -> evaluator.compare(evaluator.evaluate(other.cards), evaluator.evaluate(p.cards)) > 0)
                        .count();
            }

            String outcome;
            if (cancelled) {
                outcome = "CANCELLED";
            } else if (p.folded) {
                outcome = "FOLD";
            } else if (profit.signum() > 0) {
                outcome = "WIN";
            } else if (profit.signum() < 0) {
                outcome = "LOSE";
            } else {
                outcome = "DRAW";
            }

            // Chỉ lật bài khi ván không bị hủy, có showdown (từ 2 người chưa úp bài) và bản thân không úp bài
            var handToReveal = (!cancelled && hasShowdown && !p.folded) ? evaluator.evaluate(p.cards) : null;

            playerResults.add(new PlayerResult(
                    p.info.userId(),
                    p.info.username(),
                    p.info.displayName(),
                    handToReveal,
                    placement,
                    outcome,
                    profit
            ));
        }

        round.phase = cancelled ? "CANCELLED" : "FINISHED";
        round.turnUserId = null;
        round.pending.clear();
        round.version++;
        round.result = new RoundResult(
                round.id,
                List.copyOf(playerResults),
                List.copyOf(round.history),
                pots,
                round.phase,
                System.currentTimeMillis()
        );
    }

    private GameStateSnapshot createSnapshot(Round round) {
        List<Long> activePlayerIds = round.players.stream()
                .filter(p -> !p.folded && !p.left)
                .map(p -> p.info.userId())
                .toList();

        int foldedCount = (int) round.players.stream().filter(p -> p.folded).count();

        return new GameStateSnapshot(
                round.id,
                round.phase,
                round.turnUserId,
                activePlayerIds,
                foldedCount,
                round.version
        );
    }

    private ActionResult buildActionResult(Round round) {
        List<Long> remainingPlayerIds = round.players.stream()
                .filter(p -> !p.left)
                .map(p -> p.info.userId())
                .toList();

        return new ActionResult(
                round.id,
                createSnapshot(round),
                remainingPlayerIds,
                round.result != null,
                round.result
        );
    }

    @Override
    public List<Card> getPlayerCards(String roundId, long userId) throws GameEngineException {
        return requirePlayer(requireRound(roundId), userId).cards;
    }

    @Override
    public PlayerStateSnapshot getPlayerSnapshot(String roundId, long userId) throws GameEngineException {
        Round round = requireRound(roundId);
        Player player = requirePlayer(round, userId);

        boolean isAllIn = player.balance.signum() == 0 && !player.folded;

        return new PlayerStateSnapshot(
                roundId,
                player.cards,
                player.balance,
                player.bet,
                player.folded,
                isAllIn,
                round.version
        );
    }

    @Override
    public BetStateSnapshot getBetSnapshot(String roundId) throws GameEngineException {
        Round round = requireRound(roundId);
        BigDecimal totalPot = round.players.stream()
                .map(p -> p.bet)
                .reduce(ZERO, BigDecimal::add);

        List<BetStateSnapshot.PlayerBetInfo> playerBetList = round.players.stream()
                .map(p -> new BetStateSnapshot.PlayerBetInfo(
                        p.info.userId(),
                        p.info.displayName(),
                        p.bet,
                        p.folded,
                        p.balance.signum() == 0 && !p.folded
                ))
                .toList();

        return new BetStateSnapshot(
                roundId,
                round.turnUserId,
                round.highBet,
                totalPot,
                computeSidePots(round),
                playerBetList,
                round.version
        );
    }

    @Override
    public GameStateSnapshot getPublicSnapshot(String roundId) throws GameEngineException {
        return createSnapshot(requireRound(roundId));
    }

    @Override
    public RoundResult getRoundResult(String roundId) throws GameEngineException {
        Round round = requireRound(roundId);
        if (round.result == null) {
            throw fail(ErrorCode.INVALID_STATE, "Ván bài chưa kết thúc");
        }
        return round.result;
    }

    @Override
    public boolean isRoundActive(String roundId) {
        Round round = rounds.get(roundId);
        return round != null && round.result == null;
    }

    @Override
    public void cleanupRound(String roundId) {
        rounds.remove(roundId);
    }

    private static GameEngineException fail(ErrorCode code, String message) {
        return new GameEngineException(code, message);
    }
}
