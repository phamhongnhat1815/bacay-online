# 📋 G03 — Xử Lý Hành Động Tố (BettingEngine + HandEvaluator)
**Người thực hiện:** Trọng | **Mốc hoàn thành:** M4.1 | **Phụ thuộc:** G02 phải xong trước

---

## 1. Mục Tiêu

Tiếp nhận lệnh tố đã xác thực từ Khánh (`actorId`, `roundId`, `action`, `raiseAmount`), kiểm tra toàn diện tính hợp lệ, cập nhật trạng thái ván. Song song đó, cài đặt xong `HandEvaluator` và `HandComparator` để G04 dùng khi so bài.

**Tiêu chí nghiệm thu (trích biên bản bàn giao):**
> "Hành động hợp lệ làm thay đổi đúng trạng thái; hành động sai người, sai ván hoặc sai giai đoạn bị từ chối và **không thay đổi trạng thái**."

---

## 2. Danh Sách File Cần Tạo / Cập Nhật

### Module `shared` — Bổ sung DTO snapshot (dùng từ G03 trở đi)
> Đặt tại `shared/src/main/java/com/game3cay/shared/model/`

| File | Mô tả |
|------|-------|
| `PlayerBetInfo.java` | Thông tin cược 1 người: `userId`, `displayName`, `currentBet`, `folded`, `allIn` — dùng trong `BetStateSnapshot` |
| `GameStateSnapshot.java` | Snapshot công khai sau mỗi action: `roundId`, `phase`, `currentTurnUserId`, `activePlayers`, `stateVersion` |
| `BetStateSnapshot.java` | Thông tin cược bàn: `roundId`, `currentTurnUserId`, `currentHighBet`, `mainPot`, `sidePots`, `playerBets`, `stateVersion` |
| `PlayerStateSnapshot.java` | Snapshot riêng tư: `roundId`, `myCards`, `myBalance`, `myCurrentBet`, `stateVersion` |
| `BetRecord.java` | Ghi lịch sử 1 hành động: `userId`, `targetUserId`, `action`, `raisedPoint`, `raiseAmount`, `sequenceNo`, `createdAtMillis` |

### Module `server` — Engine nội bộ
> Đặt tại `server/src/main/java/com/game3cay/server/game/engine/`

| File | Mô tả |
|------|-------|
| `HandEvaluator.java` | Xác định loại bài (SAP/LIENG/BO_DOI/DIEM) và tính điểm |
| `HandComparator.java` | So sánh 2 Hand theo thứ tự: HandType → giá trị trong loại → chất lá cao nhất |
| `BettingEngine.java` | Xử lý toàn bộ vòng tố, validate và cập nhật `GameState` |

### Module `server` — Cập nhật `GameEngineImpl`
- Implement hàm `handleAction()` trong `GameEngineImpl.java`
- Implement các hàm snapshot: `getPublicSnapshot()`, `getPlayerSnapshot()`, `getBetSnapshot()`

### Module `server` — Unit Test
> Đặt tại `server/src/test/java/com/game3cay/server/game/`

| File | Các ca kiểm thử |
|------|-----------------|
| `HandEvaluatorTest.java` | Nhận diện đúng SAP, LIENG (cả A-2-3 và Q-K-A), BO_DOI, DIEM (các mức 0-9) |
| `HandComparatorTest.java` | So bài cùng loại, khác loại, so chất khi bằng giá trị |
| `BettingEngineTest.java` | RAISE/CALL/FOLD/ALL_IN hợp lệ; tất cả ca lỗi; thắng sớm khi 1 người còn |

---

## 3. Đặc Tả Kỹ Thuật Chi Tiết

### 3.1 HandEvaluator.java

**Ưu tiên kiểm tra:** SAP → LIENG → BO_DOI → DIEM (theo Game_Rules.md)

