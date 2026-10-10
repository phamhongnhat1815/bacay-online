package com.game3cay.server.game;

import com.game3cay.server.game.engine.Deck;
import com.game3cay.server.game.exception.GameException;
import com.game3cay.server.game.model.GameState;
import com.game3cay.server.game.model.PlayerState;
import com.game3cay.shared.model.BetAction;
import com.game3cay.shared.model.BetStateSnapshot;
import com.game3cay.shared.model.GamePhase;
import com.game3cay.shared.model.GameStateSnapshot;
import com.game3cay.shared.model.PlayerBetInfo;
import com.game3cay.shared.model.PlayerStateSnapshot;
import com.game3cay.shared.model.RoundResult;
import com.game3cay.shared.network.ErrorCode;

import java.math.BigDecimal;
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

    // ═══ G03: TỐ & CƯỢC (Sẽ triển khai trong G03) ═══

    @Override
    public void handleAction(long actorId, String roundId, BetAction action, BigDecimal raiseAmount) throws GameException {
        throw new UnsupportedOperationException("handleAction sẽ được triển khai trong G03");
    }

    // ═══ G04: KẾT QUẢ VÁN & SO BÀI (Sẽ triển khai trong G04) ═══

    @Override
    public RoundResult finalizeRound(String roundId) throws GameException {
        throw new UnsupportedOperationException("finalizeRound sẽ được triển khai trong G04");
    }

    // ═══ G05: RỜI BÀN & SNAPSHOTS (Sẽ triển khai hoàn thiện trong G05) ═══

    @Override
    public boolean handlePlayerLeft(String roundId, long userId) throws GameException {
        throw new UnsupportedOperationException("handlePlayerLeft sẽ được triển khai trong G05");
    }

    @Override
    public GameStateSnapshot getPublicSnapshot(String roundId) throws GameException {
        GameState s = getActiveGame(roundId);
        List<Long> active = s.getActivePlayers();
        return new GameStateSnapshot(
                s.getRoundId(),
                s.getPhase() != null ? s.getPhase().name() : null,
                s.getCurrentTurnUserId(),
                active,
                s.getStateVersion()
        );
    }

    @Override
    public PlayerStateSnapshot getPlayerSnapshot(String roundId, long userId) throws GameException {
        GameState s = getActiveGame(roundId);
        PlayerState ps = s.getPlayers().get(userId);
        if (ps == null) {
            throw new GameException(ErrorCode.FORBIDDEN, "Người chơi không có trong ván: " + userId);
        }
        return new PlayerStateSnapshot(
                s.getRoundId(),
                List.copyOf(ps.getCards()),
                ps.getBalance(),
                ps.getCurrentBet(),
                s.getStateVersion()
        );
    }

    @Override
    public BetStateSnapshot getBetSnapshot(String roundId) throws GameException {
        GameState s = getActiveGame(roundId);
        List<PlayerBetInfo> playerBets = s.getPlayers().values().stream()
                .map(p -> new PlayerBetInfo(p.getUserId(), "", p.getCurrentBet(), p.isFolded(), p.isAllIn()))
                .toList();
        return new BetStateSnapshot(
                s.getRoundId(),
                s.getCurrentTurnUserId(),
                s.getCurrentHighBet(),
                s.getMainPot(),
                List.of(),
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
