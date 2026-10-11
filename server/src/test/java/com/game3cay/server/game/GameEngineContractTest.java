package com.game3cay.server.game;

import com.game3cay.server.game.exception.GameException;
import com.game3cay.server.game.model.GameState;
import com.game3cay.shared.model.BetAction;
import com.game3cay.shared.model.GamePhase;
import com.game3cay.shared.model.GameStateSnapshot;
import com.game3cay.shared.network.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test — Kiểm tra đúng API interface {@link GameEngine} — G06 Bước 4.
 *
 * <p>Xác minh:
 * - Chữ ký hàm trả đúng kiểu
 * - Đúng mã lỗi cho mỗi điều kiện lỗi
 * - Action lỗi → state không thay đổi (atomic)
 * - initRound trả đúng roundId
 */
@DisplayName("GameEngine Contract Tests (G06 - Bước 4)")
class GameEngineContractTest {

    private GameEngineImpl engine;
    private static final BigDecimal BET = BigDecimal.valueOf(100);

    @BeforeEach
    void setUp() {
        engine = new GameEngineImpl();
    }

    private String init3Players() throws GameException {
        String roundId = UUID.randomUUID().toString();
        engine.initRound(new RoundContext(roundId, 1L, List.of(1L, 2L, 3L), BET), 0);
        return roundId;
    }

    // ─── initRound contract ───────────────────────────────────────────────────

    @Test
    @DisplayName("initRound trả về đúng roundId đã cung cấp trong RoundContext")
    void initRound_returnsExactRoundId() throws GameException {
        String expected = UUID.randomUUID().toString();
        RoundContext ctx = new RoundContext(expected, 1L, List.of(1L, 2L), BET);
        String actual = engine.initRound(ctx, 0);
        assertEquals(expected, actual, "initRound phải trả về đúng roundId từ context");
    }

    @Test
    @DisplayName("initRound với roundId đã tồn tại → throw INVALID_STATE")
    void initRound_duplicateRoundId_throwsInvalidState() throws GameException {
        String roundId = UUID.randomUUID().toString();
        RoundContext ctx = new RoundContext(roundId, 1L, List.of(1L, 2L), BET);
        engine.initRound(ctx, 0);

        GameException ex = assertThrows(GameException.class,
                () -> engine.initRound(ctx, 0));
        assertEquals(ErrorCode.INVALID_STATE, ex.getErrorCode());
    }

    @Test
    @DisplayName("initRound với dealerIndex ngoài phạm vi → throw INVALID_REQUEST")
    void initRound_invalidDealerIndex_throwsInvalidRequest() {
        RoundContext ctx = new RoundContext(UUID.randomUUID().toString(), 1L, List.of(1L, 2L), BET);
        GameException ex = assertThrows(GameException.class,
                () -> engine.initRound(ctx, 5));
        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
    }

    // ─── handleAction error code contract ────────────────────────────────────

