package com.bacay.shared.model;

/**
 * Hành động đặt cược trong vòng tự.
 * Dùng trong {@code GameActionRequest} (client gửi) và {@code BetStateSnapshot} (server phát).
 */
public enum BetAction {
    /** Tăng mức cược lên cao hơn mức hiện tại. */
    RAISE,
    /** Theo mức cược cao nhất hiện tại. */
    CALL,
    /** Bỏ bài, không theo. Mất tiền đã đặt vào pot. */
    FOLD,
    /** Đặt tất cả điểm còn lại; có thể thấp hơn mức cược hiện tại → side pot. */
    ALL_IN,
    /**
     * Đồng ý kết thúc vòng tự (dùng khi luật cho phép dừng sớm).
     * Trọng xác nhận khi nào dùng ACCEPT trong {@code Game_Rules.md}.
     */
    ACCEPT
}
