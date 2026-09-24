# Screen_Data_Mapping.md — Màn Hình & Dữ Liệu Cần Nhận

**Người phụ trách:** Thành (module T01, T04)  
**Cập nhật lần cuối:** 2026-09-25

> Tài liệu này xác nhận các màn hình Swing cần triển khai và dữ liệu
> phải nhận từ server cho mỗi màn hình. Đây là cơ sở để Khánh xác nhận
> `Protocol.md` đã đủ và để Trọng biết dữ liệu riêng nào cần cung cấp.

---

## 1. Danh Sách Màn Hình

| # | JFrame | Module | Mô tả |
|---|---|---|---|
| 1 | `LoginFrame` | T02 | Đăng nhập |
| 2 | `RegisterFrame` | T02 | Đăng ký tài khoản |
| 3 | `LobbyFrame` | T03 | Danh sách phòng chơi |
| 4 | `RoomFrame` | T03 | Phòng chờ (trước khi bắt đầu ván) |
| 5 | `InGameFrame` | T04 | Màn hình chơi chính (bài, cược, chat) |
| 6 | `ProfileFrame` | T02 | Hồ sơ cá nhân + đổi mật khẩu |
| 7 | `HistoryFrame` | T06 | Lịch sử ván đã chơi |
| 8 | `RankingFrame` | T06 | Bảng xếp hạng |

---

## 2. Dữ Liệu Cần Nhận Theo Màn Hình

### 2.1 LoginFrame / RegisterFrame

**Gửi lên server:**
- `LOGIN` → `AuthRequest { username, password }`
- `REGISTER` → `RegisterRequest { username, password, displayName, email }`

**Nhận từ server:**
- `AUTH_RESULT` → `AuthResult { success, userId, username, displayName, balance, errorCode, message }`

**Hiển thị:**
- Thành công → lưu `userId`, `displayName`, `balance` vào session client → mở `LobbyFrame`
- Thất bại → hiển thị `message` trong label lỗi

---

### 2.2 LobbyFrame

**Gửi lên server:**
- `ROOM_LIST` (null payload) — gọi khi mở frame và khi nhấn Refresh
- `CREATE_ROOM` → `CreateRoomRequest { roomName, minPlayers, maxPlayers, betAmount }`
- `JOIN_ROOM` → `JoinRoomRequest { roomId }`

**Nhận từ server:**
- `ROOM_LIST_RESULT` → `List<RoomSummary>` — hiển thị bảng danh sách phòng

**Mỗi hàng bảng hiển thị:** `roomCode | roomName | currentPlayers/maxPlayers | betAmount | status | [Vào]`

---

### 2.3 RoomFrame (Phòng Chờ)

**Nhận từ server (push, không cần request):**
- `ROOM_UPDATED` → `RoomSnapshot { roomId, roomCode, roomName, status, ownerUserId, players[], betAmount, stateVersion }`

**Hiển thị từ `RoomSnapshot`:**
- Danh sách `PlayerSlot`: tên + trạng thái sẵn sàng (✓/✗) cho từng ghế
- Nút "Sẵn sàng" / "Bỏ sẵn sàng" — gửi `SET_READY` với `Boolean`
- Nút "Bắt đầu" (chỉ hiện với chủ phòng, khi đủ người và tất cả sẵn sàng) — gửi `START_GAME_REQUEST`
- Nút "Rời phòng" — gửi `LEAVE_ROOM`
- Chat box (xem mục 2.5)

**Cách hiển thị `stateVersion`:** client so sánh — bỏ qua nếu nhận bản có version thấp hơn bản đang hiển thị.

---

### 2.4 InGameFrame (Màn Hình Chơi)

**Dữ liệu riêng — chỉ người nhận thấy:**
- `DEAL_CARD` → `DealCardPayload { roundId, cards[3] }` — hiển thị 3 lá bài của mình
- `PLAYER_STATE` → `PlayerStateSnapshot { roundId, myCards, myBalance, myCurrentBet, folded, allIn, stateVersion }` — cập nhật sau mỗi action

**Dữ liệu công khai — tất cả trong phòng thấy:**
- `START_GAME` → `GameStartInfo { roundId, playerIds[], startedAt }` — biết ai trong ván
- `GAME_UPDATED` → `GameStateSnapshot { phase, currentTurnUserId, activePlayers[], foldedCount, stateVersion }`
- `BET_UPDATED` → `BetStateSnapshot { currentTurnUserId, currentHighBet, mainPot, sidePots[], playerBets[] }`
- `ROUND_RESULT` → `RoundResult { playerResults[], roundStatus, endedAtMillis }` — lật bài kết quả
- `CHAT_MESSAGE`, `CHAT_SYSTEM` → chat box

