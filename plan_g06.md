# 📋 G06 — Test Toàn Diện & API Ghép Cho Khánh
**Người thực hiện:** Trọng | **Mốc hoàn thành:** M4.1, M4.2, M5.1, M6 | **Phụ thuộc:** G02–G05 hoàn tất

---

## 1. Mục Tiêu

Đảm bảo Engine hoạt động đúng ở **mọi tình huống biên**, bàn giao API contract hoàn chỉnh và tài liệu kỹ thuật để Khánh tích hợp mà không cần hỏi lại.

**Tiêu chí nghiệm thu (trích biên bản bàn giao):**
> "Game Engine chạy thử không cần mạng/UI/DB; bàn giao được danh sách trường hợp đã kiểm tra và kết quả mong đợi."

---

## 2. Danh Sách File Cần Tạo

### Module `server` — Integration Tests
> Đặt tại `server/src/test/java/com/game3cay/server/game/`

| File | Mô tả |
|------|-------|
| `GameEngineIntegrationTest.java` | Full flow: `initRound → bet rounds → finalizeRound` |
| `GameEngineCancelTest.java` | Các luồng hủy ván: rời bàn, mất kết nối, không đủ người |
| `GameEngineEdgeCaseTest.java` | Các tình huống biên: all-in hết tiền, 8 người, round kết thúc nhiều lần |
| `GameEngineContractTest.java` | Kiểm tra contract API: đúng chữ ký, đúng kiểu, đúng mã lỗi |

### Module `bacay-online` (root) — Tài liệu bàn giao cho Khánh
> Đặt tại `docs/game-engine-api.md`

| File | Nội dung |
|------|----------|
| `docs/game-engine-api.md` | API reference đầy đủ: chữ ký hàm, tham số, kết quả, mã lỗi, thứ tự gọi |

---

## 3. Bộ Test Toàn Diện

### 3.1 GameEngineIntegrationTest.java — Full Happy Path

```java
/**
 * Kịch bản: 3 người chơi A, B, C
 * A là dealer (dealerIndex=0), B đi đầu (index 1)
 * betAmount = 100
 *
 * Luồng:
 * B: RAISE 50 (total bet = 150)
 * C: CALL
 * A: CALL
 * → Vòng tố kết thúc (tất cả bằng 150)
 * → SHOWDOWN
 * → finalizeRound → RoundResult
 */
@Test void fullFlow_3players_showdown_resultValid() {
    // 1. initRound
    RoundContext ctx = new RoundContext(UUID.randomUUID().toString(), 1L,
        List.of(10L, 20L, 30L), new BigDecimal("100"));
    String roundId = engine.initRound(ctx, 0);

    assertNotNull(roundId);
    assertEquals(GamePhase.BETTING, engine.getCurrentPhase(roundId));

    // 2. Kiểm tra snapshot ban đầu
    BetStateSnapshot bet = engine.getBetSnapshot(roundId);
    assertEquals(new BigDecimal("300"), bet.mainPot()); // 3 × 100 ante

    // 3. B đánh RAISE 50
    engine.handleAction(20L, roundId, BetAction.RAISE, new BigDecimal("50"));
    assertEquals(GamePhase.BETTING, engine.getCurrentPhase(roundId));

    // 4. C CALL
    engine.handleAction(30L, roundId, BetAction.CALL, null);

    // 5. A CALL → tất cả bằng → chuyển SHOWDOWN
    engine.handleAction(10L, roundId, BetAction.CALL, null);
    assertEquals(GamePhase.SHOWDOWN, engine.getCurrentPhase(roundId));

    // 6. finalizeRound → RoundResult
    RoundResult result = engine.finalizeRound(roundId);
    assertNotNull(result);
    assertEquals("FINISHED", result.roundStatus());
    assertEquals(roundId, result.roundId());
    assertEquals(3, result.playerResults().size());

    // Tổng profit = 0 (tiền không tự sinh ra hoặc mất đi)
    BigDecimal totalProfit = result.playerResults().stream()
        .map(PlayerResult::profit)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertTrue(totalProfit.compareTo(BigDecimal.ZERO) == 0,
        "Tổng lợi nhuận phải = 0, thực tế: " + totalProfit);

    // 7. Gọi lần 2 → idempotent
    RoundResult result2 = engine.finalizeRound(roundId);
    assertEquals(result.roundId(), result2.roundId());
}
```

