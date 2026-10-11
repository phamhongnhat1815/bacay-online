package com.game3cay.server.game;

import com.game3cay.server.game.exception.GameException;
import com.game3cay.server.game.model.GameState;
import com.game3cay.shared.model.BetAction;
import com.game3cay.shared.model.Card;
import com.game3cay.shared.model.GamePhase;
import com.game3cay.shared.model.RoundResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge case tests — Tình huống biên của Game Engine — G06 Bước 3.
 *
 * <p>Bao gồm:
 * - 8 người chơi, bài không trùng lặp
 * - All-in khi balance = betAmount
 * - Hai ván độc lập không lẫn nhau
 * - isRoundActive() hành vi đúng
 * - finalizeRound roundId không tồn tại
 */
@DisplayName("GameEngine Edge Case Tests (G06 - Bước 3)")
class GameEngineEdgeCaseTest {

    private GameEngineImpl engine;

    @BeforeEach
    void setUp() {
        engine = new GameEngineImpl();
    }

    @Test
    @DisplayName("8 người chơi: 24 lá bài được chia, không có lá trùng")
    void eightPlayers_allCardsUnique() throws GameException {
        List<Long> eightPlayers = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
        RoundContext ctx = new RoundContext(UUID.randomUUID().toString(), 1L,
                eightPlayers, new BigDecimal("50"));
        String roundId = engine.initRound(ctx, 0);

        GameState state = engine.getGameState(roundId);
        Set<String> allCards = new HashSet<>();

        for (Long uid : eightPlayers) {
            List<Card> cards = state.getPlayers().get(uid).getCards();
            assertEquals(3, cards.size(), "Mỗi người phải có đúng 3 lá");
            for (Card c : cards) {
                String key = c.rank() + "_" + c.suit();
                assertTrue(allCards.add(key), "Lá bài " + key + " bị trùng!");
            }
        }
        assertEquals(24, allCards.size(), "Tổng phải có đúng 24 lá bài khác nhau");
    }

    @Test
    @DisplayName("All-in: người chơi có balance = betAmount → deductBet tự đặt allIn = true")
    void allIn_balanceEqualsBet_getsAllInFromAnte() throws GameException {
        BigDecimal bet = new BigDecimal("100");
        // P1 có balance = đúng betAmount → khi đóng ante sẽ all-in
        RoundContext ctx = new RoundContext(UUID.randomUUID().toString(), 1L,
                List.of(1L, 2L),
                bet,
                java.util.Map.of(
                        1L, bet,           // P1 chỉ có đúng 100 → all-in sau ante
                        2L, bet.multiply(BigDecimal.valueOf(10)) // P2 bình thường
                )
        );
        String roundId = engine.initRound(ctx, 0);
        GameState state = engine.getGameState(roundId);

        assertTrue(state.getPlayers().get(1L).isAllIn(),
                "P1 phải là all-in sau khi đóng ante bằng toàn bộ balance");
        assertFalse(state.getPlayers().get(2L).isAllIn(),
                "P2 không all-in");
    }

    @Test
    @DisplayName("isRoundActive: trả false cho roundId không tồn tại")
    void roundNotInActiveGames_returnsInactive() {
        assertFalse(engine.isRoundActive("nonexistent-round-id"),
                "roundId không tồn tại phải trả false");
    }

    @Test
    @DisplayName("isRoundActive: trả false sau khi ván FINISHED")
    void isRoundActive_afterFinished_returnsFalse() throws GameException {
        RoundContext ctx = new RoundContext(UUID.randomUUID().toString(), 1L,
                List.of(1L, 2L), new BigDecimal("100"));
        String roundId = engine.initRound(ctx, 0);

        assertTrue(engine.isRoundActive(roundId), "Ván đang chạy → true");

        GameState state = engine.getGameState(roundId);
        state.setPhase(GamePhase.FINISHED);

        assertFalse(engine.isRoundActive(roundId), "Ván FINISHED → false");
    }

    @Test
    @DisplayName("finalizeRound với roundId không tồn tại → throw STALE_ROUND")
    void finalizeRound_nonExistentRound_throwsStaleRound() {
        GameException ex = assertThrows(GameException.class,
                () -> engine.finalizeRound("nonexistent-round"));
        assertEquals(com.game3cay.shared.network.ErrorCode.STALE_ROUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("2 ván song song (2 roomId khác nhau) → state độc lập, không lẫn nhau")
    void twoConcurrentRounds_doNotInterfere() throws GameException {
        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();

        RoundContext ctx1 = new RoundContext(id1, 1L, List.of(1L, 2L), new BigDecimal("100"));
        RoundContext ctx2 = new RoundContext(id2, 2L, List.of(3L, 4L), new BigDecimal("200"));

        engine.initRound(ctx1, 0);
        engine.initRound(ctx2, 0);

        // Action trong ván 1 không ảnh hưởng ván 2
        engine.handleAction(2L, id1, BetAction.FOLD, null);
        assertEquals(GamePhase.FINISHED, engine.getCurrentPhase(id1));
        assertEquals(GamePhase.BETTING, engine.getCurrentPhase(id2),
                "Ván 2 phải vẫn BETTING, không bị ảnh hưởng bởi ván 1");

        // Pot của ván 2 = 2 × 200 = 400
        GameState state2 = engine.getGameState(id2);
        assertEquals(0, new BigDecimal("400").compareTo(state2.getMainPot()),
                "Pot ván 2 phải là 400, không lẫn với ván 1");
    }

    @Test
    @DisplayName("Profit bất biến: tổng profit FINISHED luôn = 0")
    void profitInvariant_totalAlwaysZero() throws GameException {
        RoundContext ctx = new RoundContext(UUID.randomUUID().toString(), 1L,
                List.of(1L, 2L, 3L), new BigDecimal("100"));
        String roundId = engine.initRound(ctx, 0);

        // P2 RAISE, P3 CALL, P1 CALL → SHOWDOWN
        engine.handleAction(2L, roundId, BetAction.RAISE, new BigDecimal("100"));
        engine.handleAction(3L, roundId, BetAction.CALL, null);
        engine.handleAction(1L, roundId, BetAction.CALL, null);

        RoundResult result = engine.finalizeRound(roundId);

        BigDecimal totalProfit = result.playerResults().stream()
                .map(com.game3cay.shared.model.PlayerResult::profit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, BigDecimal.ZERO.compareTo(totalProfit),
                "Tổng profit phải = 0, thực tế: " + totalProfit);
    }
}