**Hành động người dùng gửi lên:**
- `GAME_ACTION` → `GameActionRequest { roundId, action, raiseAmount }`
  - action: RAISE / CALL / FOLD / ALL_IN
  - Nút RAISE chỉ hiện khi đến lượt và `phase = BETTING`
  - Nút ALL_IN disabled nếu `allIn = true`
- `CHAT_MESSAGE` → `ChatMessage { content, type }`
- `PLAY_AGAIN_VOTE` → `Boolean`

**Hiển thị bài đối thủ:**
- Trong phase BETTING: hiển thị mặt sau (úp)
- Khi nhận `ROUND_RESULT`: lật mặt theo `PlayerResult.hand` (chỉ những người không FOLD trước showdown)

---

### 2.5 Chat Box (dùng chung trong RoomFrame và InGameFrame)

**Nhận:**
- `CHAT_MESSAGE` — hiển thị `[displayName]: content` + timestamp
- `CHAT_SYSTEM` — hiển thị in nghiêng, màu khác

**Gửi:**
- `CHAT_MESSAGE` → `ChatMessage { content, type=TEXT }` — giới hạn 500 ký tự, hiển thị đếm ký tự còn lại
- Emoji: nút emoji gửi `ChatMessage { content=":heart:", type=EMOJI }`
- Mute: `MUTE_PLAYER { targetUserId }` — chỉ ẩn phía client, không gửi server broadcast

**Giới hạn hiển thị:** tối đa 200 tin nhắn trong scroll, cũ hơn tự ẩn.

---

### 2.6 ProfileFrame

**Gửi:**
- `GET_PROFILE` (null) — khi mở frame
- `UPDATE_PROFILE` → `UpdateProfileRequest { displayName, email, avatarUrl }`
- `CHANGE_PASSWORD` → `ChangePasswordRequest { currentPassword, newPassword }`

**Nhận:**
- `PROFILE_RESULT` → `ProfileResult { userId, username, displayName, email, avatarUrl, balance, totalGames, totalWins, totalLosses }`
- `ACK` hoặc `ERROR` → phản hồi sau cập nhật

---

### 2.7 HistoryFrame

**Gửi:**
- `GET_HISTORY` → `PageRequest { page, pageSize=20 }`

**Nhận:**
- `HISTORY_RESULT` → `PageResult<GameHistoryItem>` — bảng lịch sử

**Cột bảng:** `roomName | result | profit | handType | playedAt | playerCount`  
**Phân trang:** nút Trước/Sau dựa vào `totalPages`.

---

### 2.8 RankingFrame

**Gửi:**
- `GET_LEADERBOARD` → `PageRequest { page, pageSize=20 }`

**Nhận:**
- `LEADERBOARD_RESULT` → `PageResult<LeaderboardItem>` — bảng xếp hạng

**Cột bảng:** `#rank | displayName | totalPoints | totalWins | totalGames`  
**Highlight:** dòng của chính mình (dựa vào `userId` trong session).

---

## 3. Quy Tắc Kỹ Thuật Quan Trọng (Thành Lưu Ý)

| Quy tắc | Chi tiết |
|---|---|
| **EDT** | Mọi thao tác UI phải trong EDT (`SwingUtilities.invokeLater`). Socket đọc trên luồng riêng. |
| **SocketHandler** | Luồng nền đọc packet, dùng `invokeLater` gọi callback trên EDT |
| **stateVersion** | Bỏ qua `ROOM_UPDATED` hoặc snapshot cũ hơn bản đang hiển thị |
| **roundId** | Lưu `roundId` khi nhận `START_GAME`, đính kèm vào mọi `GAME_ACTION` |
| **DEAL_CARD** | Chỉ hiển thị bài của mình — không dùng để hiển thị bài người khác |
| **Timestamp** | Dùng `timestampMillis` từ server để hiển thị thời gian, không dùng `System.currentTimeMillis()` |

---

## 4. Xác Nhận với Khánh

Thành xác nhận các payload trong `docs/Protocol.md` phần 3 đủ để render các màn hình trên:

- [x] `RoomSnapshot` (mục 3.4) — đủ cho `RoomFrame`
- [x] `BetStateSnapshot` (mục 3.5) — đủ cho pot/lượt trong `InGameFrame`
- [x] `GameStateSnapshot` (mục 3.5) — đủ cho phase/lượt
- [x] `PlayerStateSnapshot` (mục 3.5) — đủ cho bài + số dư riêng
- [x] `RoundResult` + `PlayerResult` (mục 3.7) — đủ cho showdown

> Nếu cần thêm field, Thành ghi vào đây và báo Khánh cập nhật `Protocol.md`.
