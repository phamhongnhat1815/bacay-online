package com.game3cay.server.game;

import com.game3cay.server.game.exception.GameException;
import com.game3cay.shared.model.BetAction;
import com.game3cay.shared.model.BetStateSnapshot;
import com.game3cay.shared.model.GamePhase;
import com.game3cay.shared.model.GameStateSnapshot;
import com.game3cay.shared.model.PlayerStateSnapshot;
import com.game3cay.shared.model.RoundResult;

import java.math.BigDecimal;

/**
 * Hợp đồng giao tiếp giữa Server (Khánh - GameHandler) và Game Engine (Trọng).
 *
 * <p>Theo đúng hợp đồng đã chốt tại {@code g01_engine_contract.md}.
 */
public interface GameEngine {

    // ═══ VÒNG ĐỜI VÁN (LIFECYCLE) ═══

    /**
     * Khởi tạo và bắt đầu ván mới.
     * Trừ cược ban đầu (ante), chia bài (3 lá bí mật), xác định lượt đi đầu tiên.
     *
     * @param context     Dữ liệu khởi tạo (roomId, playerIds, betAmount, roundId)
     * @param dealerIndex Vị trí người cầm cái trong danh sách playerIds (0-based)
     * @return ID duy nhất của ván mới (khớp context.roundId)
     * @throws GameException INVALID_REQUEST nếu dealerIndex ngoài phạm vi;
     *                       INVALID_STATE nếu ván đã tồn tại;
     *                       INVALID_BET_AMOUNT nếu người chơi không đủ số dư ante
     */
    String initRound(RoundContext context, int dealerIndex) throws GameException;

    /**
     * Xử lý hành động cược/tố của người chơi trong lượt.
     *
     * @param actorId     ID người chơi thực hiện hành động
     * @param roundId     ID ván
     * @param action      Hành động: RAISE, CALL, FOLD, ALL_IN
     * @param raiseAmount Số tiền muốn tố thêm (chỉ dùng khi action == RAISE, null với các action khác)
     * @throws GameException NOT_YOUR_TURN, INVALID_BET_AMOUNT, INVALID_ACTION, STALE_ROUND
     */
    void handleAction(long actorId, String roundId, BetAction action, BigDecimal raiseAmount) throws GameException;

    /**
     * Kết thúc ván, so bài, chia pot, tổng hợp kết quả để lưu DB và gửi client.
     *
     * @param roundId ID ván
     * @return Kết quả hoàn chỉnh của ván (RoundResult)
     * @throws GameException INVALID_STATE, STALE_ROUND
     */
    RoundResult finalizeRound(String roundId) throws GameException;

    /**
     * Xử lý khi người chơi rời bàn hoặc mất kết nối giữa ván.
     * Tương đương FOLD tự động đối với người chơi này.
     *
     * @param roundId ID ván
     * @param userId  ID người chơi rời bàn
     * @return true = ván còn đủ >= 2 người tiếp tục; false = ván bị hủy (< 2 người)
     * @throws GameException STALE_ROUND
     */
    boolean handlePlayerLeft(String roundId, long userId) throws GameException;

    // ═══ QUERIES & SNAPSHOTS (Gửi Socket) ═══

    /** Trạng thái công khai — broadcast cả phòng (S→C room: GAME_UPDATED). KHÔNG chứa bài bí mật. */
    GameStateSnapshot getPublicSnapshot(String roundId) throws GameException;

    /** Trạng thái riêng (bài tẩy + số dư) — CHỈ gửi riêng cho người chơi tương ứng (S→C*: PLAYER_STATE). */
    PlayerStateSnapshot getPlayerSnapshot(String roundId, long userId) throws GameException;

    /** Trạng thái đặt cược (pot, lượt hiện tại, mức cược cao nhất) — broadcast (S→C room: BET_UPDATED). */
    BetStateSnapshot getBetSnapshot(String roundId) throws GameException;

    // ═══ STATE CHECK ═══

    boolean isRoundActive(String roundId);
    GamePhase getCurrentPhase(String roundId);
}
