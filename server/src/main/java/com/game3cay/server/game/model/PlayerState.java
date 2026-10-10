package com.game3cay.server.game.model;

import com.game3cay.server.game.exception.GameException;
import com.game3cay.shared.model.Card;
import com.game3cay.shared.network.ErrorCode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Trạng thái nội bộ của 1 người chơi trong một ván Ba Cây (lưu trong RAM).
 *
 * <p><b>Không Serializable</b> — class này chỉ tồn tại trong Game Engine,
 * không được gửi qua mạng. Dữ liệu gửi ra ngoài phải qua Snapshot/DTO trong {@code shared.model}.
 */
public class PlayerState {

    private final long userId;
    private final int seatNumber;           // Vị trí ghế 1-based theo thứ tự playerIds

    private final List<Card> cards;         // 3 lá bài bí mật
    private BigDecimal balance;             // Số dư hiện tại
    private BigDecimal currentBet;          // Tổng tiền đã bỏ vào bàn trong ván này
    private boolean folded;
    private boolean allIn;

    /**
     * @param userId         ID người chơi (long, khớp MySQL users.id)
     * @param seatNumber     Vị trí ghế (1-based)
     * @param initialBalance Số dư ban đầu trước khi trừ ante (BigDecimal, khớp DECIMAL(15,2))
     */
    public PlayerState(long userId, int seatNumber, BigDecimal initialBalance) {
        this.userId = userId;
        this.seatNumber = seatNumber;
        this.balance = initialBalance;
        this.currentBet = BigDecimal.ZERO;
        this.cards = new ArrayList<>(3);
        this.folded = false;
        this.allIn = false;
    }

    /**
     * Nhận bài từ bộ bài sau khi được chia.
     *
     * @param dealtCards đúng 3 lá bài
     * @throws IllegalArgumentException nếu không phải 3 lá
     */
    public void receiveCards(List<Card> dealtCards) {
        if (dealtCards.size() != 3) {
            throw new IllegalArgumentException("Phải chia đúng 3 lá, nhận được: " + dealtCards.size());
        }
        this.cards.clear();
        this.cards.addAll(dealtCards);
    }

    /**
     * Trừ tiền cược từ số dư vào {@code currentBet}.
     *
     * <ul>
     *   <li>Nếu {@code amount >= balance} → tự động ALL_IN (dùng toàn bộ balance).</li>
     *   <li>Nếu balance đã = 0 → ném {@link GameException} INVALID_BET_AMOUNT.</li>
     * </ul>
     *
     * @param amount số tiền cần bỏ thêm (phần chênh lệch, không phải tổng)
     * @return số tiền thực sự đã trừ (có thể nhỏ hơn {@code amount} nếu ALL_IN)
     * @throws GameException INVALID_BET_AMOUNT nếu balance đã bằng 0
     */
    public BigDecimal deductBet(BigDecimal amount) throws GameException {
        if (balance.signum() <= 0) {
            throw new GameException(ErrorCode.INVALID_BET_AMOUNT,
                    "Người chơi " + userId + " không có số dư để cược");
        }

        BigDecimal actualAmount;
        if (amount.compareTo(balance) >= 0) {
            // Không đủ tiền → ALL_IN với toàn bộ balance
            actualAmount = balance;
            this.allIn = true;
        } else {
            actualAmount = amount;
        }

        this.balance = this.balance.subtract(actualAmount);
        this.currentBet = this.currentBet.add(actualAmount);
        return actualAmount;
    }

    // ─── Getters ────────────────────────────────────────────────────────────

    public long getUserId() { return userId; }

    public int getSeatNumber() { return seatNumber; }

    /** Bài bí mật — chỉ Engine và đúng người chơi đó được đọc. */
    public List<Card> getCards() { return Collections.unmodifiableList(cards); }

    public BigDecimal getBalance() { return balance; }

    public BigDecimal getCurrentBet() { return currentBet; }

    public boolean isFolded() { return folded; }

    public boolean isAllIn() { return allIn; }

    // ─── Setters (chỉ Engine nội bộ dùng) ───────────────────────────────────

    public void setFolded(boolean folded) { this.folded = folded; }

    public void setAllIn(boolean allIn) { this.allIn = allIn; }
}
