package com.game3cay.server.game.model;

import com.game3cay.shared.model.BetRecord;
import com.game3cay.shared.model.GamePhase;
import com.game3cay.shared.model.RoundResult;

import java.math.BigDecimal;
import java.util.*;

/**
 * Toàn bộ trạng thái nội bộ của 1 ván Ba Cây trong RAM.
 *
 * <p><b>Không Serializable</b> — chỉ tồn tại trong Game Engine.
 * Bất kỳ dữ liệu nào gửi ra ngoài đều phải qua Snapshot/DTO trong {@code shared.model}.
 *
 * <p>Tuần tự hóa (serialization đồng thời) là trách nhiệm của Khánh (GameHandler):
 * Engine giả định mỗi ván chỉ có 1 luồng thao tác tại 1 thời điểm.
 */
public class GameState {

    private final String roundId;
    private final long roomId;
    private final BigDecimal baseBetAmount;         // Mức ante ban đầu (= RoundContext.betAmount)
    private final List<Long> playerOrder;           // Thứ tự ghế bất biến (từ RoundContext.playerIds)
    private final Map<Long, PlayerState> players;   // userId → PlayerState

    private int dealerIndex;                        // Index trong playerOrder
    private int currentTurnIndex;                   // Index người đang đến lượt

    private GamePhase phase;
    private BigDecimal mainPot;                     // Tổng tiền trong bàn
    private BigDecimal currentHighBet;              // Mức cược cao nhất hiện tại

    private final List<BetRecord> betHistory;       // Lịch sử mọi action (để Nhật lưu DB)
    private RoundResult finalResult;                // null khi ván chưa kết thúc
    private String cancelReason;

    private long stateVersion;                      // Tăng sau mỗi action — client bỏ qua bản cũ hơn

    public GameState(String roundId, long roomId, BigDecimal baseBetAmount,
                     List<Long> playerOrder, int dealerIndex) {
        this.roundId = roundId;
        this.roomId = roomId;
        this.baseBetAmount = baseBetAmount;
        this.playerOrder = List.copyOf(playerOrder);
        this.dealerIndex = dealerIndex;
        this.players = new LinkedHashMap<>();        // Giữ thứ tự insert để duyệt có thể đoán trước
        this.phase = GamePhase.DEALING;
        this.mainPot = BigDecimal.ZERO;
        this.currentHighBet = BigDecimal.ZERO;
        this.betHistory = new ArrayList<>();
        this.stateVersion = 0L;
    }

    // ─── Player management ──────────────────────────────────────────────────

    public void addPlayer(long userId, PlayerState ps) {
        players.put(userId, ps);
    }

    /** Danh sách userId chưa fold (vẫn đang chơi). */
    public List<Long> getActivePlayers() {
        return playerOrder.stream()
                .filter(uid -> {
                    PlayerState p = players.get(uid);
                    return p != null && !p.isFolded();
                })
                .toList();
    }

    // ─── Turn management ────────────────────────────────────────────────────

    /** userId của người đang đến lượt; null nếu ván đã xong. */
    public Long getCurrentTurnUserId() {
        if (phase != GamePhase.BETTING) return null;
        return playerOrder.get(currentTurnIndex);
    }

    /**
     * Chuyển lượt sang người tiếp theo chưa fold và chưa all-in.
     * Nếu không còn ai cần đi nữa → không thay đổi currentTurnIndex.
     */
    public void advanceTurn() {
        int size = playerOrder.size();
        for (int i = 1; i <= size; i++) {
            int nextIdx = (currentTurnIndex + i) % size;
            Long nextUid = playerOrder.get(nextIdx);
            PlayerState next = players.get(nextUid);
            if (next != null && !next.isFolded() && !next.isAllIn()) {
                currentTurnIndex = nextIdx;
                return;
            }
        }
        // Không tìm được người — ván kết thúc (sẽ do BettingEngine handle)
    }

    // ─── Pot management ─────────────────────────────────────────────────────

    public void addPot(BigDecimal amount) {
        this.mainPot = this.mainPot.add(amount);
    }

    // ─── Bet history ────────────────────────────────────────────────────────

    public void addBetRecord(BetRecord record) {
        betHistory.add(record);
    }

    // ─── State version ──────────────────────────────────────────────────────

    /** Tăng version và trả về giá trị mới — gọi sau mỗi action. */
    public long incrementAndGetStateVersion() {
        return ++stateVersion;
    }

    // ─── Getters & Setters ──────────────────────────────────────────────────

    public String getRoundId() { return roundId; }
    public long getRoomId() { return roomId; }
    public BigDecimal getBaseBetAmount() { return baseBetAmount; }
    public List<Long> getPlayerOrder() { return playerOrder; }
    public Map<Long, PlayerState> getPlayers() { return players; }
    public int getDealerIndex() { return dealerIndex; }
    public int getCurrentTurnIndex() { return currentTurnIndex; }
    public GamePhase getPhase() { return phase; }
    public BigDecimal getMainPot() { return mainPot; }
    public BigDecimal getCurrentHighBet() { return currentHighBet; }
    public List<BetRecord> getBetHistory() { return Collections.unmodifiableList(betHistory); }
    public RoundResult getFinalResult() { return finalResult; }
    public String getCancelReason() { return cancelReason; }
    public long getStateVersion() { return stateVersion; }

    public void setCurrentTurnIndex(int idx) { this.currentTurnIndex = idx; }
    public void setPhase(GamePhase phase) { this.phase = phase; }
    public void setCurrentHighBet(BigDecimal currentHighBet) { this.currentHighBet = currentHighBet; }
    public void setFinalResult(RoundResult finalResult) { this.finalResult = finalResult; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
}
