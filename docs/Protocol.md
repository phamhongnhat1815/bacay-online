# Protocol.md — Giao Thức Thông Điệp BaCay Online

**Phiên bản:** 1.0  
**Người phụ trách:** Khánh  
**Cập nhật lần cuối:** 2026-09-25  

> Đây là **hợp đồng triển khai** của cả nhóm. Mọi thay đổi (thêm field, đổi tên type, thay đổi payload) **phải** cập nhật tài liệu này và thông báo cho tất cả thành viên trước khi merge vào `main`.

---

## 1. Tổng Quan

### 1.1 Cơ chế truyền thông

```
Client A ──┐
Client B ──┤──► ServerSocket (port 8888) ──► ClientHandler thread
Client C ──┘                                      │
                                              ┌───▼────────────┐
                                              │  SessionManager │  (Khánh - N03)
                                              │  RoomManager    │  (Khánh - N04)
                                              │  GameEngine     │  (Trọng - G01-G06)
                                              │  UserBUS / DAL  │  (Nhật  - D01-D06)
                                              └────────────────┘
```

- **TCP**, một kết nối duy trì trong suốt phiên sử dụng.
- **Serialization:** `ObjectOutputStream` / `ObjectInputStream` — Java Serializable.
- **Một luồng đọc** trên mỗi kết nối; một đường gửi thống nhất (synchronized hoặc queue).
- **Thứ tự khởi tạo stream (bắt buộc cả hai phía):**
  1. Tạo `ObjectOutputStream`, gọi `flush()`.
  2. Sau đó tạo `ObjectInputStream`.
  - Vi phạm thứ tự này gây deadlock khi kết nối.

### 1.2 Cấu trúc Packet

```java
public final class Packet implements Serializable {
    String protocolVersion; // Phải là "1.0"
    PacketType type;
    String requestId;       // UUID — xem quy tắc bên dưới
    Object data;            // Payload tương ứng type, xem mục 3
}
```

**Quy tắc `requestId`:**

| Trường hợp | requestId |
|---|---|
| Client gửi request | UUID ngẫu nhiên (client tự sinh) |
| Server phản hồi request | **Copy y nguyên** requestId của request |
| Server broadcast / push chủ động | `null` |

**Quy tắc `data`:**
- Phải implement `Serializable`.
- Kiểu phải khớp với type — xem bảng payload mục 3.
- **Không được chứa:** socket, stream, JDBC connection, thread, đối tượng Game Engine nội bộ.

### 1.3 Ký hiệu chiều

| Ký hiệu | Nghĩa |
|---|---|
| **C→S** | Client gửi lên Server |
| **S→C\*** | Server gửi riêng cho đúng một client |
| **S→C (room)** | Server broadcast cho tất cả người trong cùng phòng |
| **S→C (all)** | Server broadcast cho tất cả client đang kết nối |

---

## 2. Danh Sách PacketType

### 2.1 Hệ thống

| PacketType | Chiều | Mô tả |
|---|---|---|
| `PING` | S→C\* | Server kiểm tra kết nối sống — gửi mỗi 5 giây |
| `PONG` | C→S | Client phản hồi PING — phải trả trong 15 giây |
| `ACK` | S→C\* | Xác nhận thành công (không có data riêng) |
| `ERROR` | S→C\* | Phản hồi lỗi, data là `ErrorPayload` |

### 2.2 Xác thực (Auth)

| PacketType | Chiều | Payload |
|---|---|---|
| `LOGIN` | C→S | `AuthRequest` |
| `REGISTER` | C→S | `RegisterRequest` |
| `LOGOUT` | C→S | `null` |
| `AUTH_RESULT` | S→C\* | `AuthResult` |

### 2.3 Hồ sơ (Profile)

| PacketType | Chiều | Payload |
|---|---|---|
| `GET_PROFILE` | C→S | `null` (lấy của mình) |
| `PROFILE_RESULT` | S→C\* | `ProfileResult` |
| `UPDATE_PROFILE` | C→S | `UpdateProfileRequest` |
| `CHANGE_PASSWORD` | C→S | `ChangePasswordRequest` |

### 2.4 Lobby

