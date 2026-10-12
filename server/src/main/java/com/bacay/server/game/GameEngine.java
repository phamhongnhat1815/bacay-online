package com.bacay.server.game;

import com.bacay.shared.model.*;
import com.bacay.shared.network.ErrorCode;

import java.math.BigDecimal;
import java.util.List;

/**
 * API của Game Engine — do <b>Trọng</b> triển khai (module G01–G06).
 *
 * <p><b>Khánh</b> (N05) gọi các phương thức này từ {@code ClientHandler}
 * khi nhận được packet {@code START_GAME_REQUEST} và {@code GAME_ACTION}.
 *
 * <h2>Vòng đời ván</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────┐
 * │ initRound()                                                      │
 * │    └─► DEALING  — server chia bài, gọi getPlayerCards() từng   │
 * │                   người, gửi DEAL_CARD riêng qua socket         │
 * │         └─► BETTING — lần lượt từng người: handleAction()      │
 * │                   RAISE / CALL / FOLD / ALL_IN                  │
 * │              ├── Tất cả còn lại đặt bằng nhau                  │
 * │              │    └─► SHOWDOWN → FINISHED (ActionResult.roundEnded=true)
 * │              └── Chỉ còn 1 người chưa FOLD                     │
 * │                   └─► FINISHED ngay (không cần so bài)         │
 * │                                                                 │
 * │ handlePlayerLeft() — bất cứ lúc nào:                           │
 * │    ├── Còn đủ người → tiếp tục, ActionResult.roundEnded=false  │
 * │    └── Không đủ người → CANCELLED (ActionResult.roundEnded=true)│
 * └─────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Quy tắc thread-safety</h2>
 * <ul>
 *   <li>Mọi lời gọi đến Engine cho cùng một {@code roundId} phải <b>tuần tự hóa</b>.</li>
 *   <li>Khánh dùng single-thread executor hoặc synchronized block per-room.</li>
 *   <li>Engine KHÔNG cần tự lock — Khánh chịu trách nhiệm điều phối.</li>
 * </ul>
 */
public interface GameEngine {

    // -------------------------------------------------------------------------
    // Khởi tạo ván
    // -------------------------------------------------------------------------

    /**
     * Khởi tạo ván mới, chia bài, tính ante.
     *
     * @param roundId   UUID do Khánh tạo trước khi gọi (để lưu DB)
     * @param roomId    id phòng (dùng để log/debug)
     * @param playerIds danh sách userId theo thứ tự ngồi (2–8 người)
     * @param betAmount mức ante (trừ vào balance ngay khi gọi)
     * @return snapshot công khai phase DEALING để broadcast
     * @throws GameEngineException {@link ErrorCode#NOT_ENOUGH_PLAYERS} nếu < 2 người
     */
    GameStateSnapshot initRound(String roundId, long roomId,
                                List<Long> playerIds, BigDecimal betAmount)
            throws GameEngineException;

    // -------------------------------------------------------------------------
    // Lấy dữ liệu riêng
    // -------------------------------------------------------------------------

    /**
     * Lấy 3 lá bài của một người sau khi đã gọi {@link #initRound}.
     * Khánh gọi ngay sau {@link #initRound} cho từng userId và gửi DEAL_CARD riêng.
     *
     * @throws GameEngineException {@link ErrorCode#NOT_IN_ROOM} nếu userId không thuộc ván
     */
    List<Card> getPlayerCards(String roundId, long userId) throws GameEngineException;

    /**
     * Lấy snapshot riêng (bài + số dư + trạng thái cược) của một người.
     * Khánh gọi sau mỗi action và gửi PLAYER_STATE riêng.
     *
     * @throws GameEngineException {@link ErrorCode#NOT_IN_ROOM} nếu userId không thuộc ván
     */
    PlayerStateSnapshot getPlayerSnapshot(String roundId, long userId) throws GameEngineException;

    // -------------------------------------------------------------------------
    // Xử lý hành động
    // -------------------------------------------------------------------------

    /**
     * Xử lý một hành động đặt cược của người chơi.
     *
     * @param action       RAISE / CALL / FOLD / ALL_IN
     * @param raiseAmount  chỉ có ý nghĩa khi {@code action = RAISE}; null nếu khác
     * @return {@link ActionResult} chứa snapshot mới và chỉ dẫn gửi packet
     * @throws GameEngineException mã lỗi tương ứng:
     *         {@link ErrorCode#NOT_YOUR_TURN}, {@link ErrorCode#STALE_ROUND},
     *         {@link ErrorCode#INVALID_BET_AMOUNT}, {@link ErrorCode#INVALID_STATE}
     */
    ActionResult handleAction(String roundId, long userId,
                              BetAction action, BigDecimal raiseAmount)
            throws GameEngineException;

    /**
     * Xử lý khi một người rời phòng hoặc mất kết nối giữa ván.
     * Nếu không đủ người tối thiểu → Engine tự chuyển ván sang CANCELLED.
     *
     * @return {@link ActionResult}; nếu {@code roundEnded = true} → Khánh gửi ROUND_RESULT
     */
    ActionResult handlePlayerLeft(String roundId, long userId) throws GameEngineException;

    // -------------------------------------------------------------------------
    // Truy vấn trạng thái
    // -------------------------------------------------------------------------

    /**
     * Lấy snapshot công khai ở thời điểm hiện tại (không thay đổi trạng thái).
     * Dùng để gửi cho người vừa reconnect hoặc vừa vào phòng ở trạng thái PLAYING.
     */
    GameStateSnapshot getPublicSnapshot(String roundId) throws GameEngineException;

    /**
     * Lấy kết quả ván đã kết thúc.
     * Khánh gọi sau khi {@link ActionResult#roundEnded()} = true
     * để lưu vào DB qua {@code GameBUS}.
     *
     * @throws GameEngineException {@link ErrorCode#INVALID_STATE} nếu ván chưa kết thúc
     */
    RoundResult getRoundResult(String roundId) throws GameEngineException;

    /**
     * Kiểm tra ván còn hoạt động (DEALING hoặc BETTING).
     * Khánh dùng để validate khi nhận GAME_ACTION.
     */
    boolean isRoundActive(String roundId);

    /**
     * Giải phóng tài nguyên RAM của ván sau khi đã lưu kết quả vào DB.
     * Khánh gọi sau khi {@code GameBUS.saveResult()} thành công.
     */
    void cleanupRound(String roundId);
}
