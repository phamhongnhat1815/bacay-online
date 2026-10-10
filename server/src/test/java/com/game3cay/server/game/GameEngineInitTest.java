package com.game3cay.server.game;

import com.game3cay.server.game.exception.GameException;
import com.game3cay.server.game.model.GameState;
import com.game3cay.server.game.model.PlayerState;
import com.game3cay.shared.model.Card;
import com.game3cay.shared.model.GamePhase;
import com.game3cay.shared.network.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GameEngineImpl.initRound Tests (G02)")
class GameEngineInitTest {

    private GameEngineImpl engine;

    @BeforeEach
    void setUp() {
        engine = new GameEngineImpl();
    }

    @Test
    @DisplayName("Khởi tạo ván 2 người hợp lệ: pot = 200, mỗi người đủ 3 lá, phase = BETTING")
    void initRound_2players_validState() {
        String roundId = UUID.randomUUID().toString();
        RoundContext ctx = new RoundContext(
                roundId,
                101L,
                List.of(1L, 2L),
                BigDecimal.valueOf(100)
        );

        String resultRoundId = engine.initRound(ctx, 0);
        assertEquals(roundId, resultRoundId);

        GameState state = engine.getGameState(roundId);
        assertNotNull(state);
        assertEquals(roundId, state.getRoundId());
        assertEquals(101L, state.getRoomId());
        assertEquals(0, BigDecimal.valueOf(200).compareTo(state.getMainPot()));
        assertEquals(GamePhase.BETTING, state.getPhase());

        PlayerState p1 = state.getPlayers().get(1L);
        PlayerState p2 = state.getPlayers().get(2L);
        assertNotNull(p1);
        assertNotNull(p2);
        assertEquals(3, p1.getCards().size());
        assertEquals(3, p2.getCards().size());
    }

    @Test
    @DisplayName("Khởi tạo bàn tối đa 8 người: đủ 24 lá bài được chia và không trùng lặp")
    void initRound_8players_allCardsUnique() {
        String roundId = UUID.randomUUID().toString();
        List<Long> eightPlayers = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
        RoundContext ctx = new RoundContext(
                roundId,
                102L,
                eightPlayers,
                BigDecimal.valueOf(50)
        );

        engine.initRound(ctx, 0);
        GameState state = engine.getGameState(roundId);

        Set<String> allCards = new HashSet<>();
        for (Long uid : eightPlayers) {
            PlayerState ps = state.getPlayers().get(uid);
            assertEquals(3, ps.getCards().size());
            for (Card c : ps.getCards()) {
                boolean added = allCards.add(c.rank() + "_" + c.suit());
                assertTrue(added, "Lá bài bị trùng: " + c);
            }
        }
        assertEquals(24, allCards.size(), "8 người phải nhận đủ 24 lá độc nhất");
        assertEquals(0, BigDecimal.valueOf(400).compareTo(state.getMainPot()), "Pot = 8 * 50 = 400");
    }

    @Test
    @DisplayName("Lượt đi đầu tiên: người kế tiếp dealer (dealer = 0 -> turn = 1)")
    void initRound_firstTurn_isNextAfterDealer() {
        String roundId = UUID.randomUUID().toString();
        List<Long> players = List.of(10L, 20L, 30L);
        RoundContext ctx = new RoundContext(
                roundId,
                103L,
                players,
                BigDecimal.valueOf(100)
        );

        engine.initRound(ctx, 0); // Dealer là player 10L (index 0)
        GameState state = engine.getGameState(roundId);

        assertEquals(1, state.getCurrentTurnIndex());
        assertEquals(20L, state.getCurrentTurnUserId(), "Lượt đầu tiên phải là người sau dealer (index 1)");
    }

    @Test
    @DisplayName("Lượt đi xoay vòng: dealer là người cuối cùng -> lượt đầu quay về người đầu tiên (index 0)")
    void initRound_dealerIsLastPlayer_wrapAroundTurn() {
        String roundId = UUID.randomUUID().toString();
        List<Long> players = List.of(10L, 20L, 30L);
        RoundContext ctx = new RoundContext(
                roundId,
                104L,
                players,
                BigDecimal.valueOf(100)
        );

        engine.initRound(ctx, 2); // Dealer là 30L (index 2 - người cuối)
        GameState state = engine.getGameState(roundId);

        assertEquals(0, state.getCurrentTurnIndex());
        assertEquals(10L, state.getCurrentTurnUserId(), "Dealer cuối bàn thì lượt đi quay về index 0");
    }