| PacketType | Chiều | Payload |
|---|---|---|
| `ROOM_LIST` | C→S | `null` |
| `ROOM_LIST_RESULT` | S→C\* | `List<RoomSummary>` |

### 2.5 Quản lý phòng

| PacketType | Chiều | Payload |
|---|---|---|
| `CREATE_ROOM` | C→S | `CreateRoomRequest` |
| `JOIN_ROOM` | C→S | `JoinRoomRequest` |
| `LEAVE_ROOM` | C→S | `null` |
| `SET_READY` | C→S | `Boolean` |
| `START_GAME_REQUEST` | C→S | `null` |
| `ROOM_UPDATED` | S→C (room) | `RoomSnapshot` |

### 2.6 Ván chơi

| PacketType | Chiều | Payload | Ghi chú |
|---|---|---|---|
| `START_GAME` | S→C (room) | `GameStartInfo` | Thông báo công khai, không có bài |
| `DEAL_CARD` | S→C\* | `DealCardPayload` | **Chỉ gửi riêng — KHÔNG BAO GIỜ broadcast** |
| `GAME_ACTION` | C→S | `GameActionRequest` | RAISE / CALL / FOLD / ALL_IN |
| `GAME_UPDATED` | S→C (room) | `GameStateSnapshot` | Trạng thái công khai sau mỗi action |
| `BET_UPDATED` | S→C (room) | `BetStateSnapshot` | Trạng thái đặt cược sau mỗi lượt |
| `PLAYER_STATE` | S→C\* | `PlayerStateSnapshot` | Trạng thái riêng của người nhận |
| `ROUND_RESULT` | S→C (room) | `RoundResult` | Kết quả cuối — lật bài, xếp hạng, profit |

### 2.7 Chơi tiếp

| PacketType | Chiều | Payload |
|---|---|---|
| `PLAY_AGAIN_VOTE` | C→S | `Boolean` |
| `PLAY_AGAIN_PROMPT` | S→C (room) | `PlayAgainPrompt` |

### 2.8 Chat

| PacketType | Chiều | Payload | Ghi chú |
|---|---|---|---|
| `CHAT_MESSAGE` | C→S | `ChatMessage` | Client gửi lên |
| `CHAT_MESSAGE` | S→C (room) | `ChatMessage` | Server phát lại sau khi xác nhận |
| `CHAT_SYSTEM` | S→C (room) | `ChatMessage` (type=SYSTEM) | Thông báo hệ thống tự động |
| `MUTE_PLAYER` | C→S | `Long` (targetUserId) | Chỉ áp dụng phía client gửi |

### 2.9 Dữ liệu / Thống kê

| PacketType | Chiều | Payload |
|---|---|---|
| `GET_HISTORY` | C→S | `PageRequest` |
| `HISTORY_RESULT` | S→C\* | `PageResult<GameHistoryItem>` |
| `GET_LEADERBOARD` | C→S | `PageRequest` |
| `LEADERBOARD_RESULT` | S→C\* | `PageResult<LeaderboardItem>` |

---

## 3. Định Nghĩa Payload Chi Tiết

> Tất cả record/class phải `implements Serializable` với `serialVersionUID = 1L`.  
> String dùng UTF-8. Thời gian dùng epoch millisecond từ đồng hồ **server**.

---

### 3.1 Auth

#### `AuthRequest` — dùng cho LOGIN
```
username : String   (3–50 ký tự, chỉ chữ/số/dấu gạch dưới)
password : String   (plain text — server hash; KHÔNG log lại)
```

#### `RegisterRequest` — dùng cho REGISTER  
```
username    : String
password    : String
displayName : String  (tùy chọn, nếu null dùng username)
email       : String  (tùy chọn)
```

#### `AuthResult` — server trả cho LOGIN / REGISTER
```
success     : boolean
userId      : Long    (null nếu thất bại)
username    : String
displayName : String
balance     : BigDecimal
errorCode   : ErrorCode  (null nếu thành công)
message     : String     (hiển thị cho người dùng)
```

> ⚠️ `AuthResult` **không được chứa** password hash, salt, hoặc bất kỳ thông tin bảo mật nào.

