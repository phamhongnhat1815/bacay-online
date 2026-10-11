# 📘 Game Engine API Reference — Ba Cây Online

> **Người viết:** Trọng (Game Engine) | **Người đọc:** Khánh (Network/Socket Layer)
> **Phiên bản Engine:** G02–G06 (hoàn chỉnh) | **Cập nhật:** 2026-10-11

---

## 1. Tổng Quan Kiến Trúc

```
[Client] ──Socket──► [Khánh: GameHandler] ──► [GameEngine] (Trọng)
                              │                     │
                              │◄────────────────────┘
                              │  GameStateSnapshot / RoundResult
                              │
                     [Nhật: DB Layer] ◄── RoundResult.betHistory / playerResults
```

**Nguyên tắc vàng:**
- Engine **KHÔNG** mở socket, KHÔNG tạo Swing, KHÔNG truy cập CSDL.
- Engine là **pure Java**, chỉ tính toán trong RAM.
- Khánh chịu trách nhiệm **tuần tự hóa** tất cả lời gọi vào Engine cho cùng 1 `roundId`.

---

## 2. Interface `GameEngine` — Chữ Ký Đầy Đủ

```java
package com.game3cay.server.game;

public interface GameEngine {

    // ══ VÒNG ĐỜI VÁN ══════════════════════════════════════════════════════════

    String initRound(RoundContext context, int dealerIndex) throws GameException;

    void handleAction(long actorId, String roundId, BetAction action,
                      BigDecimal raiseAmount) throws GameException;

    RoundResult finalizeRound(String roundId) throws GameException;

    boolean handlePlayerLeft(String roundId, long userId) throws GameException;

    // ══ SNAPSHOTS (Gửi Socket) ════════════════════════════════════════════════

    GameStateSnapshot  getPublicSnapshot(String roundId)                throws GameException;
    PlayerStateSnapshot getPlayerSnapshot(String roundId, long userId)  throws GameException;
    BetStateSnapshot   getBetSnapshot(String roundId)                   throws GameException;

    // ══ KIỂM TRA TRẠNG THÁI ══════════════════════════════════════════════════

    boolean   isRoundActive(String roundId);
    GamePhase getCurrentPhase(String roundId);
}
```

---

## 3. Thứ Tự Gọi Chuẩn Cho 1 Ván Bài

```
Khánh chuẩn bị:
  RoundContext ctx = new RoundContext(roundId, roomId, playerIds, betAmount);

BƯỚC 1 — Khởi tạo ván:
  String roundId = engine.initRound(ctx, dealerIndex);

BƯỚC 2 — Gửi bài riêng cho từng người (DEAL_CARD):
  for (long uid : playerIds) {
      PlayerStateSnapshot snap = engine.getPlayerSnapshot(roundId, uid);
      send(uid, PLAYER_STATE, snap);   // Chỉ gửi cho đúng người đó
  }

BƯỚC 3 — Broadcast trạng thái ban đầu:
  broadcast(room, GAME_UPDATED, engine.getPublicSnapshot(roundId));
  broadcast(room, BET_UPDATED,  engine.getBetSnapshot(roundId));

BƯỚC 4 — Nhận GAME_ACTION từ client, xử lý:
  try {
      engine.handleAction(userId, roundId, action, raiseAmount);
  } catch (GameException e) {
      send(userId, ERROR, ErrorPayload(e.getErrorCode(), e.getMessage()));
      return; // Không broadcast nếu lỗi
  }
  // Sau action thành công:
  broadcast(room, GAME_UPDATED, engine.getPublicSnapshot(roundId));
  broadcast(room, BET_UPDATED,  engine.getBetSnapshot(roundId));
  send(userId, PLAYER_STATE,    engine.getPlayerSnapshot(roundId, userId));

BƯỚC 5 — Kiểm tra kết thúc:
  GamePhase phase = engine.getCurrentPhase(roundId);
  if (phase == FINISHED || phase == SHOWDOWN || phase == CANCELLED) {
      RoundResult result = engine.finalizeRound(roundId);
      broadcast(room, ROUND_RESULT, result);
      // Nhật lưu DB: result.betHistory(), result.playerResults(), result.sidePots()
  }

BƯỚC 6 — Khi người chơi rời/mất kết nối giữa ván:
  boolean continues = engine.handlePlayerLeft(roundId, userId);
  if (!continues) {
      // Ván kết thúc (FINISHED hoặc CANCELLED)
      RoundResult result = engine.finalizeRound(roundId);
      broadcast(room, ROUND_RESULT, result);
  } else {
      // Ván tiếp tục
      broadcast(room, GAME_UPDATED, engine.getPublicSnapshot(roundId));
      broadcast(room, BET_UPDATED,  engine.getBetSnapshot(roundId));
  }
```

