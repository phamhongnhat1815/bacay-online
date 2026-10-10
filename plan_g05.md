# 📋 G05 — Rời Ván & Snapshot (handlePlayerLeft + Snapshots)
**Người thực hiện:** Trọng | **Mốc hoàn thành:** M4.2 | **Phụ thuộc:** G02, G03, G04 phải xong trước

---

## 1. Mục Tiêu

Xử lý đúng khi người chơi rời bàn / mất kết nối giữa ván. Đảm bảo dữ liệu riêng tư (bài tẩy) không bao giờ lộ ra ngoài. Phản hồi hành động tới muộn sau khi ván đã kết thúc.

**Tiêu chí nghiệm thu (trích biên bản bàn giao):**
> "Không rò rỉ dữ liệu bí mật qua kết quả trung gian hoặc snapshot; thao tác rời ván cho kết quả nhất quán theo `Game_Rules.md`."

---

## 2. Danh Sách File Cần Tạo / Cập Nhật

### Module `server` — Cập nhật `GameEngineImpl`
Implement 4 phương thức còn lại trong interface `GameEngine`:

| Phương thức | Mô tả |
|-------------|-------|
| `handlePlayerLeft(String roundId, long userId)` | Xử lý người chơi rời ván / mất kết nối |
| `getPublicSnapshot(String roundId)` | Snapshot công khai: phase, currentTurn, activePlayers — **không có bài** |
| `getPlayerSnapshot(String roundId, long userId)` | Snapshot riêng: chỉ trả bài + số dư của đúng userId đó |
| `getBetSnapshot(String roundId)` | Snapshot cược: pot, currentHighBet, danh sách mức cược người chơi |

> **Lưu ý:** 3 hàm snapshot đã được định nghĩa trong G03 plan nhưng implement hoàn chỉnh và test cẩn thận trong G05 này.

### Module `server` — Unit Test

| File | Các ca kiểm thử |
|------|-----------------|
| `PlayerLeftTest.java` | Fold tự động, hủy ván, không đủ người |
| `SnapshotSecurityTest.java` | Bài không lộ trong public snapshot, snapshot riêng đúng người |
| `StaleActionTest.java` | Hành động tới muộn sau ván kết thúc bị từ chối |

---

## 3. Đặc Tả Kỹ Thuật Chi Tiết

### 3.1 handlePlayerLeft() — Logic xử lý rời ván

**Theo Game_Rules.md Mục 4b:**
- Nếu người rời ván → Fold tự động đối với người đó.
- Nếu còn `>= 2` người chưa fold → ván tiếp tục.
- Nếu còn `< 2` người chưa fold → ván bị `CANCELLED` (hoàn trả tiền).
- Nếu người rời là người đang đến lượt → bỏ qua lượt, chuyển sang người kế tiếp.

```java
@Override
public boolean handlePlayerLeft(String roundId, long userId) throws GameException {
    GameState state = activeGames.get(roundId);

    // Nếu ván đã kết thúc → không làm gì (hành động tới muộn)
    if (state == null || state.getFinalResult() != null) {
        return false; // Ván không còn active
    }

    PlayerState player = state.getPlayers().get(userId);
    if (player == null) return false; // Người này không có trong ván

    // Đã fold rồi → không cần xử lý lại
    if (player.isFolded()) return state.getActivePlayers().size() >= 2;

    // Fold tự động cho người rời
    player.setFolded(true);

    // Ghi vào betHistory
    int seqNo = state.getBetHistory().size() + 1;
    state.addBetRecord(new BetRecord(
        userId, null, BetAction.FOLD,
        player.getCurrentBet(), BigDecimal.ZERO,
        BigDecimal.ONE, BigDecimal.ONE, seqNo, System.currentTimeMillis()
    ));

    // Nếu đang là lượt của người này → chuyển lượt
    if (state.getCurrentTurnUserId() != null && state.getCurrentTurnUserId().equals(userId)) {
        if (state.getPhase() == GamePhase.BETTING) {
            state.advanceTurn();
        }
    }

    // Kiểm tra điều kiện tiếp tục
    long activeCount = state.getActivePlayers().size(); // activePlayers = chưa fold
    if (activeCount < 2) {
        // Hủy ván
        state.setPhase(GamePhase.CANCELLED);
        state.setCancelReason("Không đủ người chơi (người chơi rời ván)");
        return false; // false = ván bị hủy
    }

    // Kiểm tra nếu chỉ còn 1 người chưa fold → kết thúc ngay (người đó thắng)
    long notFolded = state.getPlayers().values().stream().filter(p -> !p.isFolded()).count();
    if (notFolded == 1) {
        state.setPhase(GamePhase.FINISHED);
    }

    return true; // true = ván còn tiếp tục
}
```

