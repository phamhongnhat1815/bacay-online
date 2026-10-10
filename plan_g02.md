# 📋 G02 — Khởi Tạo & Dữ Liệu Ván
**Người thực hiện:** Trọng | **Mốc hoàn thành:** M4.1 | **Phụ thuộc:** G01 (đã xong)

---

## 1. Mục Tiêu

Nhận `RoundContext` + `dealerIndex` từ Khánh → khởi tạo ván mới trong RAM, chia bài, trừ ante, xác định lượt chơi đầu tiên.

**Tiêu chí nghiệm thu (trích biên bản bàn giao):**
> "Có thể tạo ván bằng chương trình kiểm thử; dữ liệu hợp lệ, không bị lẫn với ván trước."

---

## 2. Danh Sách File Cần Tạo

### Module `shared` — Model dùng chung (cả Client + Server)
> Đặt tại `shared/src/main/java/com/game3cay/shared/model/`

| File | Mô tả |
|------|-------|
| `Rank.java` | Enum 13 quân bài với 2 field: `rankValue` (so độ mạnh 2..14) và `pointValue` (điểm Ba Cây: A=1, J/Q/K/10=0, 2-9=face) |
| `Suit.java` | Enum 4 chất: `CHUON(1)`, `BICH(2)`, `CO(3)`, `RO(4)` — thứ tự tăng dần Rô > Cơ > Bích > Chuồn |
| `Card.java` | `record Card(Rank rank, Suit suit) implements Comparable<Card>, Serializable` |
| `HandType.java` | `enum HandType { DIEM(0), BO_DOI(1), LIENG(2), SAP(3) }` — field `strength` để so sánh |
| `Hand.java` | `record Hand(List<Card> cards, HandType type, int score) implements Serializable` |
| `BetAction.java` | `enum BetAction { ANTE, RAISE, CALL, FOLD, ALL_IN }` |
| `GamePhase.java` | `enum GamePhase { WAITING, DEALING, BETTING, SHOWDOWN, FINISHED, CANCELLED }` |
| `RoundStatus.java` | `enum RoundStatus { WAITING, PLAYING, FINISHED, CANCELLED }` — map sang `games.status` DB |

### Module `server` — Engine nội bộ
> Đặt tại `server/src/main/java/com/game3cay/server/game/`

| File | Mô tả |
|------|-------|
| `exception/GameException.java` | `RuntimeException` bọc `ErrorCode` (dùng enum sẵn từ `shared.network.ErrorCode`) |
| `engine/Deck.java` | Khởi tạo 52 lá (13 Rank × 4 Suit), shuffle, deal |
| `model/PlayerState.java` | Trạng thái nội bộ 1 người trong ván: `userId`, `seatNumber`, `cards`, `balance`, `currentBet`, `folded`, `allIn` |
| `model/GameState.java` | Toàn bộ trạng thái 1 ván trong RAM |
| `GameEngine.java` | Interface API cho Khánh gọi — **chỉ khai báo, chưa impl** |
| `GameEngineImpl.java` | Triển khai `initRound()` |

### Module `server` — Unit Test
> Đặt tại `server/src/test/java/com/game3cay/server/game/`

| File | Các ca kiểm thử |
|------|-----------------|
| `DeckTest.java` | Đủ 52 lá, không trùng, deal(), remaining() |
| `GameEngineInitTest.java` | Khởi tạo 2-8 người, không trùng bài, pot đúng, lượt đi đúng, ca lỗi |

---

## 3. Đặc Tả Kỹ Thuật Chi Tiết

### 3.1 Rank.java
```java
public enum Rank {
    TWO(2, 2), THREE(3, 3), FOUR(4, 4), FIVE(5, 5), SIX(6, 6),
    SEVEN(7, 7), EIGHT(8, 8), NINE(9, 9), TEN(10, 0),
    JACK(11, 0), QUEEN(12, 0), KING(13, 0), ACE(14, 1);

    private final int rankValue;   // Để so sánh độ mạnh bài
    private final int pointValue;  // Để tính điểm Ba Cây (mod 10)

    Rank(int rankValue, int pointValue) { ... }
    public int getRankValue() { return rankValue; }
    public int getPointValue() { return pointValue; }
}
```

