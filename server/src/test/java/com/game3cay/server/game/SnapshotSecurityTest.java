package com.game3cay.server.game;

import com.game3cay.server.game.exception.GameException;
import com.game3cay.server.game.model.GameState;
import com.game3cay.shared.model.BetStateSnapshot;
import com.game3cay.shared.model.Card;
import com.game3cay.shared.model.GameStateSnapshot;
import com.game3cay.shared.model.PlayerStateSnapshot;
import com.game3cay.shared.network.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test bảo mật cho 3 snapshot methods — G05 Bước 5.
 *
 * <p>Xác minh: GameStateSnapshot và BetStateSnapshot TUYỆT ĐỐI không lộ bài (cards) hoặc balance.
 * PlayerStateSnapshot chỉ chứa bài của đúng userId.
 */
@DisplayName("Snapshot Security Tests (G05 - Bước 3&5)")
class SnapshotSecurityTest {

    private GameEngineImpl engine;
    private static final BigDecimal BET = BigDecimal.valueOf(100);

    @BeforeEach
    void setUp() {
        engine = new GameEngineImpl();
    }

    private String initThreePlayers() throws GameException {
        String roundId = UUID.randomUUID().toString();
        RoundContext ctx = new RoundContext(
                roundId, 1L, List.of(1L, 2L, 3L), BET
        );
        engine.initRound(ctx, 0);
        return roundId;
    }

    // ─── GameStateSnapshot Tests ─────────────────────────────────────────────

    @Test
    @DisplayName("GameStateSnapshot không chứa thông tin bài (cards)")
    void publicSnapshot_doesNotContainCards() throws GameException {
        String roundId = initThreePlayers();

        GameStateSnapshot snapshot = engine.getPublicSnapshot(roundId);

        // GameStateSnapshot chỉ có: roundId, phase, currentTurnUserId, activePlayers, stateVersion
        assertNotNull(snapshot);
        assertNotNull(snapshot.roundId());
        assertNotNull(snapshot.phase());
        // activePlayers là List<Long> (userId) — không phải List<Card>
        assertNotNull(snapshot.activePlayers());
        snapshot.activePlayers().forEach(id ->
                assertInstanceOf(Long.class, id, "activePlayers chỉ chứa userId (Long), không phải Card"));
    }

    @Test
    @DisplayName("GameStateSnapshot không chứa thông tin số dư (balance)")
    void publicSnapshot_doesNotContainBalance() throws GameException {
        String roundId = initThreePlayers();

        GameStateSnapshot snapshot = engine.getPublicSnapshot(roundId);

        // Verify bằng reflection: GameStateSnapshot không có field nào là BigDecimal ngoài không có
        // Cách đơn giản: serializable record chỉ có đúng 5 field khai báo, không có balance
        long bigDecimalFields = java.util.Arrays.stream(snapshot.getClass().getRecordComponents())
                .filter(rc -> rc.getType() == BigDecimal.class)
                .count();
        assertEquals(0, bigDecimalFields,
                "GameStateSnapshot không được có field BigDecimal (balance/bet)");
    }

    // ─── PlayerStateSnapshot Tests ────────────────────────────────────────────

    @Test
    @DisplayName("PlayerStateSnapshot chỉ chứa đúng 3 lá bài của userId đó")
    void playerSnapshot_onlyContainsOwnCards() throws GameException {
        String roundId = initThreePlayers();
        GameState state = engine.getGameState(roundId);

        // Lấy bài của P1 từ internal state
        List<Card> p1Cards = state.getPlayers().get(1L).getCards();
        List<Card> p2Cards = state.getPlayers().get(2L).getCards();
        List<Card> p3Cards = state.getPlayers().get(3L).getCards();

        // Snapshot của P1
        PlayerStateSnapshot snapshotP1 = engine.getPlayerSnapshot(roundId, 1L);

        assertEquals(3, snapshotP1.myCards().size(), "Phải có đúng 3 lá bài");
        assertTrue(snapshotP1.myCards().containsAll(p1Cards), "Phải chứa đúng bài của P1");

        // Không chứa bài của P2 hoặc P3
        boolean containsP2Cards = p2Cards.stream().anyMatch(c -> snapshotP1.myCards().contains(c));
        boolean containsP3Cards = p3Cards.stream().anyMatch(c -> snapshotP1.myCards().contains(c));
        assertFalse(containsP2Cards, "Snapshot P1 không được chứa bài của P2");
        assertFalse(containsP3Cards, "Snapshot P1 không được chứa bài của P3");
    }

    @Test
    @DisplayName("PlayerStateSnapshot với userId không tồn tại → throw FORBIDDEN")
    void playerSnapshot_wrongUserId_throwsForbidden() throws GameException {
        String roundId = initThreePlayers();

        GameException ex = assertThrows(GameException.class,
                () -> engine.getPlayerSnapshot(roundId, 999L));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode(),
                "userId không tồn tại phải throw FORBIDDEN");
    }

    // ─── BetStateSnapshot Tests ───────────────────────────────────────────────

    @Test
    @DisplayName("BetStateSnapshot không chứa thông tin bài (cards)")
    void betSnapshot_doesNotContainCards() throws GameException {
        String roundId = initThreePlayers();

        BetStateSnapshot snapshot = engine.getBetSnapshot(roundId);

        assertNotNull(snapshot);

        // playerBets chỉ chứa userId, displayName, currentBet, folded, allIn — không có cards
        assertNotNull(snapshot.playerBets());
        snapshot.playerBets().forEach(betInfo -> {
            // PlayerBetInfo là record — verify không có method getCards()
            assertDoesNotThrow(() -> betInfo.userId()); // userId có
            assertDoesNotThrow(() -> betInfo.currentBet()); // currentBet có
            assertDoesNotThrow(() -> betInfo.folded()); // folded có
            // Không có field cards trong PlayerBetInfo
            boolean hasCardsField = java.util.Arrays.stream(betInfo.getClass().getRecordComponents())
                    .anyMatch(rc -> rc.getType() == Card.class
                            || rc.getType() == List.class && rc.getName().contains("card"));
            assertFalse(hasCardsField, "PlayerBetInfo không được có field cards");
        });
    }

    @Test
    @DisplayName("getPublicSnapshot với roundId không tồn tại → throw STALE_ROUND")
    void publicSnapshot_unknownRoundId_throwsStaleRound() {
        GameException ex = assertThrows(GameException.class,
                () -> engine.getPublicSnapshot("unknown-round-id"));
        assertEquals(ErrorCode.STALE_ROUND, ex.getErrorCode(),
                "roundId không tồn tại phải throw STALE_ROUND");
    }
}
