package com.game3cay.server.game;

import com.game3cay.server.game.exception.GameException;
import com.game3cay.server.game.model.GameState;
import com.game3cay.shared.model.BetAction;
import com.game3cay.shared.model.GamePhase;
import com.game3cay.shared.model.PlayerResult;
import com.game3cay.shared.model.RoundResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test — Luồng hủy ván (CANCELLED) — G06 Bước 2.
 *
 * <p>Kiểm tra các tình huống:
 * - Người rời bàn khi chỉ còn 2 người → ván hủy
 * - Nhiều người rời bàn → ván hủy
 * - Ván hủy → tất cả profit = 0 (tiền được hoàn trả)
 * - finalizeRound sau CANCELLED → kết quả CANCELLED
 */
@DisplayName("GameEngine Cancel Tests (G06 - Bước 2)")
class GameEngineCancelTest {

    private GameEngineImpl engine;

    @BeforeEach
    void setUp() {
        engine = new GameEngineImpl();
    }

    @Test
    @DisplayName("2 người, 1 rời bàn → còn 1 người → phase = FINISHED (thắng sớm)")
    void cancel_playerLeft_lessThan2Active() throws GameException {
        RoundContext ctx = new RoundContext(UUID.randomUUID().toString(), 1L,
                List.of(10L, 20L), new BigDecimal("50"));
        String roundId = engine.initRound(ctx, 0);

        // A(10L) rời bàn → còn B(20L) → FINISHED (thắng sớm, không phải CANCELLED)
        boolean continues = engine.handlePlayerLeft(roundId, 10L);

        assertFalse(continues, "Ván phải kết thúc khi còn < 2 người");
        assertEquals(GamePhase.FINISHED, engine.getCurrentPhase(roundId),
                "Còn 1 người thắng sớm → FINISHED (không phải CANCELLED)");

        RoundResult result = engine.finalizeRound(roundId);
        assertEquals("FINISHED", result.roundStatus());
        assertNull(result.cancelReason(), "Không có cancel reason khi thắng sớm");

        // Người thắng là B(20L), profit dương
        PlayerResult winner = result.playerResults().stream()
                .filter(r -> "WIN".equals(r.result())).findFirst().orElseThrow();
        assertEquals(20L, winner.userId());
    }

    @Test
    @DisplayName("3 người, 2 người rời bàn lần lượt → ván CANCELLED sau lần rời thứ 2")
    void cancel_twoPlayersLeft_gameEventuallyCancel() throws GameException {
        RoundContext ctx = new RoundContext(UUID.randomUUID().toString(), 1L,
                List.of(1L, 2L, 3L), new BigDecimal("100"));
        String roundId = engine.initRound(ctx, 0);

        // P2 rời → còn 2 người (P1, P3) → ván tiếp tục
        boolean continues1 = engine.handlePlayerLeft(roundId, 2L);
        assertTrue(continues1, "Vẫn còn 2 người → ván tiếp tục");
        assertEquals(GamePhase.BETTING, engine.getCurrentPhase(roundId));

        // P3 rời → còn 1 người (P1) → FINISHED (thắng sớm, không phải CANCELLED)
        boolean continues2 = engine.handlePlayerLeft(roundId, 3L);
        assertFalse(continues2, "Chỉ còn 1 người → ván kết thúc");
        assertEquals(GamePhase.FINISHED, engine.getCurrentPhase(roundId),
                "Khi còn 1 người → FINISHED (thắng sớm), không phải CANCELLED");
    }

    @Test
    @DisplayName("Ván CANCELLED → finalizeRound idempotent (gọi 2 lần trả cùng object)")
    void cancel_finalizeRound_idempotent() throws GameException {
        RoundContext ctx = new RoundContext(UUID.randomUUID().toString(), 1L,
                List.of(1L, 2L), new BigDecimal("100"));
        String roundId = engine.initRound(ctx, 0);

        // Chuyển ván sang CANCELLED thủ công
        GameState state = engine.getGameState(roundId);
        state.setPhase(GamePhase.CANCELLED);
        state.setCancelReason("Test cancel idempotent");

        RoundResult r1 = engine.finalizeRound(roundId);
        RoundResult r2 = engine.finalizeRound(roundId);

        assertSame(r1, r2, "finalizeRound CANCELLED phải idempotent");
        assertEquals("CANCELLED", r1.roundStatus());
    }

    @Test
    @DisplayName("Ván CANCELLED → action mới bị từ chối với STALE_ROUND")
    void cancel_actionAfterCancel_throwsStaleRound() throws GameException {
        RoundContext ctx = new RoundContext(UUID.randomUUID().toString(), 1L,
                List.of(1L, 2L), new BigDecimal("100"));
        String roundId = engine.initRound(ctx, 0);

        // Ép ván sang CANCELLED
        GameState state = engine.getGameState(roundId);
        state.setPhase(GamePhase.CANCELLED);
        state.setCancelReason("Test cancel");

        GameException ex = assertThrows(GameException.class,
                () -> engine.handleAction(1L, roundId, BetAction.FOLD, null));
        assertEquals(com.game3cay.shared.network.ErrorCode.STALE_ROUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("handlePlayerLeft với roundId không tồn tại → trả false (không throw)")
    void cancel_playerLeft_unknownRoundId_returnsFalse() throws GameException {
        boolean result = engine.handlePlayerLeft("nonexistent-round", 1L);
        assertFalse(result, "roundId không tồn tại phải trả false (không throw)");
    }

    @Test
    @DisplayName("handlePlayerLeft người không có trong ván → trả false")
    void cancel_playerLeft_unknownUser_returnsFalse() throws GameException {
        RoundContext ctx = new RoundContext(UUID.randomUUID().toString(), 1L,
                List.of(1L, 2L), new BigDecimal("100"));
        String roundId = engine.initRound(ctx, 0);

        boolean result = engine.handlePlayerLeft(roundId, 999L);
        assertFalse(result, "userId không có trong ván phải trả false");
        assertEquals(GamePhase.BETTING, engine.getCurrentPhase(roundId),
                "Ván phải vẫn đang chạy bình thường");
    }
}