### 3.2 Suit.java
```java
public enum Suit {
    CHUON(1), BICH(2), CO(3), RO(4);
    // RO(4) > CO(3) > BICH(2) > CHUON(1) — compareValue càng cao càng mạnh
    private final int compareValue;
    Suit(int compareValue) { ... }
    public int getCompareValue() { return compareValue; }
}
```

### 3.3 Card.java
```java
public record Card(Rank rank, Suit suit) implements Comparable<Card>, Serializable {
    @Serial private static final long serialVersionUID = 1L;

    @Override
    public int compareTo(Card other) {
        int c = Integer.compare(this.rank.getRankValue(), other.rank.getRankValue());
        if (c != 0) return c;
        return Integer.compare(this.suit.getCompareValue(), other.suit.getCompareValue());
    }
}
```

### 3.4 GameException.java
```java
package com.game3cay.server.game.exception;

import com.game3cay.shared.network.ErrorCode;

public class GameException extends RuntimeException {
    private final ErrorCode errorCode;

    public GameException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    public ErrorCode getErrorCode() { return errorCode; }
}
```
> **Lưu ý:** Dùng trực tiếp `ErrorCode` từ `shared.network` — không tự định nghĩa mã lỗi riêng.

### 3.5 Deck.java
```java
package com.game3cay.server.game.engine;

public class Deck {
    private final List<Card> cards = new ArrayList<>();

    public Deck() {
        for (Rank r : Rank.values())
            for (Suit s : Suit.values())
                cards.add(new Card(r, s));
        // Tổng = 13 × 4 = 52 lá
    }

    public void shuffle() { Collections.shuffle(cards); }
    public Card deal() {
        if (cards.isEmpty()) throw new IllegalStateException("Bộ bài đã hết lá");
        return cards.remove(cards.size() - 1);
    }
    public List<Card> deal(int n) {
        if (n > cards.size()) throw new IllegalStateException("Không đủ lá bài");
        List<Card> dealt = new ArrayList<>(n);
        for (int i = 0; i < n; i++) dealt.add(deal());
        return dealt;
    }
    public int remaining() { return cards.size(); }
}
```

### 3.6 PlayerState.java
```java
package com.game3cay.server.game.model;

public class PlayerState {
    private final long userId;
    private final int seatNumber;              // Vị trí ghế (1-based, theo thứ tự playerIds)
    private final List<Card> cards;            // 3 lá bài bí mật
    private BigDecimal balance;                // Số dư hiện tại của người chơi
    private BigDecimal currentBet;             // Tổng đặt cược của người này trong ván
    private boolean folded;
    private boolean allIn;

    public PlayerState(long userId, int seatNumber, BigDecimal initialBalance) { ... }
    public void receiveCards(List<Card> dealtCards) { ... }
    public void deductBet(BigDecimal amount) {
        // Trừ tiền từ balance vào currentBet
        // Nếu amount >= balance → set allIn = true, amount = balance
        // Ném GameException(INVALID_BET_AMOUNT) nếu balance <= 0
    }
    // Các getter...
}
```

### 3.7 GameState.java
```java
package com.game3cay.server.game.model;

public class GameState {
    private final String roundId;
    private final long roomId;
    private final BigDecimal baseBetAmount;         // = RoundContext.betAmount (ante)
    private final List<Long> playerOrder;           // Thứ tự ngồi ghế từ RoundContext.playerIds
    private final Map<Long, PlayerState> players;   // userId → PlayerState
    private int dealerIndex;
    private int currentTurnIndex;                   // Index trong playerOrder
    private GamePhase phase;
    private BigDecimal mainPot;
    private BigDecimal currentHighBet;
    private final List<BetRecord> betHistory;       // Lịch sử mọi action, sinh betHistory cho RoundResult
    private RoundResult finalResult;                // null khi chưa chốt, set khi finalizeRound()

    public Long getCurrentTurnUserId() {
        return playerOrder.get(currentTurnIndex);
    }
    public void advanceTurn() { /* tìm người chưa fold kế tiếp */ }
    // ... các getter, setter nội bộ
}
```

