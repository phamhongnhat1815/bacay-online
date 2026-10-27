package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Chi tiết toàn diện ván bài thời gian thực của một bàn chơi dành cho Quản trị viên (God-View).
 *
 * @param roomId                 mã số bàn (1 - 20)
 * @param roomName               tên bàn
 * @param status                 trạng thái bàn ("WAITING", "PLAYING", "RESULT")
 * @param ante                   mức cược sàn cơ bản
 * @param currentPot             tổng quỹ pot đang có trên bàn
 * @param roundId                mã ván chơi hiện tại (hoặc null nếu đang chờ)
 * @param currentTurnUserId      ID của người chơi đang đến lượt tố (hoặc null)
 * @param currentTurnDisplayName tên người chơi đang đến lượt
 * @param stage                  vòng cược hiện tại
 * @param playerCount            số lượng người đang trong bàn
 * @param maxPlayers             số ghế tối đa
 * @param players                danh sách bài và cược của từng người chơi
 * @param recentChats            nhật ký chat và thông báo gần nhất của bàn
 */
public record AdminLiveMatchDetail(
        long roomId,
        String roomName,
        String status,
        BigDecimal ante,
        BigDecimal currentPot,
        String roundId,
        Long currentTurnUserId,
        String currentTurnDisplayName,
        int stage,
        int playerCount,
        int maxPlayers,
        List<AdminLivePlayerHand> players,
        List<ChatMessage> recentChats
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
