package com.game3cay.server.game;

import com.game3cay.server.game.engine.BettingEngine;
import com.game3cay.server.game.exception.GameException;
import com.game3cay.server.game.model.GameState;
import com.game3cay.server.game.model.PlayerState;
import com.game3cay.shared.model.BetAction;
import com.game3cay.shared.model.Card;
import com.game3cay.shared.model.GamePhase;
import com.game3cay.shared.model.Rank;
import com.game3cay.shared.model.Suit;
import com.game3cay.shared.network.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BettingEngine Tests (G03 - Bước 6-7)")
class BettingEngineTest {

    // ─── Fixture ───────────────────────────────────────────────────────────

    private static final long P1 = 1L;
    private static final long P2 = 2L;
    private static final long P3 = 3L;

    private static final BigDecimal ANTE = BigDecimal.valueOf(100);
    private static final BigDecimal BALANCE = BigDecimal.valueOf(1000);

    private String roundId;
    private GameState state;
    private BettingEngine engine;

    @BeforeEach
    void setUp() {
        roundId = UUID.randomUUID().toString();
        state = buildState(roundId, ANTE, P1, P2, P3);
        engine = new BettingEngine(state);
    }

    /**
     * Tạo GameState 3 người đã qua giai đoạn DEALING (chuẩn bị vào BETTING).
     * Mỗi người đã trừ ante, nhận bài và currentBet = ante.
     */
    private static GameState buildState(String id, BigDecimal ante, long... playerIds) {
        GameState s = new GameState(id, 1L, ante, toList(playerIds), 0);
        for (int i = 0; i < playerIds.length; i++) {
            long uid = playerIds[i];
            PlayerState ps = new PlayerState(uid, i + 1, BALANCE);
            // Giả lập đã trừ ante
            try { ps.deductBet(ante); } catch (GameException ignored) {}
            // Chia 3 lá bài giả
            ps.receiveCards(List.of(
                new Card(Rank.ACE, Suit.BICH),
                new Card(Rank.TWO, Suit.CO),
                new Card(Rank.FIVE, Suit.RO)
            ));
            s.addPlayer(uid, ps);
            s.addPot(ante);
        }
        s.setCurrentHighBet(ante);                      // Mức cược ban đầu = ante
        s.setCurrentTurnIndex(1);                       // Lượt P2 đi trước (dealer = 0 = P1)
        s.setPhase(GamePhase.BETTING);
        return s;
    }

    private static List<Long> toList(long... ids) {
        java.util.List<Long> list = new java.util.ArrayList<>();
        for (long id : ids) list.add(id);
        return list;
    }

    private PlayerState player(long uid) { return state.getPlayers().get(uid); }

    // ─── RAISE hợp lệ ──────────────────────────────────────────────────────

    @Test
    @DisplayName("RAISE hợp lệ: cập nhật pot, currentHighBet, lịch sử action và chuyển lượt")
    void raise_valid_updatesState() throws GameException {
        // P2 đang ở lượt, RAISE thêm 50
        BigDecimal raisedBy = BigDecimal.valueOf(50);
        BigDecimal potBefore = state.getMainPot();
        long versionBefore = state.getStateVersion();

        engine.handleAction(P2, roundId, BetAction.RAISE, raisedBy);

        // currentHighBet = ante(100) + raise(50) = 150
        assertEquals(0, BigDecimal.valueOf(150).compareTo(state.getCurrentHighBet()));
        // P2 bỏ thêm 50, balance = 1000-100-50 = 850
        assertEquals(0, BigDecimal.valueOf(850).compareTo(player(P2).getBalance()));
        // currentBet P2 = 100+50 = 150
        assertEquals(0, BigDecimal.valueOf(150).compareTo(player(P2).getCurrentBet()));
        // Pot tăng thêm 50
        assertEquals(0, potBefore.add(raisedBy).compareTo(state.getMainPot()));
        // Có 1 bản ghi lịch sử
        assertEquals(1, state.getBetHistory().size());
        // stateVersion tăng
        assertTrue(state.getStateVersion() > versionBefore);
        // Lượt chuyển sang người tiếp (P3 hoặc P1)
        assertNotEquals(P2, state.getCurrentTurnUserId());
    }

    // ─── CALL hợp lệ ───────────────────────────────────────────────────────

    @Test
    @DisplayName("CALL hợp lệ: P2 CALL bắt kịp mức cược hiện tại sau khi P1 đã RAISE")
    void call_valid_callsCurrentHighBet() throws GameException {
        // Giả lập P1 là người đặt cược cao hơn bằng cách set highBet thủ công
        // (Thực tế P2 đang ở lượt, ta skip bằng cách cho P2 RAISE trước rồi P3 CALL)
        // P2 RAISE 50 → highBet = 150
        engine.handleAction(P2, roundId, BetAction.RAISE, BigDecimal.valueOf(50));
        // Giờ P3 ở lượt, CALL 150 - 100 = 50 thêm
        BigDecimal balanceBefore = player(P3).getBalance();
        engine.handleAction(P3, roundId, BetAction.CALL, null);

        assertEquals(0, BigDecimal.valueOf(150).compareTo(player(P3).getCurrentBet()));
        assertEquals(0, balanceBefore.subtract(BigDecimal.valueOf(50)).compareTo(player(P3).getBalance()));
    }