---

### 3.2 Hồ sơ

#### `ProfileResult`
```
userId      : long
username    : String
displayName : String
email       : String
avatarUrl   : String
balance     : BigDecimal
totalGames  : int
totalWins   : int
totalLosses : int
```

#### `UpdateProfileRequest`
```
displayName : String  (null = không đổi)
email       : String  (null = không đổi)
avatarUrl   : String  (null = không đổi)
```

#### `ChangePasswordRequest`
```
currentPassword : String
newPassword     : String
```

---

### 3.3 Lobby

#### `RoomSummary` — phần tử trong `ROOM_LIST_RESULT`
```
roomId      : long
roomCode    : String
roomName    : String
status      : String  (WAITING / PLAYING)
playerCount : int
maxPlayers  : int
betAmount   : BigDecimal
```

---

### 3.4 Quản lý phòng

#### `CreateRoomRequest`
```
roomName   : String        (1–100 ký tự)
minPlayers : int           (2–8)
maxPlayers : int           (minPlayers–8)
betAmount  : BigDecimal    (> 0)
```

#### `JoinRoomRequest`
```
roomId : long
```

#### `RoomSnapshot` — payload của `ROOM_UPDATED`
```
roomId       : long
roomCode     : String
roomName     : String
status       : String   (WAITING / PLAYING / FINISHED)
ownerUserId  : long
minPlayers   : int
maxPlayers   : int
betAmount    : BigDecimal
players      : List<PlayerSlot>
stateVersion : long     // tăng sau mỗi thay đổi — client bỏ qua bản cũ hơn
```

#### `PlayerSlot` — phần tử trong `RoomSnapshot.players`
```
userId      : long
displayName : String
seatNumber  : int      (1–8)
ready       : boolean
status      : String   (PLAYING / SPECTATOR / LEFT)
```

---

### 3.5 Ván chơi

#### `GameStartInfo` — payload của `START_GAME`
```
roundId    : String   (UUID — duy nhất, bất biến trong suốt ván)
roomId     : long
playerIds  : List<Long>   (thứ tự ngồi)
startedAt  : long         (epoch milli, đồng hồ server)
```

#### `DealCardPayload` — payload của `DEAL_CARD` (chỉ gửi riêng)
```
roundId : String
cards   : List<Card>  (đúng 3 lá)
```

> 🔒 Server KHÔNG BAO GIỜ gửi `DealCardPayload` qua broadcast.  
> Client nhận được bài của mình qua kênh riêng này.

#### `GameActionRequest` — payload của `GAME_ACTION`
```
roundId      : String
action       : BetAction   (RAISE / CALL / FOLD / ALL_IN)
raiseAmount  : BigDecimal  (chỉ có ý nghĩa khi action = RAISE; null nếu khác)
```

#### `GameStateSnapshot` — payload của `GAME_UPDATED`
```
roundId          : String
phase            : String        (BETTING / SHOWDOWN / FINISHED)
currentTurnUserId: Long          (null nếu không có lượt cụ thể)
activePlayers    : List<Long>    (userId của người chưa fold/left)
stateVersion     : long
```

#### `BetStateSnapshot` — payload của `BET_UPDATED`
```
roundId           : String
currentTurnUserId : Long
currentHighBet    : BigDecimal
mainPot           : BigDecimal
sidePots          : List<SidePot>
playerBets        : List<PlayerBetInfo>
stateVersion      : long
```

#### `PlayerBetInfo` — phần tử trong `BetStateSnapshot`
```
userId      : long
displayName : String
currentBet  : BigDecimal
folded      : boolean
allIn       : boolean
```

#### `PlayerStateSnapshot` — payload của `PLAYER_STATE` (chỉ gửi riêng)
```
roundId      : String
myCards      : List<Card>      (bài của người nhận — bí mật)
myBalance    : BigDecimal      (số dư hiện tại)
myCurrentBet : BigDecimal
stateVersion : long
```

#### `SidePot`
```
amount           : BigDecimal
eligiblePlayerIds: List<Long>
```

---

### 3.6 Chơi tiếp