---

## 4. Chi Tiết Từng Hàm

### 4.1 `initRound(RoundContext context, int dealerIndex)`

| Tham số | Kiểu | Mô tả |
|---------|------|-------|
| `context.roundId` | `String` | UUID duy nhất do Khánh sinh (UUID.randomUUID().toString()) |
| `context.roomId` | `long` | ID phòng chơi |
| `context.playerIds` | `List<Long>` | Danh sách userId theo thứ tự ghế ngồi (2–8 người) |
| `context.betAmount` | `BigDecimal` | Mức cược ante (phải > 0) |
| `context.initialBalances` | `Map<Long,BigDecimal>` | Số dư ban đầu (nếu null → Engine tự cấp 100×ante) |
| `dealerIndex` | `int` | Index (0-based) của dealer trong `playerIds` |

**Trả về:** `String` — đúng `context.roundId` đã truyền vào.

**Throws:**
- `INVALID_REQUEST` — `dealerIndex` ngoài phạm vi [0, playerIds.size()-1]
- `INVALID_STATE` — `roundId` đã tồn tại
- `INVALID_BET_AMOUNT` — có người không đủ số dư để đóng ante

---

### 4.2 `handleAction(long actorId, String roundId, BetAction action, BigDecimal raiseAmount)`

**`BetAction`** enum: `RAISE`, `CALL`, `FOLD`, `ALL_IN`

| Hành động | `raiseAmount` | Điều kiện |
|-----------|---------------|-----------|
| `RAISE`   | Số tiền tố thêm > 0 | Phải có đủ balance |
| `CALL`    | `null` | Phải còn chênh lệch so với `currentHighBet` |
| `FOLD`    | `null` | Luôn hợp lệ |
| `ALL_IN`  | `null` | Bỏ toàn bộ balance còn lại |

**Throws:**
| `ErrorCode` | Điều kiện |
|-------------|-----------|
| `STALE_ROUND` | `roundId` không tồn tại, hoặc ván đã FINISHED/CANCELLED |
| `FORBIDDEN` | `actorId` không có trong ván |
| `NOT_YOUR_TURN` | Chưa đến lượt `actorId` |
| `INVALID_STATE` | Ván không ở phase BETTING |
| `INVALID_BET_AMOUNT` | RAISE ≤ 0 hoặc vượt số dư |

> ⚠️ **Atomicity:** Nếu throw exception, state ván **không thay đổi**. Không cần rollback.

---

### 4.3 `finalizeRound(String roundId)`

Gọi khi phase = `FINISHED`, `SHOWDOWN`, hoặc `CANCELLED`.

**Idempotent:** Gọi nhiều lần trả cùng object `RoundResult`.

**Trả về `RoundResult`:**
```java
record RoundResult(
    String roundId,
    long roomId,
    String roundStatus,       // "FINISHED" hoặc "CANCELLED"
    long finishedAtMillis,
    BigDecimal baseBetAmount,
    List<PlayerResult> playerResults,
    List<BetRecord> betHistory,   // → Nhật lưu DB (bảng point_bets)
    List<SidePot> sidePots,       // Chỉ có khi có người ALL_IN
    String cancelReason           // Chỉ có khi CANCELLED
)
```

