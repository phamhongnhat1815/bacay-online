package com.game3cay.server.game;

import com.game3cay.server.game.exception.GameException;
import com.game3cay.server.game.model.GameState;
import com.game3cay.shared.model.BetAction;
import com.game3cay.shared.model.GamePhase;
import com.game3cay.shared.network.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test phòng thủ "hành động tới muộn" — G05 Bước 6.
 *
 * <p>Xác minh: Sau khi ván đã FINISHED hoặc CANCELLED, mọi action phải bị từ chối
 * với {@link ErrorCode#STALE_ROUND}. State không được thay đổi.
 */
@DisplayName("StaleAction Tests (G05 - Bước 2&6)")
class StaleActionTest {

    private GameEngineImpl engine;
    private static final BigDecimal BET = BigDecimal.valueOf(100);

    @BeforeEach
    void setUp() {
        engine = new GameEngineImpl();
    }

    private String initTwoPlayers() throws GameException {
        String roundId = UUID.randomUUID().toString();
        RoundContext ctx = new RoundContext(
                roundId, 1L, List.of(1L, 2L), BET
        );
        engine.initRound(ctx, 0);
        return roundId;
    }

    // ─── Test cases ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Gửi action sau ván FINISHED → throw STALE_ROUND")
    void actionAfterFinished_throwsStaleRound() throws GameException {
        String roundId = initTwoPlayers();
        GameState state = engine.getGameState(roundId);

        // Chuyển phase sang FINISHED
        state.setPhase(GamePhase.FINISHED);

        // Lấy userId đang đến lượt
        Long currentTurn = state.getCurrentTurnUserId();
        // getCurrentTurnUserId trả null khi không phải BETTING — dùng userId đầu tiên
        long actorId = state.getPlayerOrder().get(0);

        GameException ex = assertThrows(GameException.class,
                () -> engine.handleAction(actorId, roundId, BetAction.FOLD, null));

        assertEquals(ErrorCode.STALE_ROUND, ex.getErrorCode(),
                "Hành động sau FINISHED phải ném STALE_ROUND");
    }

    @Test
    @DisplayName("Gửi action sau ván CANCELLED → throw STALE_ROUND")
    void actionAfterCancelled_throwsStaleRound() throws GameException {
        String roundId = initTwoPlayers();
        GameState state = engine.getGameState(roundId);

        // Chuyển phase sang CANCELLED
        state.setPhase(GamePhase.CANCELLED);
        state.setCancelReason("Test cancel");

        long actorId = state.getPlayerOrder().get(0);

        GameException ex = assertThrows(GameException.class,
                () -> engine.handleAction(actorId, roundId, BetAction.CALL, null));

        assertEquals(ErrorCode.STALE_ROUND, ex.getErrorCode(),
                "Hành động sau CANCELLED phải ném STALE_ROUND");
    }

    @Test
    @DisplayName("Gửi action với roundId không tồn tại → throw STALE_ROUND")
    void actionAfterStaleRoundId_throwsStaleRound() {
        String nonExistentRoundId = "round-does-not-exist-" + UUID.randomUUID();

        GameException ex = assertThrows(GameException.class,
                () -> engine.handleAction(1L, nonExistentRoundId, BetAction.FOLD, null));

        assertEquals(ErrorCode.STALE_ROUND, ex.getErrorCode(),
                "roundId không tồn tại phải ném STALE_ROUND");
    }

    @Test
    @DisplayName("Gửi action sau ván FINISHED → state không thay đổi")
    void actionAfterFinished_stateUnchanged() throws GameException {
        String roundId = initTwoPlayers();
        GameState state = engine.getGameState(roundId);
        state.setPhase(GamePhase.FINISHED);

        long p1 = state.getPlayerOrder().get(0);
        boolean p1FoldedBefore = state.getPlayers().get(p1).isFolded();
        int historyBefore = state.getBetHistory().size();

        // Action phải fail
        assertThrows(GameException.class,
                () -> engine.handleAction(p1, roundId, BetAction.FOLD, null));

        // State phải giữ nguyên
        assertEquals(p1FoldedBefore, state.getPlayers().get(p1).isFolded(),
                "fold status phải không đổi sau action thất bại");
        assertEquals(historyBefore, state.getBetHistory().size(),
                "betHistory phải không đổi sau action thất bại");
        assertEquals(GamePhase.FINISHED, state.getPhase(),
                "phase phải vẫn là FINISHED");
    }
}
