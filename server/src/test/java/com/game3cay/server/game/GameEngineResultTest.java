package com.game3cay.server.game;

import com.game3cay.server.game.exception.GameException;
import com.game3cay.server.game.model.GameState;
import com.game3cay.server.game.model.PlayerState;
import com.game3cay.shared.model.GamePhase;
import com.game3cay.shared.model.PlayerResult;
import com.game3cay.shared.model.RoundResult;
import com.game3cay.shared.network.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test cho {@link GameEngineImpl#finalizeRound(String)} — G04 Bước 6.
 */
@DisplayName("GameEngineImpl.finalizeRound Tests (G04 - Bước 4-6)")
class GameEngineResultTest {

    private GameEngineImpl engine;
    private static final BigDecimal BET = BigDecimal.valueOf(100);

    @BeforeEach
    void setUp() {
        engine = new GameEngineImpl();
    }

    /** Khởi tạo ván 3 người chơi cơ bản và trả về roundId. */
    private String initThreePlayers() throws GameException {
        String roundId = UUID.randomUUID().toString();
        RoundContext ctx = new RoundContext(
                roundId, 1L,
                List.of(1L, 2L, 3L),
                BET
        );
        engine.initRound(ctx, 0);
        return roundId;
    }

    // ─── Test cases ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("1 người còn lại (thắng sớm): nhận toàn bộ pot, hand = null")
    void finalizeRound_earlyWin_handIsNull() throws GameException {
        String roundId = initThreePlayers();
        GameState state = engine.getGameState(roundId);

        // P2 và P3 fold
        state.getPlayers().get(2L).setFolded(true);
        state.getPlayers().get(3L).setFolded(true);
        state.setPhase(GamePhase.FINISHED);

        RoundResult result = engine.finalizeRound(roundId);

        assertNotNull(result);
        assertEquals("FINISHED", result.roundStatus());

        PlayerResult winner = result.playerResults().stream()
                .filter(r -> r.userId() == 1L).findFirst().orElseThrow();

        assertEquals("WIN", winner.result());
        assertEquals(1, winner.rank());
        assertNull(winner.hand(), "Thắng sớm: hand phải null (không lật bài)");
        assertNull(winner.handType(), "Thắng sớm: handType phải null");
        // Profit = mainPot (300) - currentBet (100) = 200
        assertEquals(0, BigDecimal.valueOf(200).compareTo(winner.profit()),
                "Profit phải = 300 - 100 = 200");
    }

    @Test
    @DisplayName("SHOWDOWN: xếp hạng đúng theo độ mạnh bài")
    void finalizeRound_showdown_correctRanking() throws GameException {
        String roundId = initThreePlayers();
        GameState state = engine.getGameState(roundId);
        state.setPhase(GamePhase.FINISHED);

        RoundResult result = engine.finalizeRound(roundId);

        assertNotNull(result);
        assertEquals("FINISHED", result.roundStatus());
        assertEquals(3, result.playerResults().size());

        // Người rank 1 phải có result = "WIN"
        PlayerResult top = result.playerResults().stream()
                .filter(r -> r.rank() == 1).findFirst().orElseThrow();
        assertEquals("WIN", top.result());
        assertNotNull(top.hand(), "SHOWDOWN: hand phải != null");
        assertNotNull(top.handType(), "SHOWDOWN: handType phải != null");

        // Người còn lại phải LOSE
        result.playerResults().stream()
                .filter(r -> r.rank() > 1)
                .forEach(r -> assertEquals("LOSE", r.result()));
    }

    @Test
    @DisplayName("CANCELLED: tất cả profit = 0")
    void finalizeRound_cancelled_allProfitZero() throws GameException {
        String roundId = initThreePlayers();
        GameState state = engine.getGameState(roundId);
        state.setPhase(GamePhase.CANCELLED);
        state.setCancelReason("Phòng giải tán");

        RoundResult result = engine.finalizeRound(roundId);

        assertEquals("CANCELLED", result.roundStatus());
        assertEquals("Phòng giải tán", result.cancelReason());
        assertEquals(3, result.playerResults().size());

        for (PlayerResult pr : result.playerResults()) {
            assertEquals("CANCELLED", pr.result());
            assertEquals(0, BigDecimal.ZERO.compareTo(pr.profit()),
                    "Profit của CANCELLED phải = 0");
        }
    }

    @Test
    @DisplayName("Idempotent: gọi finalizeRound 2 lần → cùng đối tượng")
    void finalizeRound_idempotent() throws GameException {
        String roundId = initThreePlayers();
        GameState state = engine.getGameState(roundId);
        state.setPhase(GamePhase.FINISHED);

        RoundResult first = engine.finalizeRound(roundId);
        RoundResult second = engine.finalizeRound(roundId);

        assertSame(first, second, "Gọi 2 lần phải trả về cùng đối tượng (idempotent)");
    }

    @Test
    @DisplayName("Ván đang chơi (BETTING) → throw GameException INVALID_STATE")
    void finalizeRound_stillPlaying_throwsException() throws GameException {
        String roundId = initThreePlayers();
        // Phase mặc định sau initRound = BETTING

        GameException ex = assertThrows(GameException.class,
                () -> engine.finalizeRound(roundId));
        assertEquals(ErrorCode.INVALID_STATE, ex.getErrorCode(),
                "Phải ném INVALID_STATE khi ván chưa kết thúc");
    }

    @Test
    @DisplayName("RoundResult.roundId khớp với roundId ban đầu")
    void finalizeRound_roundIdStable() throws GameException {
        String roundId = initThreePlayers();
        GameState state = engine.getGameState(roundId);
        state.setPhase(GamePhase.FINISHED);

        RoundResult result = engine.finalizeRound(roundId);

        assertEquals(roundId, result.roundId(),
                "roundId trong kết quả phải khớp với roundId ban đầu");
    }

    @Test
    @DisplayName("Người FOLD: result = FOLD, profit âm, rank = 0")
    void finalizeRound_winner_receivesFullPot() throws GameException {
        String roundId = initThreePlayers();
        GameState state = engine.getGameState(roundId);

        // P3 fold sớm
        state.getPlayers().get(3L).setFolded(true);
        state.setPhase(GamePhase.FINISHED);

        RoundResult result = engine.finalizeRound(roundId);

        PlayerResult folded = result.playerResults().stream()
                .filter(r -> r.userId() == 3L).findFirst().orElseThrow();

        assertEquals("FOLD", folded.result(), "Người fold phải có result = FOLD");
        assertEquals(0, folded.rank(), "Người fold: rank = 0");
        assertTrue(folded.profit().signum() < 0, "Người fold: profit âm");
        // profit = -100 (số tiền đã bỏ ante)
        assertEquals(0, BigDecimal.valueOf(-100).compareTo(folded.profit()),
                "Profit của FOLD phải = -100 (mất ante)");
    }
}
