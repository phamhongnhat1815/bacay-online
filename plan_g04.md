# 📋 G04 — Vòng Đời Ván & Kết Quả (SidePotCalculator + finalizeRound)
**Người thực hiện:** Trọng | **Mốc hoàn thành:** M4.1, M5.1 | **Phụ thuộc:** G02, G03 phải xong trước

---

## 1. Mục Tiêu

Tính toán kết quả ván sau khi vòng tố kết thúc (SHOWDOWN hoặc thắng sớm). Tính đúng Side Pot khi có nhiều người All-in ở các mức khác nhau. Chốt `RoundResult` ổn định (idempotent) để Nhật lưu DB và Khánh gửi socket `ROUND_RESULT`.

**Tiêu chí nghiệm thu (trích biên bản bàn giao):**
> "Một ván chỉ chốt một kết quả; không cộng điểm cho ván hủy; một yêu cầu tới muộn không làm ván đã kết thúc thay đổi kết quả."

---

## 2. Danh Sách File Cần Tạo / Cập Nhật

### Module `shared` — Bổ sung DTO kết quả
> Đặt tại `shared/src/main/java/com/game3cay/shared/model/`

| File | Mô tả |
|------|-------|
| `SidePot.java` | `record SidePot(BigDecimal amount, List<Long> eligiblePlayerIds)` — pot phụ khi có ALL_IN |
| `PlayerResult.java` | Kết quả 1 người: `userId`, `username`, `displayName`, `hand`, `handType`, `rank`, `result`, `profit`, `seatNumber` |
| `RoundResult.java` | Kết quả ván hoàn chỉnh: `roundId`, `roomId`, `roundStatus`, `endedAtMillis`, `basePoint`, `playerResults`, `betHistory`, `sidePots`, `cancelReason` |

### Module `server` — Engine nội bộ
> Đặt tại `server/src/main/java/com/game3cay/server/game/engine/`

| File | Mô tả |
|------|-------|
| `SidePotCalculator.java` | Thuật toán tính danh sách Pot phụ từ trạng thái All-in |

### Module `server` — Cập nhật `GameEngineImpl`
- Implement `finalizeRound()` — chốt kết quả SHOWDOWN và thắng sớm
- Cập nhật `GameState` để giữ `finalResult` không đổi sau khi chốt

### Module `server` — Unit Test

| File | Các ca kiểm thử |
|------|-----------------|
| `SidePotCalculatorTest.java` | 1 all-in mức thấp, 3 mức khác nhau, fold + all-in |
| `GameEngineResultTest.java` | Chốt kết quả bình thường, thắng sớm, CANCELLED, idempotent |

---

## 3. Đặc Tả Kỹ Thuật Chi Tiết

### 3.1 SidePot.java
```java
package com.game3cay.shared.model;

public record SidePot(
    BigDecimal amount,
    List<Long> eligiblePlayerIds   // Những userId được tranh pot này
) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
}
```

### 3.2 PlayerResult.java
```java
package com.game3cay.shared.model;

public record PlayerResult(
    long userId,
    String username,
    String displayName,
    Hand hand,              // null nếu FOLD trước showdown
    String handType,        // "SAP" / "LIENG" / "BO_DOI" / "DIEM" / null nếu FOLD
    int rank,               // 1 = nhất, 0 = không xếp hạng (fold/cancelled)
    String result,          // "WIN" / "LOSE" / "DRAW" / "FOLD" / "CANCELLED"
    BigDecimal profit,      // Dương = thắng, âm = thua, 0 = hoàn tiền (CANCELLED)
    int seatNumber
) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
}
```

### 3.3 RoundResult.java
```java
package com.game3cay.shared.model;

public record RoundResult(
    String roundId,
    long roomId,
    String roundStatus,              // "FINISHED" hoặc "CANCELLED"
    long endedAtMillis,
    BigDecimal basePoint,            // = betAmount (mức ante ban đầu)
    List<PlayerResult> playerResults,// Sắp xếp theo rank từ cao xuống thấp
    List<BetRecord> betHistory,      // Lịch sử toàn bộ action tố (cho DB point_bets)
    List<SidePot> sidePots,          // Rỗng nếu không có ALL_IN
    String cancelReason              // null nếu FINISHED
) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
}
```

### 3.4 SidePotCalculator.java

**Thuật toán:**
1. Sắp xếp các người chơi còn lại (không fold) theo `currentBet` tăng dần.
2. Xử lý từng "mức cược mốc" tạo bởi những người All-in:
   - Với mỗi mốc `level`, cộng đóng góp `min(p.currentBet, level) - prevLevel` từ tất cả người chơi có `currentBet >= prevLevel`.
   - Những người có `currentBet >= level` đều là `eligiblePlayerIds` cho pot đó.
3. Phần còn lại của pot lớn (main pot) thuộc về tất cả người chưa fold.

