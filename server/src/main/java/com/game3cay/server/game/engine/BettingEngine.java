package com.game3cay.server.game.engine;

import com.game3cay.server.game.exception.GameException;
import com.game3cay.server.game.model.GameState;
import com.game3cay.server.game.model.PlayerState;
import com.game3cay.shared.model.BetAction;
import com.game3cay.shared.model.BetRecord;
import com.game3cay.shared.model.GamePhase;
import com.game3cay.shared.network.ErrorCode;

import java.math.BigDecimal;

/**
 * Xử lý vòng tố của một ván Ba Cây.
 *
 * <p><b>Nguyên tắc atomicity:</b> Toàn bộ validate hoàn thành trước khi bất kỳ field nào
 * trong {@link GameState} hay {@link PlayerState} bị thay đổi.
 * Nếu ném {@link GameException}, trạng thái hoàn toàn giữ nguyên.
 */
public class BettingEngine {

    private final GameState state;

    public BettingEngine(GameState state) {
        this.state = state;
    }

    /**
     * Xử lý 1 hành động tố từ người chơi.
     *
     * @param actorId     userId người thực hiện
     * @param roundId     ID ván (để kiểm tra chéo)
     * @param action      RAISE / CALL / FOLD / ALL_IN
     * @param raiseAmount Số tiền tố thêm (chỉ có nghĩa khi RAISE, null với action khác)
     * @throws GameException xem {@link ErrorCode} phù hợp với từng trường hợp
     */
    public void handleAction(long actorId, String roundId, BetAction action, BigDecimal raiseAmount)
            throws GameException {

        // ─── BƯỚC 1: Validate toàn bộ trước khi đổi state ─────────────────

        if (!state.getRoundId().equals(roundId)) {
            throw new GameException(ErrorCode.STALE_ROUND, "roundId không khớp với ván hiện tại");
        }

        if (state.getPhase() != GamePhase.BETTING) {
            throw new GameException(ErrorCode.INVALID_STATE,
                    "Không phải giai đoạn tố, phase hiện tại: " + state.getPhase());
        }

        PlayerState actor = state.getPlayers().get(actorId);
        if (actor == null) {
            throw new GameException(ErrorCode.FORBIDDEN, "Người chơi " + actorId + " không có trong ván");
        }

        Long currentTurn = state.getCurrentTurnUserId();
        if (currentTurn == null || !currentTurn.equals(actorId)) {
            throw new GameException(ErrorCode.NOT_YOUR_TURN,
                    "Chưa đến lượt của người chơi " + actorId + ", hiện tại: " + currentTurn);
        }

        if (actor.isFolded()) {
            throw new GameException(ErrorCode.INVALID_STATE, "Người chơi " + actorId + " đã úp bài");
        }

        // Validate theo từng loại action
        switch (action) {
            case RAISE  -> validateRaise(actor, raiseAmount);
            case CALL   -> validateCall(actor);
            case FOLD   -> {} // FOLD luôn hợp lệ nếu qua được kiểm tra trên
            case ALL_IN -> {} // ALL_IN luôn hợp lệ (còn tiền hay không cũng đều được)
            default     -> throw new GameException(ErrorCode.INVALID_REQUEST, "Action không hợp lệ: " + action);
        }

        // ─── BƯỚC 2: Apply thay đổi (chỉ bắt đầu đổi state TỪ ĐÂY) ───────

        applyAction(actor, actorId, action, raiseAmount);
    }

    // ─── Validate từng loại action ─────────────────────────────────────────

    private void validateRaise(PlayerState actor, BigDecimal raiseAmount) throws GameException {
        if (raiseAmount == null || raiseAmount.signum() <= 0) {
            throw new GameException(ErrorCode.INVALID_BET_AMOUNT, "Mức tố RAISE phải > 0");
        }
        // Số tiền cần thêm = (currentHighBet + raiseAmount) - actor.currentBet
        BigDecimal newHigh = state.getCurrentHighBet().add(raiseAmount);
        BigDecimal needed  = newHigh.subtract(actor.getCurrentBet());
        if (needed.compareTo(actor.getBalance()) > 0) {
            throw new GameException(ErrorCode.INVALID_BET_AMOUNT,
                    "Không đủ số dư để RAISE " + raiseAmount + " (cần thêm " + needed + ")");
        }
    }