```java
package com.game3cay.server.game.engine;

public class HandEvaluator {

    public static Hand evaluate(List<Card> cards) {
        if (isSap(cards))   return new Hand(cards, HandType.SAP,    calcSapScore(cards));
        if (isLieng(cards)) return new Hand(cards, HandType.LIENG,  calcLiengScore(cards));
        if (isBoDoi(cards)) return new Hand(cards, HandType.BO_DOI, calcBdScore(cards));
        return new Hand(cards, HandType.DIEM, calcDiem(cards));
    }

    // SAP: 3 lá cùng Rank
    static boolean isSap(List<Card> cards) {
        return cards.get(0).rank() == cards.get(1).rank()
            && cards.get(1).rank() == cards.get(2).rank();
    }

    // LIENG: 3 lá liên tiếp theo Tiến Lên
    // A-2-3 hợp lệ (ranks: 14,2,3 → sort → 2,3,14 → cặp đặc biệt)
    // Q-K-A hợp lệ (ranks: 12,13,14 → liên tiếp)
    // K-A-2 KHÔNG hợp lệ
    static boolean isLieng(List<Card> cards) {
        List<Integer> ranks = cards.stream()
            .map(c -> c.rank().getRankValue())
            .sorted()
            .toList();
        // Trường hợp thông thường: 3 lá liên tiếp
        if (ranks.get(2) - ranks.get(1) == 1 && ranks.get(1) - ranks.get(0) == 1) return true;
        // Trường hợp đặc biệt: A-2-3 (ranks sau sort: [2, 3, 14])
        if (ranks.equals(List.of(2, 3, 14))) return true;
        return false;
    }

    // BO_DOI: 3 lá ∈ {J(11), Q(12), K(13)} — đã loại SAP ở trên
    static boolean isBoDoi(List<Card> cards) {
        return cards.stream().allMatch(c ->
            c.rank() == Rank.JACK || c.rank() == Rank.QUEEN || c.rank() == Rank.KING);
    }

    // DIEM: Tổng pointValue mod 10
    static int calcDiem(List<Card> cards) {
        return cards.stream()
            .mapToInt(c -> c.rank().getPointValue())
            .sum() % 10;
    }

    // Score SAP: rankValue của 1 lá (A-A-A = 14, K-K-K = 13, ...)
    private static int calcSapScore(List<Card> cards) { return cards.get(0).rank().getRankValue(); }

    // Score LIENG: rankValue của lá cao nhất
    // Trường hợp A-2-3 → lá cao nhất là 3 (điểm = 3), Q-K-A → lá cao nhất là A (điểm = 14)
    private static int calcLiengScore(List<Card> cards) {
        List<Integer> ranks = cards.stream().map(c -> c.rank().getRankValue()).sorted().toList();
        if (ranks.equals(List.of(2, 3, 14))) return 3; // A-2-3 thấp nhất
        return ranks.get(2); // Lá cao nhất
    }

    // Score BO_DOI: rankValue lá cao nhất (J=11, Q=12, K=13)
    private static int calcBdScore(List<Card> cards) {
        return cards.stream().mapToInt(c -> c.rank().getRankValue()).max().getAsInt();
    }
}
```

### 3.2 HandComparator.java

**Thứ tự so sánh (theo Game_Rules.md Mục 3):**
1. `HandType.strength` (SAP > LIENG > BO_DOI > DIEM)
2. Score trong cùng loại bài
3. So chất: Sắp xếp 3 lá giảm dần theo rank → so chất lá cao nhất → nếu bằng nhau so lá thứ 2 → rồi lá thứ 3

```java
public class HandComparator implements Comparator<Hand> {

    @Override
    public int compare(Hand h1, Hand h2) {
        // Bước 1: So loại bài
        int typeCmp = Integer.compare(h1.type().getStrength(), h2.type().getStrength());
        if (typeCmp != 0) return typeCmp;

        // Bước 2: So giá trị trong cùng loại
        int scoreCmp = Integer.compare(h1.score(), h2.score());
        if (scoreCmp != 0) return scoreCmp;

        // Bước 3: So chất lá bài (tiebreak)
        return compareBySuit(h1.cards(), h2.cards());
    }

    // So chất: Sort 3 lá giảm dần theo Rank, rồi so chất lần lượt
    private int compareBySuit(List<Card> c1, List<Card> c2) {
        List<Card> sorted1 = c1.stream().sorted(Comparator.reverseOrder()).toList();
        List<Card> sorted2 = c2.stream().sorted(Comparator.reverseOrder()).toList();
        for (int i = 0; i < 3; i++) {
            int cmp = Integer.compare(
                sorted1.get(i).suit().getCompareValue(),
                sorted2.get(i).suit().getCompareValue()
            );
            if (cmp != 0) return cmp;
        }
        return 0; // Không bao giờ xảy ra với bộ bài 52 lá duy nhất
    }
}
```