    // ─── FOLD hợp lệ ───────────────────────────────────────────────────────

    @Test
    @DisplayName("FOLD hợp lệ: người chơi bị đánh dấu folded và không còn trong activePlayers")
    void fold_valid_marksPlayerFolded() throws GameException {
        assertFalse(player(P2).isFolded());
        engine.handleAction(P2, roundId, BetAction.FOLD, null);
        assertTrue(player(P2).isFolded(), "P2 phải được đánh dấu folded");
        // P2 không còn trong active
        assertFalse(state.getActivePlayers().contains(P2));
    }

    // ─── ALL_IN hợp lệ ─────────────────────────────────────────────────────

    @Test
    @DisplayName("ALL_IN hợp lệ: người chơi dồn toàn bộ balance, allIn = true")
    void allIn_valid_marksPlayerAllIn() throws GameException {
        BigDecimal balanceBefore = player(P2).getBalance();
        assertFalse(player(P2).isAllIn());

        engine.handleAction(P2, roundId, BetAction.ALL_IN, null);

        assertTrue(player(P2).isAllIn(), "P2 phải được đánh dấu allIn");
        assertEquals(0, BigDecimal.ZERO.compareTo(player(P2).getBalance()), "Balance phải về 0");
        // Pot tăng đúng số tiền balance ban đầu
        assertEquals(0, state.getMainPot().subtract(BigDecimal.valueOf(300).add(balanceBefore))
                          .abs().compareTo(BigDecimal.ZERO));
    }

    // ─── Lỗi: sai roundId ─────────────────────────────────────────────────

    @Test
    @DisplayName("Sai roundId → GameException(STALE_ROUND) và state không đổi")
    void action_wrongRoundId_throwsStaleRound() {
        BigDecimal potBefore = state.getMainPot();
        GameException ex = assertThrows(GameException.class,
            () -> engine.handleAction(P2, "wrong-round-id", BetAction.CALL, null));
        assertEquals(ErrorCode.STALE_ROUND, ex.getErrorCode());
        // State không thay đổi
        assertEquals(0, potBefore.compareTo(state.getMainPot()));
    }

    // ─── Lỗi: sai phase ───────────────────────────────────────────────────

    @Test
    @DisplayName("Sai phase (SHOWDOWN) → GameException(INVALID_STATE) và state không đổi")
    void action_wrongPhase_throwsInvalidState() {
        state.setPhase(GamePhase.SHOWDOWN);
        BigDecimal potBefore = state.getMainPot();

        GameException ex = assertThrows(GameException.class,
            () -> engine.handleAction(P2, roundId, BetAction.CALL, null));
        assertEquals(ErrorCode.INVALID_STATE, ex.getErrorCode());
        assertEquals(0, potBefore.compareTo(state.getMainPot()));
    }

    // ─── Lỗi: người không có trong ván ─────────────────────────────────────