    private void validateCall(PlayerState actor) throws GameException {
        BigDecimal needed = state.getCurrentHighBet().subtract(actor.getCurrentBet());
        if (needed.compareTo(BigDecimal.ZERO) <= 0) {
            throw new GameException(ErrorCode.INVALID_STATE,
                    "Không cần CALL khi đã đóng đủ mức cược cao nhất");
        }
    }

    // ─── Apply state ───────────────────────────────────────────────────────

    private void applyAction(PlayerState actor, long actorId, BetAction action, BigDecimal raiseAmount) {
        BigDecimal amountPaid = BigDecimal.ZERO;
        int seqNo = state.getBetHistory().size() + 1;

        switch (action) {
            case RAISE -> {
                BigDecimal newHigh = state.getCurrentHighBet().add(raiseAmount);
                amountPaid = newHigh.subtract(actor.getCurrentBet());
                actor.deductBet(amountPaid);
                state.addPot(amountPaid);
                state.setCurrentHighBet(newHigh);
            }
            case CALL -> {
                amountPaid = state.getCurrentHighBet().subtract(actor.getCurrentBet());
                if (amountPaid.compareTo(actor.getBalance()) >= 0) {
                    // Không đủ tiền call đủ → chuyển ALL_IN tự động
                    amountPaid = actor.getBalance();
                    actor.deductBet(amountPaid);
                    // deductBet đã set allIn = true khi balance = 0
                } else {
                    actor.deductBet(amountPaid);
                }
                state.addPot(amountPaid);
            }
            case FOLD -> actor.setFolded(true);
            case ALL_IN -> {
                amountPaid = actor.getBalance();
                BigDecimal newBetAfterAllIn = actor.getCurrentBet().add(amountPaid);
                if (newBetAfterAllIn.compareTo(state.getCurrentHighBet()) > 0) {
                    state.setCurrentHighBet(newBetAfterAllIn);
                }
                actor.deductBet(amountPaid); // deductBet tự set allIn = true khi balance về 0
                state.addPot(amountPaid);
            }
        }

        // Ghi lịch sử action vào ván
        state.addBetRecord(new BetRecord(
            actorId,
            null,                    // targetUserId = null (Ba Cây không tố nhắm người cụ thể)
            action,
            actor.getCurrentBet(),   // raisedPoint = mức cược tích lũy sau action
            amountPaid,              // raiseAmount = số tiền thực bỏ thêm
            BigDecimal.ONE,          // multiplierBefore (mặc định)
            BigDecimal.ONE,          // multiplierAfter  (mặc định)
            seqNo,
            System.currentTimeMillis()
        ));

        // Tăng stateVersion sau mỗi action thành công
        state.incrementAndGetStateVersion();

        // Kiểm tra kết thúc vòng tố
        checkBettingRoundComplete();
    }

    // ─── Kiểm tra kết thúc vòng tố ────────────────────────────────────────

    private void checkBettingRoundComplete() {
        long notFolded = state.getPlayers().values().stream()
            .filter(p -> !p.isFolded())
            .count();

        if (notFolded == 1) {
            // Chỉ còn 1 người chưa fold → thắng ngay, không cần lật bài
            state.setPhase(GamePhase.FINISHED);
        } else if (isBettingEqual()) {
            // Tất cả người chưa fold đều đã đóng đủ mức cược → chuyển SHOWDOWN
            state.setPhase(GamePhase.SHOWDOWN);
        } else {
            // Còn lượt cần xử lý → chuyển sang người tiếp theo
            state.advanceTurn();
        }
    }

    /**
     * Trả về true khi tất cả người chưa fold đều đóng đủ mức cược cao nhất
     * (hoặc đã ALL_IN).
     */
    private boolean isBettingEqual() {
        return state.getPlayers().values().stream()
            .filter(p -> !p.isFolded())
            .allMatch(p -> p.isAllIn()
                || p.getCurrentBet().compareTo(state.getCurrentHighBet()) == 0);
    }
}