#### `PlayAgainPrompt`
```
roundId     : String
timeoutSecs : int      (thời gian chờ vote)
votes       : Map<Long, Boolean>   // userId → wantPlayAgain
```

---

### 3.7 Kết quả ván

#### `RoundResult` — payload của `ROUND_RESULT`
```
roundId       : String
playerResults : List<PlayerResult>   (sắp xếp theo rank từ cao xuống)
roundStatus   : String   (FINISHED / CANCELLED)
endedAtMillis : long     (đồng hồ server)
```

#### `PlayerResult` — phần tử trong `RoundResult`
```
userId      : long
username    : String
displayName : String
hand        : Hand         (null nếu FOLD trước showdown)
rank        : int          (1 = thắng; 0 = không xếp hạng)
result      : String       (WIN / LOSE / DRAW / FOLD)
profit      : BigDecimal   (dương = thắng, âm = thua)
```

---

### 3.8 Chat

#### `ChatMessage`
```
senderId          : Long     (null nếu SYSTEM)
senderDisplayName : String   (null nếu SYSTEM)
content           : String   (tối đa 500 ký tự)
type              : String   (TEXT / EMOJI / SYSTEM)
timestampMillis   : long     (đồng hồ server — client không tự điền)
```

---

### 3.9 Dữ liệu / Thống kê

#### `PageRequest`
```
page     : int   (bắt đầu từ 0; âm → lỗi INVALID_PAGE)
pageSize : int   (mặc định 20; tối đa 100; vượt quá → lỗi INVALID_PAGE)
```

#### `PageResult<T>`
```
items      : List<T>
page       : int
pageSize   : int
totalItems : long
totalPages : int
```

#### `GameHistoryItem` — phần tử của `HISTORY_RESULT`
```
gameId      : long
roundId     : String
roomName    : String
result      : String       (WIN / LOSE / DRAW / FOLD / CANCELLED)
profit      : BigDecimal
handType    : String       (SAP / LIENG / BO_DOI / DIEM)
playedAt    : long         (epoch milli)
```

#### `LeaderboardItem` — phần tử của `LEADERBOARD_RESULT`
```
rank        : int
userId      : long
displayName : String
totalPoints : BigDecimal   (tổng điểm tích lũy)
totalWins   : int
totalGames  : int
```

> Sắp xếp: `totalPoints DESC` → `totalWins DESC` → `userId ASC` (ổn định).

---

### 3.10 Lỗi

#### `ErrorPayload` — payload của `ERROR`
```
code    : ErrorCode   (enum — UI chỉ so sánh code, không so sánh message)
message : String      (hiển thị cho người dùng hoặc log)
```

---

## 4. Mã Lỗi (ErrorCode)

| Mã | Khi nào xảy ra |
|---|---|
| `INVALID_REQUEST` | Request sai định dạng, thiếu field bắt buộc, hoặc payload sai kiểu |
| `UNAUTHENTICATED` | Chưa đăng nhập mà dùng chức năng cần xác thực |
| `FORBIDDEN` | Đã đăng nhập nhưng không có quyền (ví dụ không phải chủ phòng) |
| `INTERNAL_ERROR` | Lỗi phía server — không lộ stack trace cho client |
| `USERNAME_EXISTS` | Đăng ký trùng username |
| `INVALID_CREDENTIALS` | Sai username hoặc mật khẩu khi đăng nhập |
| `ALREADY_LOGGED_IN` | Tài khoản đang có phiên khác hoạt động |
| `USER_BLOCKED` | Tài khoản bị khóa |
| `ROOM_NOT_FOUND` | roomId không tồn tại hoặc phòng đã đóng |
| `ROOM_FULL` | Phòng đã đủ maxPlayers |
| `ROOM_NOT_WAITING` | Phòng đang chơi, không thể vào |
| `ALREADY_IN_ROOM` | Người dùng đang ở phòng khác |
| `NOT_IN_ROOM` | Yêu cầu cần ở trong phòng mà chưa vào |
| `NOT_ROOM_OWNER` | Cần là chủ phòng để thực hiện thao tác |
| `NOT_ENOUGH_PLAYERS` | Chưa đủ minPlayers để bắt đầu ván |
| `NOT_ALL_READY` | Có người chưa bấm sẵn sàng |
| `INVALID_STATE` | Hành động sai trạng thái hiện tại (ví dụ FOLD khi ván chưa bắt đầu) |
| `STALE_ROUND` | roundId trong request không khớp với ván đang chạy |
| `NOT_YOUR_TURN` | Chưa đến lượt người chơi này đặt cược |
| `INVALID_BET_AMOUNT` | Mức cược âm, bằng 0, hoặc vượt quá số dư |
| `MESSAGE_TOO_LONG` | Nội dung chat vượt 500 ký tự |
| `RATE_LIMIT_EXCEEDED` | Gửi quá 5 tin nhắn trong 10 giây |
| `INVALID_PAGE` | Số trang âm hoặc pageSize không hợp lệ |