### 3.8 GameEngine.java (Interface)
```java
package com.game3cay.server.game;

import com.game3cay.server.game.exception.GameException;
import com.game3cay.shared.model.*;
import java.math.BigDecimal;

public interface GameEngine {

    // === G02 ===
    /**
     * Khởi tạo ván mới. Engine trừ ante từ balance từng người, chia bài, set lượt đi.
     * @param context   RoundContext do Khánh chuẩn bị (đã có roundId UUID, roomId, playerIds, betAmount)
     * @param dealerIndex   Vị trí dealer trong context.playerIds() (0-based)
     * @return roundId (UUID)
     * @throws GameException INVALID_REQUEST nếu dealerIndex sai; INVALID_STATE nếu roundId đã tồn tại
     */
    String initRound(RoundContext context, int dealerIndex) throws GameException;

    // === G03 ===
    void handleAction(long actorId, String roundId, BetAction action, BigDecimal raiseAmount) throws GameException;

    // === G04 ===
    RoundResult finalizeRound(String roundId) throws GameException;

    // === G05 ===
    boolean handlePlayerLeft(String roundId, long userId) throws GameException;
    GameStateSnapshot getPublicSnapshot(String roundId);
    PlayerStateSnapshot getPlayerSnapshot(String roundId, long userId);
    BetStateSnapshot getBetSnapshot(String roundId);

    // === Queries ===
    boolean isRoundActive(String roundId);
    GamePhase getCurrentPhase(String roundId);
}
```

### 3.9 GameEngineImpl.initRound()
```java
@Override
public String initRound(RoundContext context, int dealerIndex) throws GameException {
    List<Long> playerIds = context.playerIds(); // List<Long> bất biến từ RoundContext

    // 1. Validate thêm
    if (dealerIndex < 0 || dealerIndex >= playerIds.size())
        throw new GameException(ErrorCode.INVALID_REQUEST, "dealerIndex ngoài phạm vi");
    if (activeGames.containsKey(context.roundId()))
        throw new GameException(ErrorCode.INVALID_STATE, "roundId đã tồn tại");

    // 2. Tạo bộ bài và xáo
    Deck deck = new Deck();
    deck.shuffle();

    // 3. Tạo GameState
    GameState state = new GameState(context.roundId(), context.roomId(), context.betAmount(), playerIds, dealerIndex);

    // 4. Chia bài + trừ ante cho từng người
    for (int i = 0; i < playerIds.size(); i++) {
        long uid = playerIds.get(i);
        // balance sẽ được Khánh truyền vào hoặc Engine tự tra — xem ghi chú bên dưới
        PlayerState ps = new PlayerState(uid, i + 1, getBalanceForPlayer(uid, context));
        ps.receiveCards(deck.deal(3));
        ps.deductBet(context.betAmount());       // Trừ ante
        state.addPlayer(uid, ps);
        state.addPot(context.betAmount());
    }

    // 5. Tính lượt đầu tiên: người kế tiếp dealer
    state.setCurrentTurnIndex((dealerIndex + 1) % playerIds.size());
    state.setPhase(GamePhase.BETTING);

    // 6. Lưu ván
    activeGames.put(context.roundId(), state);
    return context.roundId();
}
```

