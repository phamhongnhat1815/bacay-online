package com.game3cay.server.game;

import com.game3cay.server.game.engine.BettingEngine;
import com.game3cay.server.game.engine.Deck;
import com.game3cay.server.game.engine.HandComparator;
import com.game3cay.server.game.engine.HandEvaluator;
import com.game3cay.server.game.engine.SidePotCalculator;
import com.game3cay.server.game.exception.GameException;
import com.game3cay.server.game.model.GameState;
import com.game3cay.server.game.model.PlayerState;
import com.game3cay.shared.model.BetAction;
import com.game3cay.shared.model.BetRecord;
import com.game3cay.shared.model.BetStateSnapshot;
import com.game3cay.shared.model.GamePhase;
import com.game3cay.shared.model.GameStateSnapshot;
import com.game3cay.shared.model.Hand;
import com.game3cay.shared.model.PlayerBetInfo;
import com.game3cay.shared.model.PlayerResult;
import com.game3cay.shared.model.PlayerStateSnapshot;
import com.game3cay.shared.model.RoundResult;
import com.game3cay.shared.model.SidePot;
import com.game3cay.shared.network.ErrorCode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Triển khai chính của {@link GameEngine}.
 *
 * <p>Quản lý vòng đời ván chơi, phân phối bài, điều phối cược và tính điểm.
 */
public class GameEngineImpl implements GameEngine {

    private final Map<String, GameState> activeGames = new ConcurrentHashMap<>();

    // ═══ G02: KHỞI TẠO VÁN & CHIA BÀI ═══

    @Override
    public String initRound(RoundContext context, int dealerIndex) throws GameException {
        if (context == null) {
            throw new GameException(ErrorCode.INVALID_REQUEST, "RoundContext không được null");
        }

        List<Long> playerIds = context.playerIds();

        // 1. Kiểm tra dealerIndex
        if (dealerIndex < 0 || dealerIndex >= playerIds.size()) {
            throw new GameException(ErrorCode.INVALID_REQUEST, "dealerIndex ngoài phạm vi: " + dealerIndex);
        }

        // 2. Kiểm tra roundId trùng lặp
        if (activeGames.containsKey(context.roundId())) {
            throw new GameException(ErrorCode.INVALID_STATE, "roundId đã tồn tại: " + context.roundId());
        }

        // 3. Kiểm tra số dư người chơi trước khi thao tác (đảm bảo atomic)
        for (Long uid : playerIds) {
            BigDecimal balance = getInitialBalance(uid, context);
            if (balance.compareTo(context.betAmount()) < 0) {
                throw new GameException(ErrorCode.INVALID_BET_AMOUNT,
                        "Người chơi " + uid + " không đủ số dư để đóng ante");
            }
        }

        // 4. Tạo bộ bài 52 lá và xáo
        Deck deck = new Deck();
        deck.shuffle();

        // 5. Khởi tạo GameState
        GameState state = new GameState(
                context.roundId(),
                context.roomId(),
                context.betAmount(),
                playerIds,
                dealerIndex
        );

        // 6. Chia 3 lá bài và trừ ante cho từng người chơi
        for (int i = 0; i < playerIds.size(); i++) {
            long uid = playerIds.get(i);
            BigDecimal balance = getInitialBalance(uid, context);
            PlayerState ps = new PlayerState(uid, i + 1, balance);
            ps.receiveCards(deck.deal(3));
            ps.deductBet(context.betAmount());

            state.addPlayer(uid, ps);
            state.addPot(context.betAmount());
        }

        // 7. Thiết lập cược cao nhất hiện tại = ante, lượt đầu tiên là người kế tiếp dealer
        state.setCurrentHighBet(context.betAmount());
        state.setCurrentTurnIndex((dealerIndex + 1) % playerIds.size());
        state.setPhase(GamePhase.BETTING);

        // 8. Lưu ván vào danh sách active
        activeGames.put(context.roundId(), state);
        return context.roundId();
    }