---

## 5. Luồng Chính (Flow)

### 5.1 Đăng nhập

```
Client                          Server
  │                               │
  │── LOGIN {username, password} ─►│
  │                               │── UserBUS.authenticate()
  │                               │   ├─ OK  → gắn userId vào session
  │◄─ AUTH_RESULT {success=true} ──│   └─ FAIL → không gắn
  │                               │
  │  (nếu sai thông tin)          │
  │◄─ AUTH_RESULT {success=false, │
  │    errorCode=INVALID_CREDENTIALS}
```

**Quy tắc:**
- Server **không tin** `userId` do client tự khai trong payload. Danh tính lấy từ session sau khi xác thực.
- Nếu tài khoản đã có phiên → trả `ALREADY_LOGGED_IN`, không đá phiên cũ.
- Sau `LOGOUT` hoặc mất kết nối → giải phóng phiên ngay.

---

### 5.2 Tạo phòng và bắt đầu ván

```
Client A (chủ)        Server                  Client B
    │                   │                        │
    │── CREATE_ROOM ───►│                        │
    │◄─ ACK ────────────│                        │
    │◄─ ROOM_UPDATED ───│                        │
    │                   │                        │
    │                   │◄──── JOIN_ROOM ─────────│
    │◄─ ROOM_UPDATED ───│──── ROOM_UPDATED ──────►│
    │                   │                        │
    │── SET_READY ─────►│                        │
    │◄─ ROOM_UPDATED ───│──── ROOM_UPDATED ──────►│
    │                   │◄──── SET_READY ─────────│
    │◄─ ROOM_UPDATED ───│──── ROOM_UPDATED ──────►│
    │                   │                        │
    │── START_GAME_REQUEST ►│                    │
    │                   │── GameEngine.initRound()
    │◄─ START_GAME ─────│──── START_GAME ────────►│
    │◄─ DEAL_CARD* ─────│                        │
    │                   │──────────── DEAL_CARD* ─►│
```

**Quy tắc:**
- `DEAL_CARD` gửi **riêng từng client** — không bao giờ broadcast.
- Server kiểm tra: chủ phòng, đủ minPlayers, tất cả đã sẵn sàng.
- `stateVersion` trong `ROOM_UPDATED` tăng sau mỗi thay đổi.

---

### 5.3 Vòng đặt cược

```
Client A                Server               Client B
    │                     │                     │
    │                     │──── BET_UPDATED ────►│  (lượt A)
    │◄─── BET_UPDATED ────│                     │
    │                     │                     │
    │── GAME_ACTION(RAISE,50) ►│               │
    │                     │── GameEngine.handleAction()
    │◄─ GAME_UPDATED ─────│──── GAME_UPDATED ──►│
    │◄─ BET_UPDATED ──────│──── BET_UPDATED ───►│  (lượt B)
    │◄─ PLAYER_STATE* ────│                     │
    │                     │──────── PLAYER_STATE* ►│
    │                     │                     │
    │  (B fold)           │◄── GAME_ACTION(FOLD) ──│
    │                     │── GameEngine.handleAction()
    │◄─ GAME_UPDATED ─────│──── GAME_UPDATED ──►│
    │◄─ ROUND_RESULT ─────│──── ROUND_RESULT ──►│
```

