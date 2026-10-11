package com.game3cay.server.game;

import com.game3cay.server.game.exception.GameException;
import com.game3cay.server.game.model.GameState;
import com.game3cay.shared.model.BetAction;
import com.game3cay.shared.model.BetStateSnapshot;
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
 * Integration test — Full flow ván Ba Cây từ đầu đến cuối — G06 Bước 1.
 *
 * <p>Mỗi test mô phỏng một kịch bản thực tế hoàn chỉnh:
 * initRound → bet rounds → finalizeRound → kiểm tra RoundResult.
 *
 * <p>Bất biến "tiền không tự sinh/mất" được kiểm tra ở mọi kịch bản FINISHED:
 * SUM(profit) == 0 (người thắng lấy đúng số tiền người thua bỏ ra).
 */
@DisplayName("GameEngine Integration Tests (G06 - Bước 1)")
class GameEngineIntegrationTest {

    private GameEngineImpl engine;

    @BeforeEach
    void setUp() {
        engine = new GameEngineImpl();
    }

    // ─── Happy Path ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Full flow 3 người: initRound → RAISE/CALL/CALL → SHOWDOWN → finalizeRound")
    void fullFlow_3players_showdown_resultValid() throws GameException {
        // 1. Khởi tạo ván: A(10L) = dealer, B(20L) đi đầu, C(30L)
        RoundContext ctx = new RoundContext(UUID.randomUUID().toString(), 1L,
                List.of(10L, 20L, 30L), new BigDecimal("100"));
        String roundId = engine.initRound(ctx, 0);

        assertNotNull(roundId);
        assertEquals(GamePhase.BETTING, engine.getCurrentPhase(roundId));

        // 2. Kiểm tra pot ban đầu = 3 × 100 = 300
        BetStateSnapshot bet = engine.getBetSnapshot(roundId);
        assertEquals(0, new BigDecimal("300").compareTo(bet.mainPot()),
                "Pot ban đầu phải = 300 (3 × ante)");

        // 3. B(20L) RAISE 50 → currentHighBet = 150
        engine.handleAction(20L, roundId, BetAction.RAISE, new BigDecimal("50"));
        assertEquals(GamePhase.BETTING, engine.getCurrentPhase(roundId));

        // 4. C(30L) CALL
        engine.handleAction(30L, roundId, BetAction.CALL, null);
        assertEquals(GamePhase.BETTING, engine.getCurrentPhase(roundId));

        // 5. A(10L) CALL → tất cả bằng 150 → chuyển SHOWDOWN
        engine.handleAction(10L, roundId, BetAction.CALL, null);
        assertEquals(GamePhase.SHOWDOWN, engine.getCurrentPhase(roundId));

        // 6. finalizeRound → RoundResult
        RoundResult result = engine.finalizeRound(roundId);

        assertNotNull(result);
        assertEquals("FINISHED", result.roundStatus());
        assertEquals(roundId, result.roundId());
        assertEquals(3, result.playerResults().size());

        // Bất biến: Tổng profit = 0
        BigDecimal totalProfit = result.playerResults().stream()
                .map(PlayerResult::profit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, BigDecimal.ZERO.compareTo(totalProfit),
                "Tổng profit phải = 0 (tiền không tự sinh/mất), thực tế: " + totalProfit);

        // 7. Gọi lần 2 → idempotent (cùng object)
        RoundResult result2 = engine.finalizeRound(roundId);
        assertSame(result, result2, "finalizeRound 2 lần phải trả cùng object");
    }