**`PlayerResult`:**
```java
record PlayerResult(
    long userId,
    String displayName,    // "" (Khánh tự bổ sung từ session)
    String avatarUrl,      // ""
    Hand hand,             // null khi thắng sớm (không lật bài)
    String handType,       // null khi thắng sớm
    int rank,              // 1 = người thắng, 2,3,... người thua
    String result,         // "WIN" / "LOSE" / "FOLD" / "CANCELLED"
    BigDecimal profit,     // Dương = thắng, âm = thua, 0 = CANCELLED
    int seatNumber
)
```

**Throws:** `STALE_ROUND` (roundId không tồn tại), `INVALID_STATE` (ván chưa kết thúc)

---

### 4.4 `handlePlayerLeft(String roundId, long userId)`

Gọi khi phát hiện: **EOF socket**, **timeout heartbeat**, **`LEAVE_ROOM`**, **`LOGOUT`** trong khi ván đang chạy.

| Trả về | Ý nghĩa |
|--------|---------|
| `true` | Ván còn tiếp tục (≥ 2 người chưa fold) — broadcast snapshot |
| `false` | Ván kết thúc (FINISHED hoặc CANCELLED) — gọi `finalizeRound` |

> Hàm **không throw** khi `roundId` không tồn tại hoặc `userId` không có trong ván — trả `false` im lặng.

---

### 4.5 Snapshot Methods — Bảo Mật Dữ Liệu

| Method | Gửi cho ai | Chứa bài | Chứa balance |
|--------|-----------|----------|--------------|
| `getPublicSnapshot()` | **Broadcast** cả phòng (`GAME_UPDATED`) | ❌ **KHÔNG** | ❌ **KHÔNG** |
| `getBetSnapshot()` | **Broadcast** cả phòng (`BET_UPDATED`) | ❌ **KHÔNG** | ❌ **KHÔNG** |
| `getPlayerSnapshot(userId)` | **Chỉ gửi riêng** đúng `userId` đó (`PLAYER_STATE`) | ✅ Chỉ của người đó | ✅ Của người đó |

> 🔴 **TUYỆT ĐỐI:** Không bao giờ gửi `getPlayerSnapshot(A)` cho người B. Không broadcast `PlayerStateSnapshot`.

**`GameStateSnapshot`** (public):
```java
record GameStateSnapshot(
    String roundId,
    String phase,               // "BETTING", "SHOWDOWN", "FINISHED", "CANCELLED"
    Long currentTurnUserId,     // null khi ván xong
    List<Long> activePlayers,   // userId chưa fold — KHÔNG chứa Card
    long stateVersion
)
```

**`BetStateSnapshot`** (public):
```java
record BetStateSnapshot(
    String roundId,
    Long currentTurnUserId,
    BigDecimal currentHighBet,
    BigDecimal mainPot,
    List<SidePot> sidePots,     // Chỉ có khi finalizeRound đã gọi
    List<PlayerBetInfo> playerBets, // userId, currentBet, folded, allIn — KHÔNG chứa Card
    long stateVersion
)
```

**`PlayerStateSnapshot`** (private — 1 người):
```java
record PlayerStateSnapshot(
    String roundId,
    List<Card> myCards,         // 3 lá bài bí mật — CHỈ của userId được gửi
    BigDecimal myBalance,
    BigDecimal myCurrentBet,
    long stateVersion
)
```

---

## 5. Mã Lỗi và Cách Xử Lý

```java
try {
    engine.handleAction(userId, roundId, action, raiseAmount);
} catch (GameException e) {
    ErrorCode code = e.getErrorCode();
    switch (code) {
        case STALE_ROUND   -> send(userId, ERROR, "Ván đã kết thúc");
        case NOT_YOUR_TURN -> send(userId, ERROR, "Chưa đến lượt bạn");
        case FORBIDDEN     -> send(userId, ERROR, "Bạn không có trong ván");
        case INVALID_BET_AMOUNT -> send(userId, ERROR, "Mức cược không hợp lệ");
        case INVALID_STATE -> send(userId, ERROR, "Hành động không hợp lệ lúc này");
        default            -> send(userId, ERROR, e.getMessage());
    }
}
```

> **Quy tắc:** Client chỉ được kiểm tra `ErrorCode`, KHÔNG so sánh `message` string.

---