    @Test
    @DisplayName("actorId không có trong ván → GameException(FORBIDDEN)")
    void action_wrongActor_throwsForbidden() {
        GameException ex = assertThrows(GameException.class,
            () -> engine.handleAction(999L, roundId, BetAction.CALL, null));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    // ─── Lỗi: không phải lượt ─────────────────────────────────────────────

    @Test
    @DisplayName("P1 action khi đang là lượt P2 → GameException(NOT_YOUR_TURN)")
    void action_notYourTurn_throwsNotYourTurn() {
        assertEquals(P2, state.getCurrentTurnUserId(), "Lượt phải đang là P2");
        GameException ex = assertThrows(GameException.class,
            () -> engine.handleAction(P1, roundId, BetAction.CALL, null));
        assertEquals(ErrorCode.NOT_YOUR_TURN, ex.getErrorCode());
    }

    // ─── Lỗi: đã fold rồi action tiếp ─────────────────────────────────────

    @Test
    @DisplayName("Người đã fold cố action lại → GameException(INVALID_STATE)")
    void action_alreadyFolded_throwsInvalidState() throws GameException {
        // Cho P2 fold trước
        engine.handleAction(P2, roundId, BetAction.FOLD, null);
        // Xoay về P2 lượt (giả lập thủ công)
        state.setCurrentTurnIndex(1); // index P2
        state.setPhase(GamePhase.BETTING);

        GameException ex = assertThrows(GameException.class,
            () -> engine.handleAction(P2, roundId, BetAction.CALL, null));
        assertEquals(ErrorCode.INVALID_STATE, ex.getErrorCode());
    }

    // ─── Lỗi: RAISE < mức tối thiểu ─────────────────────────────────────

    @Test
    @DisplayName("RAISE với raiseAmount <= 0 → GameException(INVALID_BET_AMOUNT)")
    void raise_lessThanMinimum_throwsInvalidBetAmount() {
        GameException ex = assertThrows(GameException.class,
            () -> engine.handleAction(P2, roundId, BetAction.RAISE, BigDecimal.ZERO));
        assertEquals(ErrorCode.INVALID_BET_AMOUNT, ex.getErrorCode());

        GameException ex2 = assertThrows(GameException.class,
            () -> engine.handleAction(P2, roundId, BetAction.RAISE, BigDecimal.valueOf(-10)));
        assertEquals(ErrorCode.INVALID_BET_AMOUNT, ex2.getErrorCode());
    }

    // ─── Lỗi: RAISE vượt balance ──────────────────────────────────────────

    @Test
    @DisplayName("RAISE vượt balance → GameException(INVALID_BET_AMOUNT), state không đổi")
    void raise_moreThanBalance_throwsInvalidBetAmount() {
        // P2 balance = 1000-100 = 900, cần raise > 900 để fail
        BigDecimal potBefore = state.getMainPot();
        BigDecimal balanceBefore = player(P2).getBalance();

        GameException ex = assertThrows(GameException.class,
            () -> engine.handleAction(P2, roundId, BetAction.RAISE, BigDecimal.valueOf(10000)));
        assertEquals(ErrorCode.INVALID_BET_AMOUNT, ex.getErrorCode());
        // State không đổi
        assertEquals(0, potBefore.compareTo(state.getMainPot()));
        assertEquals(0, balanceBefore.compareTo(player(P2).getBalance()));
    }

    // ─── Kịch bản kết thúc vòng tố ────────────────────────────────────────

    @Test
    @DisplayName("Chỉ 1 người còn lại chưa fold → phase chuyển sang FINISHED")
    void onePlayerLeft_phaseBecomesFinished() throws GameException {
        // Tạo ván 2 người (đơn giản nhất: 1 fold → còn 1 → FINISHED)
        String id2 = UUID.randomUUID().toString();
        GameState state2 = buildState(id2, ANTE, P1, P2);
        BettingEngine engine2 = new BettingEngine(state2);
        state2.setCurrentTurnIndex(1); // Lượt P2

        // P2 fold → chỉ còn P1 → FINISHED
        engine2.handleAction(P2, id2, BetAction.FOLD, null);

        assertEquals(GamePhase.FINISHED, state2.getPhase(), "Chỉ 1 người còn thì ván FINISHED ngay");
    }

    @Test
    @DisplayName("Tất cả CALL bằng nhau → phase chuyển sang SHOWDOWN")
    void allCallEqual_phaseBecomesShowdown() throws GameException {
        // P2 RAISE 50 → highBet = 150
        engine.handleAction(P2, roundId, BetAction.RAISE, BigDecimal.valueOf(50));
        // P3 CALL 150 (bỏ thêm 50)
        engine.handleAction(P3, roundId, BetAction.CALL, null);
        // P1 CALL 150 (bỏ thêm 50) → Tất cả đều ở mức 150 → SHOWDOWN
        engine.handleAction(P1, roundId, BetAction.CALL, null);

        assertEquals(GamePhase.SHOWDOWN, state.getPhase(), "Mọi người CALL bằng nhau → SHOWDOWN");
    }

    @Test
    @DisplayName("Exception không làm thay đổi state (atomicity guarantee)")
    void action_doesNotChangeStateOnException() {
        BigDecimal potBefore = state.getMainPot();
        BigDecimal highBetBefore = state.getCurrentHighBet();
        BigDecimal balanceBefore = player(P2).getBalance();
        long historyBefore = state.getBetHistory().size();
        long versionBefore = state.getStateVersion();

        // Action lỗi: RAISE vượt balance
        assertThrows(GameException.class,
            () -> engine.handleAction(P2, roundId, BetAction.RAISE, BigDecimal.valueOf(99999)));

        // Kiểm tra không có gì thay đổi
        assertEquals(0, potBefore.compareTo(state.getMainPot()), "Pot không được thay đổi khi lỗi");
        assertEquals(0, highBetBefore.compareTo(state.getCurrentHighBet()), "HighBet không được thay đổi");
        assertEquals(0, balanceBefore.compareTo(player(P2).getBalance()), "Balance không được thay đổi");
        assertEquals(historyBefore, state.getBetHistory().size(), "History không được thay đổi");
        assertEquals(versionBefore, state.getStateVersion(), "StateVersion không được thay đổi");
    }
}