> **Quy tắc quan trọng:** Đường dọn trạng thái thống nhất — `handlePlayerLeft` được gọi từ Khánh cho mọi tình huống: `LEAVE_ROOM`, `LOGOUT`, timeout heartbeat, EOF socket. Xem [Protocol.md Mục 5.4](file:///d:/Project/bacay-online/docs/Protocol.md#L582-L601).

### 3.2 Bảo Mật Snapshot — Nguyên tắc quan trọng

Theo [Protocol.md Mục 6](file:///d:/Project/bacay-online/docs/Protocol.md#L622-L629):
> "Không broadcast trạng thái nội bộ Game Engine. Có thể chứa bài chưa lật của người khác."

**3 loại snapshot và quy tắc bảo mật:**

| Snapshot | Gửi cho ai | Bài tẩy | Số dư |
|----------|-----------|---------|-------|
| `GameStateSnapshot` | **Broadcast tất cả** (GAME_UPDATED) | ❌ Tuyệt đối không | ❌ Không |
| `BetStateSnapshot` | **Broadcast tất cả** (BET_UPDATED) | ❌ Tuyệt đối không | ❌ Không |
| `PlayerStateSnapshot` | **Chỉ gửi riêng đúng người** (PLAYER_STATE) | ✅ Chỉ bài của ĐỦng người nhận | ✅ Chỉ số dư của người đó |

```java
@Override
public GameStateSnapshot getPublicSnapshot(String roundId) {
    GameState s = requireActiveGame(roundId); // helper throw STALE_ROUND nếu không có
    // KIỂM TRA: GameStateSnapshot TUYỆT ĐỐI không chứa cards, balance
    List<Long> activePlayers = s.getPlayers().entrySet().stream()
        .filter(e -> !e.getValue().isFolded())
        .map(Map.Entry::getKey)
        .toList();
    return new GameStateSnapshot(
        s.getRoundId(),
        s.getPhase().name(),
        s.getCurrentTurnUserId(),   // null khi ván xong
        activePlayers,
        s.incrementAndGetStateVersion()
    );
}

@Override
public PlayerStateSnapshot getPlayerSnapshot(String roundId, long userId) {
    GameState s = requireActiveGame(roundId);
    PlayerState ps = s.getPlayers().get(userId);
    if (ps == null)
        throw new GameException(ErrorCode.FORBIDDEN, "Người chơi không có trong ván này");
    // CHỈ lấy bài của đúng người yêu cầu — không bao giờ lấy cards của người khác
    return new PlayerStateSnapshot(
        s.getRoundId(),
        List.copyOf(ps.getCards()),   // Bài bí mật — chỉ của userId này
        ps.getBalance(),
        ps.getCurrentBet(),
        s.getStateVersion()
    );
}

@Override
public BetStateSnapshot getBetSnapshot(String roundId) {
    GameState s = requireActiveGame(roundId);
    List<PlayerBetInfo> playerBets = s.getPlayers().values().stream()
        .map(p -> new PlayerBetInfo(
            p.getUserId(), "",  // displayName để trống — Khánh tự bổ sung từ session
            p.getCurrentBet(), p.isFolded(), p.isAllIn()
        ))
        .toList();
    // KIỂM TRA: BetStateSnapshot không chứa bài
    return new BetStateSnapshot(
        s.getRoundId(),
        s.getCurrentTurnUserId(),
        s.getCurrentHighBet(),
        s.getMainPot(),
        List.of(),           // sidePots sẽ có khi finalizeRound được gọi
        playerBets,
        s.getStateVersion()
    );
}
```

### 3.3 Phòng Thủ Hành Động Tới Muộn

Sau khi ván đã `FINISHED` hoặc `CANCELLED`, mọi action gửi đến phải bị từ chối:

```java
// Trong handleAction() (đã define ở G03), thêm kiểm tra đầu:
if (state.getPhase() == GamePhase.FINISHED || state.getPhase() == GamePhase.CANCELLED) {
    throw new GameException(ErrorCode.STALE_ROUND, "Ván đã kết thúc, không chấp nhận thêm action");
}
```

---

## 4. Kế Hoạch Test (JUnit 5)

### PlayerLeftTest.java
```java
@Test void playerLeft_autoFold_gameContines() {
    // 3 người, 1 người rời → fold tự động → còn 2 người → ván tiếp tục → return true
}

@Test void playerLeft_lastTwoPlayers_gameCancelled() {
    // 2 người, 1 người rời → < 2 người → CANCELLED → return false
}

@Test void playerLeft_currentTurn_advancesToNext() {
    // Người đang đến lượt rời ván → lượt chuyển sang người kế tiếp
}

@Test void playerLeft_alreadyFolded_noEffect() {
    // Người đã fold trước đó rời ván → state không đổi
}

@Test void playerLeft_afterGameFinished_noEffect() {
    // Ván đã FINISHED → gọi handlePlayerLeft → return false, state không đổi
}

@Test void playerLeft_onlyOneRemaining_gameFinished() {
    // 2 người, 1 rời → người còn lại thắng ngay → phase = FINISHED
}
```

### SnapshotSecurityTest.java
```java
@Test void publicSnapshot_doesNotContainCards() {
    // Kiểm tra GameStateSnapshot không có field nào chứa Card
}

@Test void publicSnapshot_doesNotContainBalance() {
    // Kiểm tra GameStateSnapshot không có field balance
}

@Test void playerSnapshot_onlyContainsOwnCards() {
    // 3 người, snapshot của người A chỉ chứa đúng 3 lá của A, không có lá của B, C
}

@Test void playerSnapshot_wrongUserId_throwsForbidden() {
    // userId không có trong ván → GameException(FORBIDDEN)
}

@Test void betSnapshot_doesNotContainCards() {
    // BetStateSnapshot không có thông tin bài
}
```

### StaleActionTest.java
```java
@Test void actionAfterFinished_throwsStaleRound() {
    // Khởi tạo ván → chốt → gửi action → expect STALE_ROUND exception
}

@Test void actionAfterCancelled_throwsStaleRound() {
    // Ván CANCELLED → gửi action → expect STALE_ROUND exception
}

@Test void actionAfterStaleRoundId_throwsStaleRound() {
    // roundId không tồn tại → expect STALE_ROUND
}
```

---

## 5. Checklist Thực Hiện Theo Thứ Tự

- [x] **1.** Implement `handlePlayerLeft()` trong `GameEngineImpl`
- [x] **2.** Thêm kiểm tra "hành động tới muộn" vào đầu `handleAction()`
- [x] **3.** Hoàn thiện và kiểm tra 3 snapshot methods (`getPublicSnapshot`, `getPlayerSnapshot`, `getBetSnapshot`)
- [x] **4.** Viết `PlayerLeftTest.java` → tất cả PASS (6/6 PASS)
- [x] **5.** Viết `SnapshotSecurityTest.java` → tất cả PASS (6/6 PASS)
- [x] **6.** Viết `StaleActionTest.java` → tất cả PASS (4/4 PASS)
- [x] **7.** Chạy toàn bộ `mvn test` → PASS (91/91 PASS)

---

## 6. Điểm Giao Tiếp Với Khánh (N05, N06)

| Khánh cần từ Engine | Engine trả về | Khi nào Khánh gọi |
|--------------------|--------------|-------------------|
| `handlePlayerLeft(roundId, userId)` | `true/false` (ván tiếp tục hay bị hủy) | Khi phát hiện EOF, timeout, `LEAVE_ROOM`, `LOGOUT` trong khi ván đang chạy |
| `getPublicSnapshot(roundId)` | `GameStateSnapshot` | Sau mỗi `handleAction` thành công → broadcast `GAME_UPDATED` |
| `getPlayerSnapshot(roundId, userId)` | `PlayerStateSnapshot` | Sau mỗi action → gửi riêng `PLAYER_STATE` cho từng người |
| `getBetSnapshot(roundId)` | `BetStateSnapshot` | Sau mỗi action có thay đổi cược → broadcast `BET_UPDATED` |

> **Quy tắc tuần tự hóa (Khánh chịu trách nhiệm):** Tất cả lời gọi vào Engine cho cùng 1 phòng phải được serialize (synchronized hoặc single-thread executor). Engine giả định rằng không có 2 lời gọi đồng thời trên cùng 1 `roundId`. — Theo [Ban_giao_viec_BTL_Lap_trinh_mang.md](file:///d:/Project/bacay-online/Ban_giao_viec_BTL_Lap_trinh_mang.md#L61) Dòng 61.