### 3.3 BettingEngine.java

```java
package com.game3cay.server.game.engine;

public class BettingEngine {

    private final GameState state;

    public BettingEngine(GameState state) { this.state = state; }

    /**
     * Xử lý 1 hành động tố. Atomicity: nếu ném exception, state hoàn toàn không đổi.
     */
    public void handleAction(long actorId, String roundId, BetAction action, BigDecimal raiseAmount)
            throws GameException {

        // --- VALIDATE (fail-fast, không đổi state) ---
        if (!state.getRoundId().equals(roundId))
            throw new GameException(ErrorCode.STALE_ROUND, "roundId không khớp");

        if (state.getPhase() != GamePhase.BETTING)
            throw new GameException(ErrorCode.INVALID_STATE, "Không phải giai đoạn tố");

        PlayerState actor = state.getPlayers().get(actorId);
        if (actor == null)
            throw new GameException(ErrorCode.FORBIDDEN, "Người chơi không có trong ván");

        if (!state.getCurrentTurnUserId().equals(actorId))
            throw new GameException(ErrorCode.NOT_YOUR_TURN, "Chưa đến lượt của bạn");

        if (actor.isFolded())
            throw new GameException(ErrorCode.INVALID_STATE, "Người chơi đã úp bài");

        // --- VALIDATE theo từng action ---
        switch (action) {
            case RAISE -> validateRaise(actor, raiseAmount);
            case CALL  -> validateCall(actor);
            case FOLD  -> {}  // FOLD luôn hợp lệ nếu qua các kiểm tra trên
            case ALL_IN -> {}  // ALL_IN luôn hợp lệ (hết tiền)
            default    -> throw new GameException(ErrorCode.INVALID_REQUEST, "Action không hợp lệ khi tố");
        }

        // --- APPLY (chỉ đến đây mới được thay đổi state) ---
        applyAction(actor, actorId, action, raiseAmount);
    }

    private void validateRaise(PlayerState actor, BigDecimal raiseAmount) throws GameException {
        if (raiseAmount == null || raiseAmount.signum() <= 0)
            throw new GameException(ErrorCode.INVALID_BET_AMOUNT, "Mức tố phải > 0");
        // Mức cược sau RAISE = currentHighBet + raiseAmount
        BigDecimal newHighBet = state.getCurrentHighBet().add(raiseAmount);
        BigDecimal needed = newHighBet.subtract(actor.getCurrentBet()); // Số tiền cần thêm
        if (needed.compareTo(actor.getBalance()) > 0)
            throw new GameException(ErrorCode.INVALID_BET_AMOUNT, "Không đủ tiền tố (dùng ALL_IN)");
    }

    private void validateCall(PlayerState actor) throws GameException {
        BigDecimal needed = state.getCurrentHighBet().subtract(actor.getCurrentBet());
        if (needed.compareTo(BigDecimal.ZERO) <= 0)
            throw new GameException(ErrorCode.INVALID_STATE, "Không cần CALL khi đã đủ mức cược");
    }

    private void applyAction(PlayerState actor, long actorId, BetAction action, BigDecimal raiseAmount) {
        BigDecimal amountPaid = BigDecimal.ZERO;
        int seqNo = state.getBetHistory().size() + 1;

        switch (action) {
            case RAISE -> {
                BigDecimal newHigh = state.getCurrentHighBet().add(raiseAmount);
                amountPaid = newHigh.subtract(actor.getCurrentBet());
                actor.deductBet(amountPaid);
                state.addPot(amountPaid);
                state.setCurrentHighBet(newHigh);
            }
            case CALL -> {
                amountPaid = state.getCurrentHighBet().subtract(actor.getCurrentBet());
                if (amountPaid.compareTo(actor.getBalance()) >= 0) {
                    // Tự động chuyển ALL_IN nếu không đủ
                    amountPaid = actor.getBalance();
                    actor.deductBet(amountPaid);
                    actor.setAllIn(true);
                } else {
                    actor.deductBet(amountPaid);
                }
                state.addPot(amountPaid);
            }
            case FOLD  -> actor.setFolded(true);
            case ALL_IN -> {
                amountPaid = actor.getBalance();
                if (actor.getCurrentBet().add(amountPaid).compareTo(state.getCurrentHighBet()) > 0)
                    state.setCurrentHighBet(actor.getCurrentBet().add(amountPaid));
                actor.deductBet(amountPaid); // deductBet phải set allIn = true khi balance = 0
                state.addPot(amountPaid);
            }
        }

        // Ghi vào lịch sử
        state.addBetRecord(new BetRecord(actorId, null, action,
            actor.getCurrentBet(), amountPaid, BigDecimal.ONE, BigDecimal.ONE, seqNo,
            System.currentTimeMillis()));

        // Kiểm tra kết thúc vòng tố
        checkBettingRoundComplete();
    }

    private void checkBettingRoundComplete() {
        long activeCount = state.getPlayers().values().stream()
            .filter(p -> !p.isFolded() && !p.isAllIn())
            .count();
        long notFolded = state.getPlayers().values().stream()
            .filter(p -> !p.isFolded()).count();

        if (notFolded == 1) {
            // Chỉ 1 người chưa fold → thắng ngay, không lật bài
            state.setPhase(GamePhase.FINISHED);
        } else if (isBettingEqual()) {
            // Tất cả mức cược bằng nhau → chuyển sang SHOWDOWN
            state.setPhase(GamePhase.SHOWDOWN);
        } else {
            // Chuyển lượt
            state.advanceTurn();
        }
    }

    private boolean isBettingEqual() {
        return state.getPlayers().values().stream()
            .filter(p -> !p.isFolded())
            .allMatch(p -> p.isAllIn() || p.getCurrentBet().compareTo(state.getCurrentHighBet()) == 0);
    }
}
```