    @Test
    @DisplayName("handleAction với roundId không tồn tại → throw STALE_ROUND")
    void handleAction_unknownRoundId_throwsStaleRound() {
        GameException ex = assertThrows(GameException.class,
                () -> engine.handleAction(1L, "no-such-round", BetAction.FOLD, null));
        assertEquals(ErrorCode.STALE_ROUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("handleAction với userId không có trong ván → throw FORBIDDEN")
    void handleAction_unknownUser_throwsForbidden() throws GameException {
        String roundId = init3Players();
        GameException ex = assertThrows(GameException.class,
                () -> engine.handleAction(999L, roundId, BetAction.FOLD, null));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("handleAction khi không phải lượt mình → throw NOT_YOUR_TURN")
    void handleAction_notYourTurn_throwsNotYourTurn() throws GameException {
        String roundId = init3Players();
        GameState state = engine.getGameState(roundId);
        Long currentTurn = state.getCurrentTurnUserId(); // Người đang đến lượt

        // Gọi cho người KHÁC lượt
        long wrongUser = state.getPlayerOrder().stream()
                .filter(id -> !id.equals(currentTurn)).findFirst().orElseThrow();

        GameException ex = assertThrows(GameException.class,
                () -> engine.handleAction(wrongUser, roundId, BetAction.CALL, null));
        assertEquals(ErrorCode.NOT_YOUR_TURN, ex.getErrorCode());
    }

    @Test
    @DisplayName("handleAction RAISE với raiseAmount <= 0 → throw INVALID_BET_AMOUNT")
    void handleAction_raise_zeroAmount_throwsInvalidBetAmount() throws GameException {
        String roundId = init3Players();
        GameState state = engine.getGameState(roundId);
        Long currentTurn = state.getCurrentTurnUserId();

        GameException ex = assertThrows(GameException.class,
                () -> engine.handleAction(currentTurn, roundId, BetAction.RAISE, BigDecimal.ZERO));
        assertEquals(ErrorCode.INVALID_BET_AMOUNT, ex.getErrorCode());
    }

    @Test
    @DisplayName("handleAction RAISE vượt balance → throw INVALID_BET_AMOUNT")
    void handleAction_raise_exceedsBalance_throwsInvalidBetAmount() throws GameException {
        String roundId = init3Players();
        GameState state = engine.getGameState(roundId);
        Long currentTurn = state.getCurrentTurnUserId();

        // Balance mặc định = 100 × 100 = 10000, nhưng raise thêm 999999 sẽ vượt quá
        GameException ex = assertThrows(GameException.class,
                () -> engine.handleAction(currentTurn, roundId, BetAction.RAISE,
                        new BigDecimal("999999")));
        assertEquals(ErrorCode.INVALID_BET_AMOUNT, ex.getErrorCode());
    }

    @Test
    @DisplayName("handleAction sau FINISHED → throw STALE_ROUND")
    void handleAction_afterFinished_throwsStaleRound() throws GameException {
        String roundId = init3Players();
        engine.getGameState(roundId).setPhase(GamePhase.FINISHED);

        GameException ex = assertThrows(GameException.class,
                () -> engine.handleAction(1L, roundId, BetAction.FOLD, null));
        assertEquals(ErrorCode.STALE_ROUND, ex.getErrorCode());
    }

    // ─── Atomicity: Action lỗi → state không thay đổi ────────────────────────

    @Test
    @DisplayName("Action lỗi (NOT_YOUR_TURN) → stateVersion không tăng")
    void handleAction_onException_stateVersionUnchanged() throws GameException {
        String roundId = init3Players();
        GameState state = engine.getGameState(roundId);
        Long currentTurn = state.getCurrentTurnUserId();
        long wrongUser = state.getPlayerOrder().stream()
                .filter(id -> !id.equals(currentTurn)).findFirst().orElseThrow();

        GameStateSnapshot before = engine.getPublicSnapshot(roundId);

        assertThrows(GameException.class,
                () -> engine.handleAction(wrongUser, roundId, BetAction.CALL, null));

        GameStateSnapshot after = engine.getPublicSnapshot(roundId);
        assertEquals(before.stateVersion(), after.stateVersion(),
                "stateVersion không được tăng khi action thất bại");
    }

    @Test
    @DisplayName("Action lỗi → fold status người chơi không thay đổi")
    void handleAction_onException_foldStatusUnchanged() throws GameException {
        String roundId = init3Players();
        GameState state = engine.getGameState(roundId);
        Long currentTurn = state.getCurrentTurnUserId();
        long wrongUser = state.getPlayerOrder().stream()
                .filter(id -> !id.equals(currentTurn)).findFirst().orElseThrow();

        boolean foldBefore = state.getPlayers().get(wrongUser).isFolded();

        assertThrows(GameException.class,
                () -> engine.handleAction(wrongUser, roundId, BetAction.FOLD, null));

        assertEquals(foldBefore, state.getPlayers().get(wrongUser).isFolded(),
                "fold status không được thay đổi khi action thất bại");
    }

    // ─── getCurrentPhase và isRoundActive contract ────────────────────────────

    @Test
    @DisplayName("getCurrentPhase với roundId không tồn tại → trả null (không throw)")
    void getCurrentPhase_unknownRound_returnsNull() {
        assertNull(engine.getCurrentPhase("no-such-round"),
                "getCurrentPhase trả null cho roundId không tồn tại");
    }

    @Test
    @DisplayName("Sau initRound: phase = BETTING, isRoundActive = true")
    void afterInitRound_correctInitialState() throws GameException {
        String roundId = init3Players();
        assertEquals(GamePhase.BETTING, engine.getCurrentPhase(roundId));
        assertTrue(engine.isRoundActive(roundId));
    }
}