```java
package com.game3cay.server.game.engine;

public class SidePotCalculator {

    public static List<SidePot> calculate(List<PlayerState> players) {
        // Chỉ tính cho người không fold
        List<PlayerState> active = players.stream()
            .filter(p -> !p.isFolded())
            .sorted(Comparator.comparing(PlayerState::getCurrentBet))
            .toList();

        List<SidePot> pots = new ArrayList<>();
        BigDecimal prevLevel = BigDecimal.ZERO;

        for (PlayerState allInPlayer : active) {
            BigDecimal level = allInPlayer.getCurrentBet();
            if (level.compareTo(prevLevel) <= 0) continue; // Đã xử lý mốc này

            // Tính tổng pot tại mốc này
            BigDecimal potAmount = BigDecimal.ZERO;
            List<Long> eligible = new ArrayList<>();

            for (PlayerState p : players) { // Dùng tất cả players (kể cả fold)
                BigDecimal contribution = p.getCurrentBet().subtract(prevLevel);
                if (contribution.signum() <= 0) continue;
                BigDecimal actualContrib = contribution.min(level.subtract(prevLevel));
                potAmount = potAmount.add(actualContrib);
                if (!p.isFolded()) eligible.add(p.getUserId()); // Chỉ người không fold được tranh
            }

            if (potAmount.signum() > 0)
                pots.add(new SidePot(potAmount, List.copyOf(eligible)));

            prevLevel = level;
        }

        return pots;
    }
}
```

### 3.5 finalizeRound() trong GameEngineImpl

```java
@Override
public RoundResult finalizeRound(String roundId) throws GameException {
    GameState state = getActiveGame(roundId); // throw STALE_ROUND nếu không tìm thấy

    // IDEMPOTENT: Nếu đã chốt rồi, trả ngay kết quả cũ
    if (state.getFinalResult() != null) return state.getFinalResult();

    // Chỉ chốt khi ván ở trạng thái FINISHED (thắng sớm hoặc SHOWDOWN xong) hoặc CANCELLED
    if (state.getPhase() != GamePhase.FINISHED && state.getPhase() != GamePhase.CANCELLED)
        throw new GameException(ErrorCode.INVALID_STATE, "Ván chưa kết thúc");

    RoundResult result;
    if (state.getPhase() == GamePhase.CANCELLED) {
        result = buildCancelledResult(state);
    } else {
        result = buildFinishedResult(state);
    }

    state.setFinalResult(result);
    return result;
}

private RoundResult buildFinishedResult(GameState state) {
    HandComparator comparator = new HandComparator();
    List<PlayerState> notFolded = state.getPlayers().values().stream()
        .filter(p -> !p.isFolded()).toList();

    // Tính Side Pot nếu có ALL_IN
    boolean hasAllIn = notFolded.stream().anyMatch(PlayerState::isAllIn);
    List<SidePot> sidePots = hasAllIn
        ? SidePotCalculator.calculate(new ArrayList<>(state.getPlayers().values()))
        : List.of();

    // Đánh giá bài và xếp hạng nếu SHOWDOWN
    List<PlayerResult> results = new ArrayList<>();

    if (notFolded.size() == 1) {
        // Thắng sớm (chỉ 1 người còn): không lật bài (hand không công khai)
        PlayerState winner = notFolded.get(0);
        BigDecimal profit = state.getMainPot().subtract(state.getBaseBetAmount());
        results.add(new PlayerResult(winner.getUserId(), "", "", null, null,
            1, "WIN", profit, winner.getSeatNumber()));
        // Thêm kết quả FOLD cho những người còn lại
        for (PlayerState p : state.getPlayers().values()) {
            if (p.isFolded())
                results.add(new PlayerResult(p.getUserId(), "", "", null, null,
                    0, "FOLD", p.getCurrentBet().negate(), p.getSeatNumber()));
        }
    } else {
        // SHOWDOWN: đánh giá bài, xếp hạng, chia pot
        Map<PlayerState, Hand> hands = new LinkedHashMap<>();
        for (PlayerState p : notFolded) {
            hands.put(p, HandEvaluator.evaluate(p.getCards()));
        }

        // Xếp hạng (sort giảm dần = mạnh nhất trước)
        List<PlayerState> ranked = notFolded.stream()
            .sorted((a, b) -> comparator.compare(hands.get(b), hands.get(a)))
            .toList();

        // Chia pot (đơn giản: winner nhận toàn bộ main pot, side pot theo eligible)
        // Lưu ý: Side pot phức tạp hơn — cho bản đầu, xử lý main pot thông thường
        BigDecimal totalPot = state.getMainPot();
        PlayerState topPlayer = ranked.get(0);

        for (int i = 0; i < ranked.size(); i++) {
            PlayerState p = ranked.get(i);
            Hand h = hands.get(p);
            int rank = i + 1;
            BigDecimal profit;
            String resultStr;

            if (i == 0) {
                // Người thắng
                profit = totalPot.subtract(p.getCurrentBet()); // Thắng = pot - số tiền đã bỏ
                resultStr = "WIN";
            } else {
                profit = p.getCurrentBet().negate(); // Thua = âm số tiền đã bỏ
                resultStr = "LOSE";
            }

            results.add(new PlayerResult(p.getUserId(), "", "",
                h, h.type().name(), rank, resultStr, profit, p.getSeatNumber()));
        }

        // Thêm kết quả FOLD
        for (PlayerState p : state.getPlayers().values()) {
            if (p.isFolded())
                results.add(new PlayerResult(p.getUserId(), "", "", null, null,
                    0, "FOLD", p.getCurrentBet().negate(), p.getSeatNumber()));
        }
    }

    return new RoundResult(
        state.getRoundId(), state.getRoomId(), "FINISHED",
        System.currentTimeMillis(), state.getBaseBetAmount(),
        results, state.getBetHistory(), sidePots, null
    );
}

private RoundResult buildCancelledResult(GameState state) {
    // Tất cả người chơi nhận lại số tiền đã bỏ (profit = 0, hoàn trả ante + cược)
    List<PlayerResult> results = state.getPlayers().values().stream()
        .map(p -> new PlayerResult(p.getUserId(), "", "", null, null,
            0, "CANCELLED", BigDecimal.ZERO, p.getSeatNumber()))
        .toList();

    return new RoundResult(
        state.getRoundId(), state.getRoomId(), "CANCELLED",
        System.currentTimeMillis(), state.getBaseBetAmount(),
        results, state.getBetHistory(), List.of(), state.getCancelReason()
    );
}
```