    private BigDecimal getInitialBalance(long userId, RoundContext context) {
        if (context.initialBalances() != null && context.initialBalances().containsKey(userId)) {
            return context.initialBalances().get(userId);
        }
        // Mặc định cấp đủ số dư (100 lần cược) nếu chưa truyền
        return context.betAmount().multiply(BigDecimal.valueOf(100));
    }

    /** Lấy GameState nội bộ phục vụ unit test và xử lý engine. */
    public GameState getGameState(String roundId) {
        return activeGames.get(roundId);
    }

    private GameState getActiveGame(String roundId) {
        GameState state = activeGames.get(roundId);
        if (state == null) {
            throw new GameException(ErrorCode.STALE_ROUND, "Ván chơi không tồn tại: " + roundId);
        }
        return state;
    }

    // ═══ G03: TỐ & CƯỢC ═══

    @Override
    public void handleAction(long actorId, String roundId, BetAction action, BigDecimal raiseAmount) throws GameException {
        GameState state = getActiveGame(roundId);

        // G05: Phòng thủ hành động tới muộn — ván đã kết thúc
        if (state.getPhase() == GamePhase.FINISHED || state.getPhase() == GamePhase.CANCELLED) {
            throw new GameException(ErrorCode.STALE_ROUND, "Ván đã kết thúc, không chấp nhận thêm action");
        }

        new BettingEngine(state).handleAction(actorId, roundId, action, raiseAmount);
    }

    // ═══ G04: KẾT QUẢ VÁN & SO BÀI ═══

    @Override
    public RoundResult finalizeRound(String roundId) throws GameException {
        GameState state = getActiveGame(roundId);

        // IDEMPOTENT: Nếu đã chốt rồi → trả ngay kết quả cũ
        if (state.getFinalResult() != null) return state.getFinalResult();

        // Chỉ chốt khi ván SHOWDOWN, FINISHED hoặc CANCELLED
        // SHOWDOWN: tất cả còn lại đã bằng nhau → lật bài, Engine tự chuyển FINISHED
        if (state.getPhase() != GamePhase.FINISHED
                && state.getPhase() != GamePhase.SHOWDOWN
                && state.getPhase() != GamePhase.CANCELLED) {
            throw new GameException(ErrorCode.INVALID_STATE, "Ván chưa kết thúc: " + roundId);
        }

        // Nếu SHOWDOWN → chuyển FINISHED trước khi chốt kết quả
        if (state.getPhase() == GamePhase.SHOWDOWN) {
            state.setPhase(GamePhase.FINISHED);
        }


        RoundResult result = (state.getPhase() == GamePhase.CANCELLED)
                ? buildCancelledResult(state)
                : buildFinishedResult(state);

        state.setFinalResult(result);
        return result;
    }