    @Test
    @DisplayName("Khởi tạo 2 ván khác nhau không bị lẫn lộn dữ liệu")
    void initRound_roundIdUnique_twoRounds() {
        String r1 = UUID.randomUUID().toString();
        String r2 = UUID.randomUUID().toString();

        RoundContext ctx1 = new RoundContext(r1, 1L, List.of(1L, 2L), BigDecimal.valueOf(100));
        RoundContext ctx2 = new RoundContext(r2, 2L, List.of(3L, 4L, 5L), BigDecimal.valueOf(200));

        engine.initRound(ctx1, 0);
        engine.initRound(ctx2, 1);

        GameState s1 = engine.getGameState(r1);
        GameState s2 = engine.getGameState(r2);

        assertNotNull(s1);
        assertNotNull(s2);
        assertEquals(0, BigDecimal.valueOf(200).compareTo(s1.getMainPot()));
        assertEquals(0, BigDecimal.valueOf(600).compareTo(s2.getMainPot()));
        assertEquals(2, s1.getPlayers().size());
        assertEquals(3, s2.getPlayers().size());
    }

    @Test
    @DisplayName("dealerIndex ngoài phạm vi ném GameException(INVALID_REQUEST)")
    void initRound_invalidDealerIndex_throwsException() {
        RoundContext ctx = new RoundContext(
                UUID.randomUUID().toString(),
                1L,
                List.of(1L, 2L),
                BigDecimal.valueOf(100)
        );

        GameException exNegative = assertThrows(GameException.class, () -> engine.initRound(ctx, -1));
        assertEquals(ErrorCode.INVALID_REQUEST, exNegative.getErrorCode());

        GameException exOverflow = assertThrows(GameException.class, () -> engine.initRound(ctx, 2));
        assertEquals(ErrorCode.INVALID_REQUEST, exOverflow.getErrorCode());
    }

    @Test
    @DisplayName("Khởi tạo trùng roundId ném GameException(INVALID_STATE)")
    void initRound_duplicateRoundId_throwsException() {
        String roundId = UUID.randomUUID().toString();
        RoundContext ctx = new RoundContext(
                roundId,
                1L,
                List.of(1L, 2L),
                BigDecimal.valueOf(100)
        );

        engine.initRound(ctx, 0);

        GameException ex = assertThrows(GameException.class, () -> engine.initRound(ctx, 0));
        assertEquals(ErrorCode.INVALID_STATE, ex.getErrorCode());
    }

    @Test
    @DisplayName("Khấu trừ tiền cược cửa (ante) từ số dư ban đầu chính xác")
    void initRound_ante_deductedFromBalance() {
        String roundId = UUID.randomUUID().toString();
        Map<Long, BigDecimal> balances = Map.of(
                1L, BigDecimal.valueOf(500),
                2L, BigDecimal.valueOf(800)
        );
        RoundContext ctx = new RoundContext(
                roundId,
                1L,
                List.of(1L, 2L),
                BigDecimal.valueOf(100),
                balances
        );

        engine.initRound(ctx, 0);
        GameState state = engine.getGameState(roundId);

        PlayerState p1 = state.getPlayers().get(1L);
        PlayerState p2 = state.getPlayers().get(2L);

        assertEquals(0, BigDecimal.valueOf(400).compareTo(p1.getBalance()), "Balance 1: 500 - 100 = 400");
        assertEquals(0, BigDecimal.valueOf(100).compareTo(p1.getCurrentBet()), "CurrentBet 1 = 100");

        assertEquals(0, BigDecimal.valueOf(700).compareTo(p2.getBalance()), "Balance 2: 800 - 100 = 700");
        assertEquals(0, BigDecimal.valueOf(100).compareTo(p2.getCurrentBet()), "CurrentBet 2 = 100");
    }

    @Test
    @DisplayName("Người chơi không đủ tiền ante ném GameException(INVALID_BET_AMOUNT)")
    void initRound_insufficientBalance_throwsException() {
        String roundId = UUID.randomUUID().toString();
        Map<Long, BigDecimal> balances = Map.of(
                1L, BigDecimal.valueOf(500),
                2L, BigDecimal.valueOf(50) // Cần 100 ante nhưng chỉ có 50
        );
        RoundContext ctx = new RoundContext(
                roundId,
                1L,
                List.of(1L, 2L),
                BigDecimal.valueOf(100),
                balances
        );

        GameException ex = assertThrows(GameException.class, () -> engine.initRound(ctx, 0));
        assertEquals(ErrorCode.INVALID_BET_AMOUNT, ex.getErrorCode());

        // Kiểm tra không để lại rác trạng thái
        assertNull(engine.getGameState(roundId), "Ván lỗi không được lưu vào activeGames");
    }

    @Test
    @DisplayName("RoundContext null ném GameException(INVALID_REQUEST)")
    void initRound_nullContext_throwsException() {
        GameException ex = assertThrows(GameException.class, () -> engine.initRound(null, 0));
        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
    }
}