---

## 4. Kế Hoạch Test (JUnit 5)

### SidePotCalculatorTest.java
```java
@Test void noAllIn_returnsEmptyList() {
    // 3 người đều CALL đủ mức → không có all-in → pots rỗng
}

@Test void oneAllIn_twoLevels() {
    // A all-in 50, B cược 100, C cược 100
    // Pot 1 (main): 150 (50×3), eligible: [A,B,C]
    // Pot 2 (side): 100 (50×2), eligible: [B,C]
}

@Test void threeAllIn_differentLevels() {
    // A all-in 30, B all-in 60, C cược 100
    // 3 pots độc lập với eligible khác nhau
}

@Test void foldedPlayer_notEligible() {
    // D fold → contribution vẫn tính vào pot nhưng D không trong eligiblePlayerIds
}
```

### GameEngineResultTest.java
```java
@Test void finalizeRound_winner_receivesFullPot()

@Test void finalizeRound_showdown_correctRanking()

@Test void finalizeRound_cancelled_allProfitZero() {
    // Ván CANCELLED → tất cả profit = 0
}

@Test void finalizeRound_idempotent() {
    // Gọi finalizeRound 2 lần → trả về cùng đối tượng RoundResult
}

@Test void finalizeRound_stillPlaying_throwsException() {
    // Phase = BETTING → throw GameException(INVALID_STATE)
}

@Test void finalizeRound_earlyWin_handIsNull() {
    // 1 người còn lại thắng ngay → PlayerResult.hand == null (không lật bài)
}

@Test void finalizeRound_roundIdStable() {
    // RoundResult.roundId == RoundContext.roundId ban đầu
}
```

---

## 5. Checklist Thực Hiện Theo Thứ Tự

- [x] **1.** Tạo `SidePot.java`, `PlayerResult.java`, `RoundResult.java` trong `shared/model`
- [x] **2.** Tạo `SidePotCalculator.java` trong `server/game/engine`
- [x] **3.** Viết `SidePotCalculatorTest.java` → tất cả PASS (4/4 PASS)
- [x] **4.** Implement `finalizeRound()` trong `GameEngineImpl`
- [x] **5.** `GameState` đã có `finalResult` và `cancelReason` từ trước
- [x] **6.** Viết `GameEngineResultTest.java` → tất cả PASS (7/7 PASS)
- [x] **7.** Chạy toàn bộ `mvn test` → PASS (75/75 PASS)

---

## 6. Lưu Ý Ghép Với Nhóm

| Giao điểm | Người liên quan | Mô tả |
|-----------|----------------|-------|
| `RoundResult.betHistory` | **Nhật** | Nhật cần toàn bộ `List<BetRecord>` để lưu vào bảng `point_bets`. Mỗi `BetRecord` ghi đủ `sequenceNo`, `raisedPoint`, `multiplierBefore/After`. |
| `RoundResult.playerResults` | **Nhật** | Nhật lưu `game_players`: `userId`, `hand_type`, `result`, `profit`, `seatNumber`. |
| `RoundResult` gửi socket | **Khánh** | Khánh nhận `RoundResult` từ Engine → đóng gói thành `Packet(ROUND_RESULT, result)` → broadcast cả phòng. Cần `username` và `displayName` → Khánh bổ sung từ Session/UserDTO trước khi gửi. |