> **Ghi chú về balance:** Engine nhận `balance` của từng người chơi qua cách nào?
> → Theo [g01_engine_contract.md](file:///d:/Project/bacay-online/g01_engine_contract.md) Mục 9: "Engine quản lý trừ cược" — balance ban đầu cần Khánh truyền thêm qua `RoundContext` hoặc qua 1 Map trong lúc init. **Cần chốt với Khánh** cách truyền `Map<Long, BigDecimal> initialBalances` vào `initRound`.

---

## 4. Kế Hoạch Test (JUnit 5)

### DeckTest.java
```java
@Test void deckHas52Cards() { assertEquals(52, new Deck().remaining()); }

@Test void deckCardsAreUnique() {
    Deck d = new Deck();
    Set<String> seen = new HashSet<>();
    while (d.remaining() > 0) {
        Card c = d.deal();
        assertTrue(seen.add(c.rank() + "_" + c.suit()), "Trùng lá bài: " + c);
    }
}

@Test void dealReducesRemaining() {
    Deck d = new Deck(); d.shuffle();
    d.deal(); assertEquals(51, d.remaining());
}

@Test void dealNCards() {
    Deck d = new Deck(); d.shuffle();
    assertEquals(3, d.deal(3).size());
    assertEquals(49, d.remaining());
}

@Test void dealBeyondLimit_throwsException() {
    Deck d = new Deck(); d.deal(52);
    assertThrows(IllegalStateException.class, d::deal);
}
```

### GameEngineInitTest.java
```java
@Test void initRound_2players_validState() {
    // Cho 2 người, betAmount=100
    // Kiểm tra: pot = 200, mỗi người có đúng 3 lá
}

@Test void initRound_8players_allCardsUnique() {
    // 8 người → 24 lá chia, không trùng lá nào
    // Kiểm tra: Set<Card>.size() == 24
}

@Test void initRound_firstTurn_isNextAfterDealer() {
    // dealerIndex = 0, playerIds = [A, B, C]
    // Kỳ vọng: currentTurnUserId == B (index 1)
}

@Test void initRound_roundIdUnique_twoRounds() {
    // Khởi tạo 2 ván khác roundId → không lẫn dữ liệu
}

@Test void initRound_invalidDealerIndex_throwsException() {
    // dealerIndex = -1 hoặc >= playerIds.size() → GameException(INVALID_REQUEST)
}

@Test void initRound_duplicateRoundId_throwsException() {
    // Gọi lần 2 với cùng roundId → GameException(INVALID_STATE)
}

@Test void initRound_ante_deductedFromBalance() {
    // balance ban đầu = 500, betAmount = 100
    // Sau initRound → balance = 400, currentBet = 100
}
```

---

## 5. Checklist Thực Hiện Theo Thứ Tự

- [x] **1.** Tạo `Rank.java`, `Suit.java`, `Card.java` trong `shared/model`
- [x] **2.** Tạo `HandType.java`, `Hand.java`, `BetAction.java`, `GamePhase.java`, `RoundStatus.java` trong `shared/model`
- [x] **3.** Tạo `GameException.java` trong `server/game/exception`
- [x] **4.** Tạo `Deck.java` trong `server/game/engine`
- [x] **5.** Viết `DeckTest.java` → chạy `mvn test -pl server` → tất cả PASS (9/9 PASS)
- [x] **6.** Tạo `PlayerState.java` trong `server/game/model`
- [x] **7.** Tạo `GameState.java` trong `server/game/model`
- [x] **8.** Tạo interface `GameEngine.java` trong `server/game`
- [x] **9.** Tạo `GameEngineImpl.java` — implement `initRound()`
- [x] **10.** Viết `GameEngineInitTest.java` → chạy `mvn test -pl server` → tất cả PASS (10/10 PASS)
- [x] **11.** Chốt với Khánh về cách truyền `initialBalances` vào Engine (đã tích hợp vào `RoundContext` kèm fallback)

---

## 6. Điểm Cần Chốt Với Nhóm Trước Khi Bắt Đầu Code

| Câu hỏi | Người cần xác nhận |
|---------|-------------------|
| Balance ban đầu của người chơi truyền vào Engine như thế nào? (`Map<Long, BigDecimal>` hay field bổ sung trong `RoundContext`?) | **Khánh + Nhật** |
| Server/Khánh sinh `roundId` (UUID) trước khi gọi `initRound` không? (Theo contract: CÓ) | Khánh xác nhận |
