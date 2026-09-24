package com.bacay.server.game;

import com.bacay.shared.model.GameStateSnapshot;
import com.bacay.shared.model.PlayerStateSnapshot;
import com.bacay.shared.model.RoundResult;

import java.io.Serializable;
import java.util.List;

/**
 * Kết quả trả về sau khi {@link GameEngine} xử lý một hành động.
 *
 * <p>Handler của Khánh dùng object này để quyết định gửi packet nào:
 * <ul>
 *   <li>{@link #publicSnapshot()} → broadcast {@code GAME_UPDATED} + {@code BET_UPDATED} cho cả phòng</li>
 *   <li>{@link #playersNeedingPrivateUpdate()} → gửi {@code PLAYER_STATE} riêng cho từng userId</li>
 *   <li>{@link #roundEnded()} = true → gửi {@code ROUND_RESULT} rồi dọn trạng thái ván</li>
 * </ul>
 *
 * <p>Không bao giờ gọi {@link GameEngine} tiếp theo trong khi đang giữ lock phòng
 * và đang block trên socket I/O.
 */
public record ActionResult(
        String roundId,

        /** Snapshot công khai để broadcast GAME_UPDATED. */
        GameStateSnapshot publicSnapshot,

        /**
         * Danh sách userId cần nhận PLAYER_STATE cá nhân.
         * Thường là người vừa thực hiện action + những người bị ảnh hưởng.
         * Không bao giờ rỗng khi roundEnded = false.
         */
        List<Long> playersNeedingPrivateUpdate,

        /** true khi ván kết thúc (FINISHED hoặc CANCELLED). */
        boolean roundEnded,

        /**
         * Kết quả ván — chỉ non-null khi {@link #roundEnded()} = true.
         * Dùng để broadcast ROUND_RESULT và lưu vào DB (qua GameBUS).
         */
        RoundResult roundResult
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