### 3.4 Snapshot methods trong GameEngineImpl

```java
@Override
public GameStateSnapshot getPublicSnapshot(String roundId) {
    GameState s = getActiveGame(roundId); // throw STALE_ROUND nếu không có
    List<Long> active = s.getPlayers().entrySet().stream()
        .filter(e -> !e.getValue().isFolded())
        .map(Map.Entry::getKey)
        .toList();
    return new GameStateSnapshot(
        s.getRoundId(),
        s.getPhase().name(),
        s.getCurrentTurnUserId(),
        active,
        s.getStateVersion()
    );
}

@Override
public PlayerStateSnapshot getPlayerSnapshot(String roundId, long userId) {
    GameState s = getActiveGame(roundId);
    PlayerState ps = s.getPlayers().get(userId);
    if (ps == null) throw new GameException(ErrorCode.FORBIDDEN, "Người chơi không có trong ván");
    return new PlayerStateSnapshot(
        s.getRoundId(),
        List.copyOf(ps.getCards()),   // Chỉ trả bài của ĐÚNG người này
        ps.getBalance(),
        ps.getCurrentBet(),
        s.getStateVersion()
    );
}

@Override
public BetStateSnapshot getBetSnapshot(String roundId) {
    GameState s = getActiveGame(roundId);
    List<PlayerBetInfo> playerBets = s.getPlayers().values().stream()
        .map(p -> new PlayerBetInfo(p.getUserId(), "", p.getCurrentBet(), p.isFolded(), p.isAllIn()))
        .toList();
    return new BetStateSnapshot(
        s.getRoundId(), s.getCurrentTurnUserId(), s.getCurrentHighBet(),
        s.getMainPot(), List.of(), playerBets, s.getStateVersion()
    );
}
```

