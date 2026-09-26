package com.bacay.server.bus;

import com.bacay.shared.model.GameHistoryItem;
import com.bacay.shared.model.LeaderboardItem;
import com.bacay.shared.model.PageResult;
import com.bacay.shared.model.RoundResult;
import com.bacay.shared.network.ErrorCode;

/**
 * Hợp đồng BUS cho nghiệp vụ lưu kết quả và tra cứu dữ liệu ván chơi —
 * do <b>Nhật</b> triển khai (module D04, D05).
 *
 * <p><b>Khánh</b> (N08) gọi {@link #saveResult} sau khi nhận
 * {@link com.bacay.server.game.ActionResult#roundEnded()} = true.
 * Khánh (N08) cũng gọi {@link #getHistory} và {@link #getLeaderboard}
 * khi nhận packet {@code GET_HISTORY} / {@code GET_LEADERBOARD}.
 *
 * <h2>Quy tắc chống ghi lặp</h2>
 * <ul>
 *   <li>{@code round_id} là UNIQUE trong bảng {@code games}.</li>
 *   <li>Nếu đã tồn tại → ném {@link BUSException} với {@link ErrorCode#INVALID_STATE}
 *       (KHÔNG ghi đè, KHÔNG im lặng bỏ qua).</li>
 *   <li>Khánh log cảnh báo và bỏ qua — không gửi lỗi cho client trong trường hợp này.</li>
 * </ul>
 *
 * <h2>Tính nguyên tử</h2>
 * <p>{@link #saveResult} phải thực hiện trong một transaction:
 * ghi {@code games}, {@code game_players}, {@code point_bets}, {@code side_pots},
 * {@code transactions} và cập nhật {@code users.balance} — tất cả hoặc không gì cả.
 */
public interface GameBUS {

    /**
     * Lưu toàn bộ kết quả ván vào DB trong một transaction duy nhất.
     *
     * <p><b>Thứ tự ghi (Nhật triển khai):</b>
     * <ol>
     *   <li>INSERT INTO {@code games} (round_id UNIQUE — lỗi ngay nếu ghi lặp)</li>
     *   <li>INSERT INTO {@code game_players} — kết quả từng người</li>
     *   <li>INSERT INTO {@code game_cards} — 3 lá/người, lấy từ {@code result.playerResults[i].hand()}</li>
     *   <li>INSERT INTO {@code point_bets} — toàn bộ {@code result.betHistory()}</li>
     *   <li>INSERT INTO {@code side_pots} + {@code side_pot_players} — nếu {@code result.sidePots()} không rỗng</li>
     *   <li>INSERT INTO {@code transactions} — ghi BET (âm) rồi WIN (dương) kèm balance_before/after</li>
     *   <li>UPDATE {@code users.balance} — cộng/trừ profit</li>
     *   <li>COMMIT — hoặc ROLLBACK toàn bộ nếu bất kỳ bước nào lỗi</li>
     * </ol>
     *
     * @param result kết quả từ {@link com.bacay.server.game.GameEngine#getRoundResult}
     * @param roomId id phòng (để ghi vào {@code games.room_id})
     * @throws BUSException {@link ErrorCode#INVALID_STATE} nếu {@code round_id} đã tồn tại;
     *                      {@link ErrorCode#INTERNAL_ERROR} nếu lỗi DB
     */
    void saveResult(RoundResult result, long roomId) throws BUSException;

    /**
     * Lấy lịch sử ván của một người dùng, sắp xếp theo thời gian giảm dần.
     *
     * @param page     trang (bắt đầu 0)
     * @param pageSize số bản ghi/trang (mặc định 20, tối đa 100)
     * @throws BUSException {@link ErrorCode#INVALID_PAGE} nếu tham số không hợp lệ;
     *                      {@link ErrorCode#INTERNAL_ERROR} nếu lỗi DB
     */
    PageResult<GameHistoryItem> getHistory(long userId, int page, int pageSize) throws BUSException;

    /**
     * Lấy bảng xếp hạng toàn server.
     * Sắp xếp: {@code totalPoints DESC} → {@code totalWins DESC} → {@code userId ASC}.
     *
     * @param page     trang (bắt đầu 0)
     * @param pageSize số bản ghi/trang (mặc định 20, tối đa 100)
     * @throws BUSException {@link ErrorCode#INVALID_PAGE} nếu tham số không hợp lệ;
     *                      {@link ErrorCode#INTERNAL_ERROR} nếu lỗi DB
     */
    PageResult<LeaderboardItem> getLeaderboard(int page, int pageSize) throws BUSException;
}
