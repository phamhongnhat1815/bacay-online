package com.game3cay.server.bus;

import com.game3cay.server.game.CompletedRound;
import com.game3cay.shared.model.GameHistoryItem;
import com.game3cay.shared.model.LeaderboardItem;
import com.game3cay.shared.model.PageResult;

/**
 * Giao diện dịch vụ nghiệp vụ ván chơi, lịch sử đấu và bảng xếp hạng.
 */
public interface GameBUS {

    /**
     * Lưu kết quả ván chơi vào cơ sở dữ liệu theo mô hình ACID transaction.
     * <p>
     * Phương thức này có tính chất Idempotent: nếu gọi lại cùng một roundId với cùng nội dung,
     * hệ thống sẽ kiểm tra và bỏ qua không ghi trùng lặp.
     *
     * @param round ván chơi đã hoàn thành cần lưu
     * @throws BUSException nếu lỗi ghi nhận giao dịch hoặc bất nhất dữ liệu
     */
    void saveCompleted(CompletedRound round) throws BUSException;

    /**
     * Lấy danh sách lịch sử ván bài của người chơi theo phân trang.
     *
     * @param userId   mã ID người chơi
     * @param page     số trang (bắt đầu từ 0)
     * @param pageSize số phần tử trên mỗi trang (1..100)
     * @return {@link PageResult} danh sách lịch sử đấu
     * @throws BUSException nếu lỗi truy vấn hoặc tham số không hợp lệ
     */
    PageResult<GameHistoryItem> getHistory(long userId, int page, int pageSize) throws BUSException;

    /**
     * Lấy bảng xếp hạng người chơi toàn server theo tổng điểm thắng.
     *
     * @param page     số trang (bắt đầu từ 0)
     * @param pageSize số phần tử trên mỗi trang (1..100)
     * @return {@link PageResult} danh sách xếp hạng
     * @throws BUSException nếu lỗi truy vấn hoặc tham số không hợp lệ
     */
    PageResult<LeaderboardItem> getLeaderboard(int page, int pageSize) throws BUSException;
}