---

## 4. Kế Hoạch Test (JUnit 5)

### HandEvaluatorTest.java
```java
@Test void detectSap_KKK() { /* 3 lá K → SAP */ }
@Test void detectSap_AAA_isHighest() { /* A-A-A > K-K-K */ }
@Test void detectLieng_A23_isValid() { /* A-2-3 → LIENG */ }
@Test void detectLieng_QKA_isValid() { /* Q-K-A → LIENG, score cao nhất */ }
@Test void detectLieng_KA2_isInvalid() { /* K-A-2 không phải LIENG → kiểm tra là DIEM */ }
@Test void detectBoDoi_JQK() { /* J-Q-K → BO_DOI */ }
@Test void detectBoDoi_notWhenSap_KKK() { /* K-K-K → SAP, không phải BO_DOI */ }
@Test void detectDiem_9() { /* 5-5-9 = 5+5+9=19 → 9 điểm */ }
@Test void detectDiem_0() { /* 10-J-Q = 0+0+0 = 0 điểm */ }
@Test void detectDiem_1() { /* A-10-K = 1+0+0 = 1 điểm */ }
```

### HandComparatorTest.java
```java
@Test void sap_beats_lieng() { /* Sáp > Liêng */ }
@Test void lieng_beats_bodoi() { /* Liêng > Bồ đội */ }
@Test void bodoi_beats_diem() { /* Bồ đội > Điểm */ }
@Test void sameSap_higherRank_wins() { /* A-A-A > K-K-K */ }
@Test void sameLieng_higherSuit_wins() {
    // 5♦-6♠-7♣ vs 5♣-6♦-7♠ → so lá cao nhất: 7♣ (CHUON=1) vs 7♠ (BICH=2) → B thắng
}
@Test void sameDiem_9_suitTiebreak() { /* Cùng 9 điểm → so chất */ }
```

### BettingEngineTest.java
```java
@Test void raise_valid_updatesState()
@Test void call_valid_callsCurrentHighBet()
@Test void fold_valid_marksPlayerFolded()
@Test void allIn_valid_marksPlayerAllIn()
@Test void action_wrongRoundId_throwsStaleRound()
@Test void action_wrongPhase_throwsInvalidState()
@Test void action_wrongActor_throwsForbidden()
@Test void action_notYourTurn_throwsNotYourTurn()
@Test void action_alreadyFolded_throwsInvalidState()
@Test void raise_lessThanMinimum_throwsInvalidBetAmount()
@Test void raise_moreThanBalance_throwsInvalidBetAmount()
@Test void onePlayerLeft_phaseBecomesFinished()
@Test void allCallEqual_phaseBecomesShowdown()
@Test void action_doesNotChangeStateOnException() {
    // Gọi action lỗi → kiểm tra state trước và sau giống hệt nhau
}
```

---

## 5. Checklist Thực Hiện Theo Thứ Tự

- [x] **1.** Tạo `PlayerBetInfo.java`, `GameStateSnapshot.java`, `BetStateSnapshot.java`, `PlayerStateSnapshot.java`, `BetRecord.java` trong `shared/model`
- [x] **2.** Tạo `HandEvaluator.java` trong `server/game/engine`
- [x] **3.** Viết `HandEvaluatorTest.java` → tất cả PASS (18/18 PASS)
- [x] **4.** Tạo `HandComparator.java` trong `server/game/engine`
- [x] **5.** Viết `HandComparatorTest.java` → tất cả PASS (13/13 PASS)
- [x] **6.** Tạo `BettingEngine.java` trong `server/game/engine`
- [x] **7.** Viết `BettingEngineTest.java` → tất cả PASS (14/14 PASS)
- [x] **8.** Implement `handleAction()` trong `GameEngineImpl` (delegate sang BettingEngine)
- [x] **9.** 3 snapshot methods `getPublicSnapshot`, `getPlayerSnapshot`, `getBetSnapshot` trong `GameEngineImpl`
- [x] **10.** Chạy toàn bộ `mvn test` → PASS (64/64 PASS — shared + server + client)