    private RoundResult buildFinishedResult(GameState state) {
        HandComparator comparator = new HandComparator();
        List<PlayerState> allPlayers = new ArrayList<>(state.getPlayers().values());
        List<PlayerState> notFolded = allPlayers.stream()
                .filter(p -> !p.isFolded()).toList();

        // Tính Side Pot nếu có người ALL_IN
        boolean hasAllIn = notFolded.stream().anyMatch(PlayerState::isAllIn);
        List<SidePot> sidePots = hasAllIn
                ? SidePotCalculator.calculate(allPlayers)
                : List.of();

        List<PlayerResult> results = new ArrayList<>();

        if (notFolded.size() == 1) {
            // Thắng sớm: chỉ 1 người còn lại, không lật bài
            PlayerState winner = notFolded.get(0);
            BigDecimal profit = state.getMainPot().subtract(winner.getCurrentBet());
            results.add(new PlayerResult(
                    winner.getUserId(), "", "", null, null,
                    1, "WIN", profit, winner.getSeatNumber()));

            // Những người fold
            for (PlayerState p : allPlayers) {
                if (p.isFolded()) {
                    results.add(new PlayerResult(
                            p.getUserId(), "", "", null, null,
                            0, "FOLD", p.getCurrentBet().negate(), p.getSeatNumber()));
                }
            }
        } else {
            // SHOWDOWN: đánh giá bài và xếp hạng
            Map<PlayerState, Hand> hands = new LinkedHashMap<>();
            for (PlayerState p : notFolded) {
                hands.put(p, HandEvaluator.evaluate(p.getCards()));
            }

            // Sắp xếp giảm dần: mạnh nhất ở đầu
            List<PlayerState> ranked = notFolded.stream()
                    .sorted((a, b) -> comparator.compare(hands.get(b), hands.get(a)))
                    .toList();

            BigDecimal totalPot = state.getMainPot();

            for (int i = 0; i < ranked.size(); i++) {
                PlayerState p = ranked.get(i);
                Hand h = hands.get(p);
                int rank = i + 1;
                BigDecimal profit;
                String resultStr;

                if (i == 0) {
                    // Người thắng: nhận toàn pot - số đã bỏ
                    profit = totalPot.subtract(p.getCurrentBet());
                    resultStr = "WIN";
                } else {
                    // Người thua: âm số tiền đã bỏ
                    profit = p.getCurrentBet().negate();
                    resultStr = "LOSE";
                }

                results.add(new PlayerResult(
                        p.getUserId(), "", "",
                        h, h.type().name(),
                        rank, resultStr, profit, p.getSeatNumber()));
            }

            // Thêm kết quả FOLD
            for (PlayerState p : allPlayers) {
                if (p.isFolded()) {
                    results.add(new PlayerResult(
                            p.getUserId(), "", "", null, null,
                            0, "FOLD", p.getCurrentBet().negate(), p.getSeatNumber()));
                }
            }
        }

        return new RoundResult(
                state.getRoundId(), state.getRoomId(), "FINISHED",
                System.currentTimeMillis(), state.getBaseBetAmount(),
                results, state.getBetHistory(), sidePots, null);
    }

    private RoundResult buildCancelledResult(GameState state) {
        // Tất cả người chơi nhận lại tiền (profit = 0)
        List<PlayerResult> results = state.getPlayers().values().stream()
                .map(p -> new PlayerResult(
                        p.getUserId(), "", "", null, null,
                        0, "CANCELLED", BigDecimal.ZERO, p.getSeatNumber()))
                .toList();

        return new RoundResult(
                state.getRoundId(), state.getRoomId(), "CANCELLED",
                System.currentTimeMillis(), state.getBaseBetAmount(),
                results, state.getBetHistory(), List.of(), state.getCancelReason());
    }

    // ═══ G05: RỜI VÁN & SNAPSHOT ═══

    @Override
    public boolean handlePlayerLeft(String roundId, long userId) throws GameException {
        GameState state = activeGames.get(roundId);

        // Ván không tồn tại hoặc đã có kết quả chốt → hành động tới muộn, bỏ qua
        if (state == null || state.getFinalResult() != null) {
            return false;
        }

        // Ván đã CANCELLED/FINISHED → không xử lý thêm
        if (state.getPhase() == GamePhase.CANCELLED || state.getPhase() == GamePhase.FINISHED) {
            return false;
        }

        PlayerState player = state.getPlayers().get(userId);
        if (player == null) return false; // Người này không có trong ván

        // Đã fold rồi → không cần xử lý lại
        if (player.isFolded()) {
            return state.getActivePlayers().size() >= 2;
        }

        // Fold tự động cho người rời
        player.setFolded(true);

        // Ghi vào betHistory (để Nhật lưu DB)
        int seqNo = state.getBetHistory().size() + 1;
        state.addBetRecord(new BetRecord(
                userId, null, BetAction.FOLD,
                player.getCurrentBet(), BigDecimal.ZERO,
                BigDecimal.ONE, BigDecimal.ONE,
                seqNo, System.currentTimeMillis()
        ));

        // Nếu đang là lượt của người này → chuyển lượt
        Long currentTurn = state.getCurrentTurnUserId();
        if (currentTurn != null && currentTurn.equals(userId) && state.getPhase() == GamePhase.BETTING) {
            state.advanceTurn();
        }

        // Kiểm tra điều kiện tiếp tục: còn bao nhiêu người chưa fold
        long notFoldedCount = state.getPlayers().values().stream()
                .filter(p -> !p.isFolded()).count();

        if (notFoldedCount < 2) {
            if (notFoldedCount == 1) {
                // Còn đúng 1 người → thắng sớm
                state.setPhase(GamePhase.FINISHED);
            } else {
                // Không còn ai → hủy ván
                state.setPhase(GamePhase.CANCELLED);
                state.setCancelReason("Không đủ người chơi (người chơi rời ván)");
            }
            return false; // false = ván không còn tiếp tục bình thường
        }

        state.incrementAndGetStateVersion();
        return true; // true = ván còn tiếp tục
    }

