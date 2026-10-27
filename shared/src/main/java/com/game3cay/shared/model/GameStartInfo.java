package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * Thông tin bắt đầu ván chơi thông báo công khai cho toàn bàn.
 *
 * @param roomId   ID bàn chơi
 * @param roundId  mã UUID của ván chơi
 * @param roundNo  số thứ tự ván trong phiên
 */
public record GameStartInfo(
        long roomId,
        String roundId,
        int roundNo
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