**Quy tắc:**
- Kiểm tra `roundId` mỗi action — từ chối nếu không khớp (`STALE_ROUND`).
- Cập nhật cùng một phòng phải **tuần tự hóa** (synchronized hoặc single-thread executor).
- `PLAYER_STATE` gửi riêng — chứa bài của người nhận.
- Server lấy `userId` từ session, **không** lấy từ payload của client.

---

### 5.4 Mất kết nối giữa ván

```
Client A (mất mạng)     Server              Client B
    │                     │                    │
    ×  (EOF / timeout)    │                    │
                          │ detect mất kết nối  │
                          │── GameEngine.handlePlayerLeft(userId)
                          │                    │
                          │──── CHAT_SYSTEM ──►│  "Player A đã mất kết nối"
                          │──── GAME_UPDATED ─►│  (nếu còn đủ người tiếp tục)
                          │  hoặc              │
                          │──── ROUND_RESULT ─►│  (nếu không đủ người → CANCELLED)
```

**Quy tắc:**
- Đường dọn trạng thái **thống nhất** cho cả EOF, timeout, `LOGOUT`, `LEAVE_ROOM`.
- Không phát `ROOM_UPDATED` hoặc `CHAT_SYSTEM` nhiều hơn một lần cho cùng một sự kiện rời.
- Ván `CANCELLED` không cộng điểm, nhưng lịch sử tham gia vẫn được lưu.

---

### 5.5 Heartbeat

```
Server                  Client
  │                       │
  │──── PING ────────────►│   (mỗi 5 giây)
  │◄─── PONG ─────────────│   (phải trả trong 15 giây)
  │                       │
  │ (không nhận PONG sau 15s)
  │── đóng kết nối, dọn trạng thái
```

**Quy tắc:**
- Không phân biệt "người dùng im lặng" với "mất kết nối" — PING là cơ chế duy nhất.
- Client **không** gửi PING — chỉ server gửi PING, client trả PONG.

---

## 6. Quy Tắc Bảo Mật

1. **Dữ liệu riêng chỉ gửi đúng người.** `DEAL_CARD`, `PLAYER_STATE` — chỉ gửi qua `ObjectOutputStream` của đúng client đó.
2. **Không broadcast trạng thái nội bộ Game Engine.** Có thể chứa bài chưa lật của người khác.
3. **Không log payload nhạy cảm.** Đặc biệt là password, hash, nội dung `AuthRequest`.
4. **Thời gian từ đồng hồ server.** Chat timestamp, thời gian ván — client không được tự điền.
5. **Kiểm tra kiểu payload.** Mỗi `PacketType` chỉ chấp nhận đúng kiểu tương ứng — sai kiểu → đóng kết nối.
6. **Giới hạn kích thước.** Packet vượt quá ngưỡng hợp lý → đóng kết nối, không để tràn bộ nhớ.

---

## 7. Cấu Hình Mặc Định (Tham Chiếu Nhanh)

| Tham số | Giá trị | Nơi cấu hình |
|---|---|---|
| Server port | `8888` | `server.properties` |
| Max connections | `100` | `server.properties` |
| Heartbeat interval | `5s` | `server.properties` |
| Heartbeat timeout | `15s` | `server.properties` |
| Số người / phòng | `2–8` | `server.properties` + server check |
| Chat max length | `500 ký tự` | `server.properties` |
| Chat rate limit | `5 tin / 10 giây` | `server.properties` |
| Connection timeout (client) | `5s` | `client.properties` |
| Phân trang mặc định | `20` / tối đa `100` | `server.properties` |
| Encoding | `UTF-8` (Java) / `utf8mb4` (MySQL) | Mặc định |

---

## 8. Lịch Sử Thay Đổi

| Phiên bản | Ngày | Nội dung | Người thực hiện |
|---|---|---|---|
| 1.0 | 2026-09-25 | Tạo ban đầu — đủ 9 nhóm thông điệp, payload chi tiết, 5 luồng chính | Khánh |

> Mọi thay đổi payload hoặc PacketType phải thêm dòng vào bảng này và cập nhật `PROTOCOL_VERSION` trong `Packet.java` nếu breaking change.