    /**
     * Snapshot công khai — broadcast cho tất cả.
     * TUYỆT ĐỐI không chứa bài (cards) hoặc số dư (balance) của bất kỳ ai.
     */
    @Override
    public GameStateSnapshot getPublicSnapshot(String roundId) throws GameException {
        GameState s = getActiveGame(roundId);
        // activePlayers = danh sách userId chưa fold (không chứa bài)
        List<Long> activePlayers = s.getPlayers().entrySet().stream()
                .filter(e -> !e.getValue().isFolded())
                .map(Map.Entry::getKey)
                .toList();
        return new GameStateSnapshot(
                s.getRoundId(),
                s.getPhase() != null ? s.getPhase().name() : null,
                s.getCurrentTurnUserId(),  // null khi ván xong
                activePlayers,
                s.getStateVersion()
        );
    }

    /**
     * Snapshot riêng — chỉ gửi cho đúng userId đó.
     * CHỈ chứa bài của đúng userId, không bao giờ trả bài của người khác.
     */
    @Override
    public PlayerStateSnapshot getPlayerSnapshot(String roundId, long userId) throws GameException {
        GameState s = getActiveGame(roundId);
        PlayerState ps = s.getPlayers().get(userId);
        if (ps == null) {
            throw new GameException(ErrorCode.FORBIDDEN, "Người chơi không có trong ván: " + userId);
        }
        // Chỉ lấy bài của đúng userId — bảo mật tuyệt đối
        return new PlayerStateSnapshot(
                s.getRoundId(),
                List.copyOf(ps.getCards()),  // Bài bí mật — chỉ của userId này
                ps.getBalance(),
                ps.getCurrentBet(),
                s.getStateVersion()
        );
    }

    /**
     * Snapshot cược — broadcast cho tất cả.
     * TUYỆT ĐỐI không chứa bài (cards) của bất kỳ ai.
     */
    @Override
    public BetStateSnapshot getBetSnapshot(String roundId) throws GameException {
        GameState s = getActiveGame(roundId);
        List<PlayerBetInfo> playerBets = s.getPlayers().values().stream()
                .map(p -> new PlayerBetInfo(
                        p.getUserId(), "",  // displayName trống — Khánh tự bổ sung từ session
                        p.getCurrentBet(), p.isFolded(), p.isAllIn()
                ))
                .toList();
        // BetStateSnapshot không chứa bài
        return new BetStateSnapshot(
                s.getRoundId(),
                s.getCurrentTurnUserId(),
                s.getCurrentHighBet(),
                s.getMainPot(),
                List.of(),  // sidePots có khi finalizeRound được gọi
                playerBets,
                s.getStateVersion()
        );
    }

    // ═══ QUERIES ═══

    @Override
    public boolean isRoundActive(String roundId) {
        GameState s = activeGames.get(roundId);
        return s != null && s.getPhase() != GamePhase.FINISHED;
    }

    @Override
    public GamePhase getCurrentPhase(String roundId) {
        GameState s = activeGames.get(roundId);
        return s != null ? s.getPhase() : null;
    }
}
