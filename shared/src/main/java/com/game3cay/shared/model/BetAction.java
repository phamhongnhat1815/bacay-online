package com.game3cay.shared.model;

/**
 * Các loại hành động đặt cược trong một ván chơi Ba Cây.
 */
public enum BetAction {
    /** Tiền cược sàn (tiền gà) bắt buộc nộp khi bắt đầu ván. */
    ANTE,

    /** Tố mức cược lên mức cao hơn (đưa tổng cược của mình lên mức chỉ định). */
    RAISE,

    /** Theo mức cược cao nhất hiện tại trên bàn (hoặc kiểm tra nếu đã đủ). */
    CALL,

    /** Úp bài, bỏ quyền tiếp tục so bài và chấp nhận mất phần điểm đã cược. */
    FOLD,

    /** Cược toàn bộ số điểm còn lại của người chơi. */
    ALL_IN,

    /** Chấp nhận thách cược (mở rộng trong các biến thể). */
    ACCEPT
}