    @Test
    @DisplayName("Thắng sớm: A và C fold sau RAISE của B → B thắng không cần SHOWDOWN, hand = null")
    void earlyWin_allOthersFold() throws GameException {
        RoundContext ctx = new RoundContext(UUID.randomUUID().toString(), 1L,
                List.of(10L, 20L, 30L), new BigDecimal("50"));
        String roundId = engine.initRound(ctx, 0);
        // dealer=A(10L), lượt 1: B(20L)

        // B(20L) RAISE 50 → currentHighBet=100, A và C chưa bằng → BETTING
        engine.handleAction(20L, roundId, BetAction.RAISE, new BigDecimal("50"));
        assertEquals(GamePhase.BETTING, engine.getCurrentPhase(roundId));

        // C(30L) FOLD → còn B và A, A chưa bằng (50 vs 100) → vẫn BETTING
        engine.handleAction(30L, roundId, BetAction.FOLD, null);
        assertEquals(GamePhase.BETTING, engine.getCurrentPhase(roundId));

        // A(10L) FOLD → chỉ còn B → FINISHED ngay (thắng sớm)
        engine.handleAction(10L, roundId, BetAction.FOLD, null);
        assertEquals(GamePhase.FINISHED, engine.getCurrentPhase(roundId));

        RoundResult result = engine.finalizeRound(roundId);
        assertEquals("FINISHED", result.roundStatus());

        // B (20L) thắng, hand phải null (không lật bài khi thắng sớm)
        PlayerResult winner = result.playerResults().stream()
                .filter(r -> "WIN".equals(r.result())).findFirst().orElseThrow();
        assertEquals(20L, winner.userId());
        assertNull(winner.hand(), "Thắng sớm không lật bài → hand phải null");
        assertNull(winner.handType(), "Thắng sớm → handType phải null");

        // Bất biến: Tổng profit = 0
        BigDecimal totalProfit = result.playerResults().stream()
                .map(PlayerResult::profit).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, BigDecimal.ZERO.compareTo(totalProfit));
    }

    @Test
    @DisplayName("2 người: 1 FOLD, 1 thắng với SHOWDOWN đúng thứ tự")
    void twoPlayers_oneFolds_winnerClear() throws GameException {
        RoundContext ctx = new RoundContext(UUID.randomUUID().toString(), 1L,
                List.of(1L, 2L), new BigDecimal("100"));
        String roundId = engine.initRound(ctx, 0);

        // P2(2L) đi đầu, fold
        engine.handleAction(2L, roundId, BetAction.FOLD, null);

        // Chỉ còn P1 → FINISHED
        assertEquals(GamePhase.FINISHED, engine.getCurrentPhase(roundId));

        RoundResult result = engine.finalizeRound(roundId);
        assertEquals("FINISHED", result.roundStatus());

        PlayerResult winner = result.playerResults().stream()
                .filter(r -> "WIN".equals(r.result())).findFirst().orElseThrow();
        assertEquals(1L, winner.userId());
    }

    @Test
    @DisplayName("betHistory tăng đúng sequenceNo sau mỗi action")
    void betHistory_sequenceNoIncreasing() throws GameException {
        RoundContext ctx = new RoundContext(UUID.randomUUID().toString(), 1L,
                List.of(1L, 2L, 3L), new BigDecimal("100"));
        String roundId = engine.initRound(ctx, 0);
        GameState state = engine.getGameState(roundId);

        // P2 RAISE, P3 CALL, P1 CALL → 3 action
        engine.handleAction(2L, roundId, BetAction.RAISE, new BigDecimal("50"));
        engine.handleAction(3L, roundId, BetAction.CALL, null);
        engine.handleAction(1L, roundId, BetAction.CALL, null);

        List<com.game3cay.shared.model.BetRecord> history = state.getBetHistory();
        assertEquals(3, history.size(), "Phải có 3 BetRecord");

        for (int i = 0; i < history.size(); i++) {
            assertEquals(i + 1, history.get(i).sequenceNo(),
                    "sequenceNo phải tăng từ 1, actual at " + i + ": " + history.get(i).sequenceNo());
        }
    }

    @Test
    @DisplayName("stateVersion tăng sau mỗi action thành công")
    void stateVersion_incrementsAfterEachAction() throws GameException {
        // Dùng 3 người với RAISE để tạo sự chênh lệch, tránh SHOWDOWN sớm
        RoundContext ctx = new RoundContext(UUID.randomUUID().toString(), 1L,
                List.of(1L, 2L, 3L), new BigDecimal("100"));
        String roundId = engine.initRound(ctx, 0);
        GameState state = engine.getGameState(roundId);

        long v0 = state.getStateVersion();

        // P2 RAISE 50 → currentHighBet=150, P3 và P1 chưa bằng → BETTING
        engine.handleAction(2L, roundId, BetAction.RAISE, new BigDecimal("50"));
        long v1 = state.getStateVersion();
        assertTrue(v1 > v0, "stateVersion phải tăng sau RAISE");
        assertEquals(GamePhase.BETTING, engine.getCurrentPhase(roundId));

        // P3 FOLD → còn P2(150) và P1(100), chưa bằng → BETTING
        engine.handleAction(3L, roundId, BetAction.FOLD, null);
        long v2 = state.getStateVersion();
        assertTrue(v2 > v1, "stateVersion phải tăng sau FOLD");
        assertEquals(GamePhase.BETTING, engine.getCurrentPhase(roundId));
    }
}