### 3.2 Kịch bản thắng sớm

```java
@Test void earlyWin_allOthersFold() {
    // 3 người A, B, C. B và C đều FOLD → A thắng ngay, không cần SHOWDOWN
    RoundContext ctx = new RoundContext(UUID.randomUUID().toString(), 1L,
        List.of(10L, 20L, 30L), new BigDecimal("50"));
    String roundId = engine.initRound(ctx, 0);

    engine.handleAction(20L, roundId, BetAction.FOLD, null); // B fold
    engine.handleAction(30L, roundId, BetAction.FOLD, null); // C fold
    // Chỉ còn A → thắng ngay
    assertEquals(GamePhase.FINISHED, engine.getCurrentPhase(roundId));

    RoundResult result = engine.finalizeRound(roundId);
    assertEquals("FINISHED", result.roundStatus());
    PlayerResult winner = result.playerResults().stream()
        .filter(r -> "WIN".equals(r.result())).findFirst().orElseThrow();
    assertEquals(10L, winner.userId());
    assertNull(winner.hand(), "Thắng sớm không lật bài → hand phải null");
}
```

### 3.3 Ván hủy vì rời bàn

```java
@Test void cancel_playerLeft_lessThan2Active() {
    RoundContext ctx = new RoundContext(UUID.randomUUID().toString(), 1L,
        List.of(10L, 20L), new BigDecimal("50")); // Chỉ 2 người
    String roundId = engine.initRound(ctx, 0);

    boolean continues = engine.handlePlayerLeft(roundId, 10L); // A rời bàn
    assertFalse(continues); // Ván hủy
    assertEquals(GamePhase.CANCELLED, engine.getCurrentPhase(roundId));

    RoundResult result = engine.finalizeRound(roundId);
    assertEquals("CANCELLED", result.roundStatus());
    assertNotNull(result.cancelReason());
    // Tất cả profit = 0
    result.playerResults().forEach(r ->
        assertEquals(0, r.profit().compareTo(BigDecimal.ZERO)));
}
```

### 3.4 GameEngineEdgeCaseTest.java

```java
@Test void allIn_noBalance_getsAllIn() {
    // Người chơi còn đúng số tiền = betAmount → all-in ngay từ đầu
}

@Test void eightPlayers_allCardsUnique() {
    // 8 người × 3 lá = 24 lá, kiểm tra Set<Card>.size() == 24
}

@Test void betHistory_sequenceNoIncreasing() {
    // Sau nhiều action, BetRecord.sequenceNo tăng dần từ 1
}

@Test void stateVersion_incrementsAfterEachAction() {
    // Mỗi action → stateVersion tăng 1
}

@Test void roundNotInActiveGames_returnsInactve() {
    // isRoundActive("nonexistent") → false
}

@Test void finalizeRound_nonExistentRound_throwsStaleRound() {
    // roundId không tồn tại → GameException(STALE_ROUND)
}

@Test void twoConcurrentRounds_doNotInterfer() {
    // 2 ván chạy song song (2 roomId khác nhau) → state độc lập
}
```

### 3.5 GameEngineContractTest.java — Kiểm tra đúng API

```java
@Test void initRound_returnsExactRoundId() {
    // roundId trả về == roundId trong RoundContext
}

@Test void getAllActions_throwCorrectErrorCodes() {
    // Tạo bảng ma trận: action × điều kiện sai → ErrorCode tương ứng
    // STALE_ROUND, FORBIDDEN, NOT_YOUR_TURN, INVALID_STATE, INVALID_BET_AMOUNT
}

@Test void handleAction_onException_stateUnchanged() {
    // Action lỗi → snapshot trước và sau giống hệt nhau
    GameStateSnapshot before = engine.getPublicSnapshot(roundId);
    assertThrows(GameException.class, () ->
        engine.handleAction(wrongUserId, roundId, BetAction.RAISE, BigDecimal.TEN));
    GameStateSnapshot after = engine.getPublicSnapshot(roundId);
    assertEquals(before.stateVersion(), after.stateVersion());
}
```

