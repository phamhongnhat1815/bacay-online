package com.game3cay.server.game;

import com.game3cay.server.game.exception.GameException;
import com.game3cay.server.game.model.GameState;
import com.game3cay.server.game.model.PlayerState;
import com.game3cay.shared.model.GamePhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test cho {@link GameEngineImpl#handlePlayerLeft(String, long)} — G05 Bước 4.
 */
@DisplayName("GameEngineImpl.handlePlayerLeft Tests (G05 - Bước 1&4)")
class PlayerLeftTest {

    private GameEngineImpl engine;
    private static final BigDecimal BET = BigDecimal.valueOf(100);

    @BeforeEach
    void setUp() {
        engine = new GameEngineImpl();
    }

    /** Khởi tạo ván 3 người chơi, trả về roundId. */
    private String initThreePlayers() throws GameException {
        String roundId = UUID.randomUUID().toString();
        RoundContext ctx = new RoundContext(
                roundId, 1L, List.of(1L, 2L, 3L), BET
        );
        engine.initRound(ctx, 0);
        return roundId;
    }

    /** Khởi tạo ván 2 người chơi, trả về roundId. */
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
    @DisplayName("1/3 người rời → fold tự động → ván tiếp tục → return true")
    void playerLeft_autoFold_gameContinues() throws GameException {
        String roundId = initThreePlayers();

        boolean continues = engine.handlePlayerLeft(roundId, 3L);

        assertTrue(continues, "Ván phải tiếp tục khi còn 2 người");

        GameState state = engine.getGameState(roundId);
        PlayerState p3 = state.getPlayers().get(3L);
        assertTrue(p3.isFolded(), "Người rời phải bị fold tự động");
        assertEquals(GamePhase.BETTING, state.getPhase(), "Phase vẫn phải là BETTING");

        // BetHistory phải ghi lại FOLD
        assertFalse(state.getBetHistory().isEmpty(), "betHistory phải ghi lại FOLD");
        assertEquals(3L, state.getBetHistory().get(state.getBetHistory().size() - 1).userId(),
                "BetRecord phải ghi userId của người rời");
    }

    @Test
    @DisplayName("2 người, 1 rời → còn 1 người chưa fold → phase = FINISHED (thắng sớm)")
    void playerLeft_onlyOneRemaining_gameFinished() throws GameException {
        String roundId = initTwoPlayers();

        boolean continues = engine.handlePlayerLeft(roundId, 2L);

        assertFalse(continues, "Ván không còn tiếp tục bình thường");

        GameState state = engine.getGameState(roundId);
        assertEquals(GamePhase.FINISHED, state.getPhase(),
                "Phase phải là FINISHED khi còn 1 người thắng sớm");
    }

    @Test
    @DisplayName("2 người đều fold → phase = CANCELLED")
    void playerLeft_lastTwoPlayers_gameCancelled() throws GameException {
        String roundId = initTwoPlayers();

        // Fold người 1 trước
        GameState state = engine.getGameState(roundId);
        state.getPlayers().get(1L).setFolded(true);

        // Người 2 rời → không còn ai
        boolean continues = engine.handlePlayerLeft(roundId, 2L);

        assertFalse(continues, "Ván phải kết thúc");
        assertEquals(GamePhase.CANCELLED, state.getPhase(),
                "Phase phải là CANCELLED khi không còn người chơi");
        assertNotNull(state.getCancelReason(), "CancelReason phải có nội dung");
    }

    @Test
    @DisplayName("Người đang đến lượt rời ván → lượt chuyển sang người kế tiếp")
    void playerLeft_currentTurn_advancesToNext() throws GameException {
        String roundId = initThreePlayers();
        GameState state = engine.getGameState(roundId);

        // Lượt đầu tiên là người kế tiếp dealer (dealerIndex=0 → lượt = userId tại index 1 = 2L)
        Long currentTurnBefore = state.getCurrentTurnUserId();
        assertNotNull(currentTurnBefore, "Phải có người đang đến lượt");

        engine.handlePlayerLeft(roundId, currentTurnBefore);

        Long currentTurnAfter = state.getCurrentTurnUserId();
        assertNotEquals(currentTurnBefore, currentTurnAfter,
                "Lượt phải chuyển sang người kế tiếp");
    }

    @Test
    @DisplayName("Người đã fold trước đó rời ván → state không đổi")
    void playerLeft_alreadyFolded_noEffect() throws GameException {
        String roundId = initThreePlayers();
        GameState state = engine.getGameState(roundId);

        // Fold P3 thủ công trước
        state.getPlayers().get(3L).setFolded(true);
        int historyBefore = state.getBetHistory().size();

        // Gọi handlePlayerLeft cho P3 đã fold
        boolean result = engine.handlePlayerLeft(roundId, 3L);

        assertTrue(result, "Ván vẫn tiếp tục (còn P1, P2)");
        assertEquals(historyBefore, state.getBetHistory().size(),
                "betHistory không được thêm FOLD mới khi người đã fold");
    }

    @Test
    @DisplayName("Ván đã FINISHED → gọi handlePlayerLeft → return false, state không đổi")
    void playerLeft_afterGameFinished_noEffect() throws GameException {
        String roundId = initThreePlayers();
        GameState state = engine.getGameState(roundId);
        state.setPhase(GamePhase.FINISHED);

        boolean result = engine.handlePlayerLeft(roundId, 1L);

        assertFalse(result, "Phải trả false khi ván đã kết thúc");
        assertFalse(state.getPlayers().get(1L).isFolded(),
                "Player 1 không bị fold vì ván đã xong");
    }
}