## 6. Dữ Liệu Cho Nhật (DB Layer)

Sau khi `finalizeRound()` trả về `RoundResult`, Nhật cần lưu:

| Field | Bảng DB | Ghi chú |
|-------|---------|---------|
| `result.betHistory()` | `point_bets` | Mỗi `BetRecord` = 1 row; `sequenceNo` là thứ tự action |
| `result.playerResults()` | `round_results` | Mỗi `PlayerResult` = 1 row |
| `result.sidePots()` | `side_pots` (nếu cần) | Chỉ có khi có người ALL_IN |
| `result.roundStatus()` | `rounds.status` | "FINISHED" hoặc "CANCELLED" |
| `result.finishedAtMillis()` | `rounds.finished_at` | epoch ms → TIMESTAMP |

---

## 7. Giả Định Của Engine (Khánh Phải Đảm Bảo)

1. **Tuần tự hóa:** Tất cả lời gọi vào Engine cho cùng 1 `roomId`/`roundId` phải được serialize. Dùng `synchronized` hoặc single-thread executor per room. Engine **không thread-safe** cho cùng 1 roundId.

2. **actorId đã xác thực:** `actorId` luôn lấy từ `Session` đã đăng nhập, không tin client truyền lên.

3. **roundId do Khánh sinh:** `UUID.randomUUID().toString()` trước khi gọi `initRound`.

4. **Xử lý tất cả disconnect:** Gọi `handlePlayerLeft()` cho **mọi** tình huống rời ván (EOF, timeout heartbeat, LEAVE_ROOM, LOGOUT, crash).

5. **Không gọi snapshot khi ván không active:** `getPublicSnapshot()`, `getBetSnapshot()`, `getPlayerSnapshot()` sẽ throw `STALE_ROUND` nếu `roundId` không tồn tại.

---

## 8. Sơ Đồ Phase Transitions

```
[WAITING]
    │ initRound()
    ▼
[BETTING] ──── handleAction(FOLD) → notFolded == 1 ────► [FINISHED]
    │
    │ handleAction(RAISE/CALL) → isBettingEqual() == true
    ▼
[SHOWDOWN]
    │ finalizeRound()
    ▼
[FINISHED] ──── (kết quả lưu DB bởi Nhật)

[BETTING] ──── handlePlayerLeft() → notFolded == 1 ────► [FINISHED]
[BETTING] ──── handlePlayerLeft() → notFolded == 0 ────► [CANCELLED]
```

---

## 9. Test Coverage Đã Đạt (Nghiệm Thu M4.1)

```
BettingEngineTest:          14 PASS  — Logic tố, raise, call, fold, all-in
DeckTest:                    9 PASS  — Xáo bài, chia bài, không trùng lặp
GameEngineInitTest:         10 PASS  — Khởi tạo ván, validate input
GameEngineResultTest:        7 PASS  — finalizeRound, SidePot, idempotent
HandComparatorTest:         13 PASS  — So sánh bài, sort ranking
HandEvaluatorTest:          18 PASS  — Đánh giá tất cả loại bài Ba Cây
PlayerLeftTest:              6 PASS  — handlePlayerLeft mọi tình huống
SidePotCalculatorTest:       4 PASS  — Tính side pot khi ALL_IN
SnapshotSecurityTest:        6 PASS  — Không lộ bài/balance qua public snapshot
StaleActionTest:             4 PASS  — Action sau FINISHED/CANCELLED bị từ chối
GameEngineIntegrationTest:   5 PASS  — Full flow, earlyWin, betHistory, stateVersion
GameEngineCancelTest:        6 PASS  — Hủy ván, rời bàn, idempotent cancel
GameEngineEdgeCaseTest:      7 PASS  — 8 người, all-in, 2 ván song song, profit bất biến
GameEngineContractTest:     13 PASS  — Tất cả ErrorCode, atomicity, API contract
─────────────────────────────────────────────────
TOTAL:                     122/122   BUILD SUCCESS ✅
```

> 🎯 **Sẵn sàng tích hợp M4.1** — Engine hoàn chỉnh, có thể dùng ngay.