---

## 4. Tài Liệu API Cho Khánh (`docs/game-engine-api.md`)

> File này Trọng viết và push lên repo. Khánh đọc để tích hợp `N05`.

Nội dung bao gồm:
1. **Chữ ký đầy đủ của interface `GameEngine`** (copy từ source)
2. **Thứ tự gọi chuẩn cho 1 ván bài:**
   ```
   1. Khánh chuẩn bị RoundContext + dealerIndex
   2. engine.initRound(context, dealerIndex)         → lưu roundId
   3. Gửi DEAL_CARD riêng cho từng người             → dùng getPlayerSnapshot()
   4. Broadcast GameStateSnapshot                    → dùng getPublicSnapshot()
   5. Broadcast BetStateSnapshot                     → dùng getBetSnapshot()
   6. Khi nhận GAME_ACTION từ client:
      engine.handleAction(userId, roundId, action, raiseAmount)
      → Sau đó broadcast getPublicSnapshot + getBetSnapshot
      → Gửi riêng getPlayerSnapshot cho người vừa action
   7. Khi phase = FINISHED hoặc CANCELLED:
      engine.finalizeRound(roundId)
      → Broadcast ROUND_RESULT
   8. Khi người chơi rời/mất kết nối:
      engine.handlePlayerLeft(roundId, userId)
      → Nếu false → gọi finalizeRound() → broadcast ROUND_RESULT
   ```
3. **Mã lỗi và cách xử lý:**
   - `GameException.getErrorCode()` → map sang `ErrorCode` enum → gửi `Packet(ERROR, ErrorPayload)`
4. **Giả định của Engine (Khánh phải đảm bảo):**
   - Tất cả lời gọi vào Engine cho cùng 1 phòng phải tuần tự hóa.
   - `actorId` luôn lấy từ Session đã xác thực, không tin client.

---

## 5. Checklist Nghiệm Thu M4.1

> Đây là danh sách nghiệm thu chính thức để bàn giao Engine cho Khánh tích hợp.

- [ ] `mvn test -pl server,shared` → **100% PASS**, 0 failure, 0 error
- [ ] Không có test nào dùng `Thread.sleep()`, database, socket, Swing
- [ ] `finalizeRound` gọi 2 lần → kết quả giống hệt nhau (idempotent)
- [ ] Tổng `profit` trong mọi `RoundResult` FINISHED = **0** (tiền không tự sinh/mất)
- [ ] `PlayerStateSnapshot` cho user A không chứa bài của user B
- [ ] `GameStateSnapshot` không chứa bất kỳ `Card` nào
- [ ] Action sau `FINISHED`/`CANCELLED` → ném `GameException(STALE_ROUND)`
- [ ] Action sai lượt → ném `GameException(NOT_YOUR_TURN)`, state không đổi
- [ ] File `docs/game-engine-api.md` tồn tại, đủ nội dung để Khánh đọc và tích hợp

---

## 6. Checklist Thực Hiện Theo Thứ Tự

- [x] **1.** Viết `GameEngineIntegrationTest.java` — full happy path 3 người (5/5 PASS)
- [x] **2.** Viết `GameEngineCancelTest.java` — ván hủy, rời bàn (6/6 PASS)
- [x] **3.** Viết `GameEngineEdgeCaseTest.java` — tình huống biên (7/7 PASS)
- [x] **4.** Viết `GameEngineContractTest.java` — kiểm tra API contract (13/13 PASS)
- [x] **5.** Chạy toàn bộ `mvn test` → **122/122 PASS** — BUILD SUCCESS ✅
- [x] **6.** Viết `docs/game-engine-api.md` — tài liệu API cho Khánh (7 mục, sẵn sàng tích hợp)
- [x] **7.** Báo Khánh Engine sẵn sàng ghép M4.1 — đọc `docs/game-engine-api.md`
