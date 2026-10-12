# PROMPT XÂY DỰNG — Game Bài 3 Cây Online (BTL Lập Trình Mạng)

**Phiên bản:** 1.0 — Thành, Trọng, Khánh, Nhật.  
Tài liệu kỹ thuật chi tiết phục vụ triển khai. Đọc cùng với `Ban_giao_viec_BTL_Lap_trinh_mang.md` và `docs/Protocol.md`.

---

## 1. Công Nghệ Chính Thức

| Thành phần | Công nghệ | Ghi chú |
|---|---|---|
| Ngôn ngữ / Build | Java 21, Maven | Project phải build được ngoài IDE |
| Giao diện Client | **Java Swing** | Tách View, Controller, Network |
| Giao tiếp mạng | **TCP Socket** (`java.net.Socket`, `ServerSocket`) | Không dùng WebSocket |
| Định dạng gói tin | `ObjectOutputStream`/`ObjectInputStream` + `Packet implements Serializable` | Không dùng JSON |
| Đa luồng Server | `ExecutorService` có giới hạn kết nối công bố được | 1 luồng đọc/kết nối |
| CSDL | **PostgreSQL** + JDBC (`postgresql-42.x`) | Không dùng SQLite hay file JSON |
| Bảo mật mật khẩu | PBKDF2WithHmacSHA256 với salt ngẫu nhiên | Lớp `PasswordUtil` trong `shared/security/` |
| Kiến trúc | MVC: Client (View–Controller–Network); Server (Network–Room–Game–BUS–DAL) | |

**Không được** đề xuất lại kiến trúc web/WebSocket/HTML/SQLite — không khớp với tiền lệ chấm điểm của giảng viên.

---

## 2. Kiến Trúc Tổng Thể

```
┌─────────────────────────────────────────────────────┐
│                      SERVER                          │
│  RunServer (mở ServerSocket port 8888)               │
│   └─ ClientHandler (1 luồng/client)                  │
│        └─ SessionManager   (Khánh - N03)             │
│        └─ RoomManager      (Khánh - N04)             │
│        └─ GameEngine       (Trọng - G01-G06)         │
│             └─ BettingEngine / SidePotCalculator     │
│        └─ ChatManager      (Khánh - N07)             │
│        └─ UserBUS / GameBUS / BetBUS  (Nhật - D03-D06)│
│        └─ DAL + PostgreSQL JDBC       (Nhật - D02)   │
└───────────────────────┬─────────────────────────────┘
                        │ TCP Socket — Java Serializable (Packet)
          ┌─────────────┼──────────────┐
          │             │              │
   ┌──────┴──────┐ ┌────┴────────┐ ┌──┴──────────┐
   │ Client A    │ │ Client B    │ │ Client C    │
   │ RunClient   │ │ RunClient   │ │ RunClient   │
   │ (Swing)     │ │ (Swing)     │ │ (Swing)     │
   └─────────────┘ └─────────────┘ └─────────────┘
```

- Server chạy 1 tiến trình duy nhất, lắng nghe cổng **8888** (cấu hình được).
- Mỗi phòng hỗ trợ **2 đến 8 người chơi** (`min_players`/`max_players` cấu hình khi tạo phòng).
- Toàn bộ trạng thái ván (deck, lượt tố, pot) giữ trong RAM Server (`GameEngine`); PostgreSQL chỉ lưu dữ liệu bền vững (tài khoản, lịch sử ván, giao dịch điểm).

---

## 3. Giao Thức Gói Tin (Packet — Java Serializable, KHÔNG dùng JSON)

```java
public final class Packet implements Serializable {
    String protocolVersion; // "1.0"
    PacketType type;
    String requestId;       // UUID — client tự sinh cho request; server copy lại khi phản hồi; null cho push
    Object data;            // payload tương ứng type
}
```

### Danh Sách PacketType Bắt Buộc

| Nhóm | PacketType | Chiều | Payload | Ghi chú |
|---|---|---|---|---|
| Hệ thống | `PING`, `PONG`, `ACK`, `ERROR` | S→C / C→S | `ErrorPayload` khi ERROR | Heartbeat mỗi 5s, timeout 15s |
| Auth | `LOGIN`, `REGISTER`, `LOGOUT` | C→S | `AuthRequest` / `RegisterRequest` | |
| Auth | `AUTH_RESULT` | S→C* | `AuthResult` | Không chứa hash/salt |
| Hồ sơ | `GET_PROFILE`, `UPDATE_PROFILE`, `CHANGE_PASSWORD` | C→S | | |
| Hồ sơ | `PROFILE_RESULT` | S→C* | `ProfileResult` | |
| Lobby | `ROOM_LIST` | C→S | null | |
| Lobby | `ROOM_LIST_RESULT` | S→C* | `List<RoomSummary>` | |
| Phòng | `CREATE_ROOM`, `JOIN_ROOM`, `LEAVE_ROOM`, `SET_READY`, `START_GAME_REQUEST` | C→S | | |
| Phòng | `ROOM_UPDATED` | S→C (room) | `RoomSnapshot` | Có `stateVersion` |
| Ván | `START_GAME` | S→C (room) | `GameStartInfo` | Không chứa bài |
| Ván | `DEAL_CARD` | **S→C\*** | `DealCardPayload` | **KHÔNG BAO GIỜ broadcast** |
| Ván | `GAME_ACTION` | C→S | `GameActionRequest` (RAISE/CALL/FOLD/ALL_IN) | |
| Ván | `GAME_UPDATED`, `BET_UPDATED` | S→C (room) | `GameStateSnapshot`, `BetStateSnapshot` | Trạng thái công khai |
| Ván | `PLAYER_STATE` | **S→C\*** | `PlayerStateSnapshot` | Bài + điểm riêng |
| Ván | `ROUND_RESULT` | S→C (room) | `RoundResult` | Lật bài, xếp hạng, profit |
| Chơi tiếp | `PLAY_AGAIN_VOTE`, `PLAY_AGAIN_PROMPT` | C→S / S→C (room) | `Boolean` / `PlayAgainPrompt` | |
| Chat | `CHAT_MESSAGE`, `CHAT_SYSTEM`, `MUTE_PLAYER` | C→S / S→C (room) | `ChatMessage` | Lọc từ ngữ server-side |
| Dữ liệu | `GET_HISTORY`, `GET_LEADERBOARD` | C→S | `PageRequest` | |
| Dữ liệu | `HISTORY_RESULT`, `LEADERBOARD_RESULT` | S→C* | `PageResult<T>` | |

> Xem chi tiết payload từng type trong `docs/Protocol.md`.

---

## 4. Thiết Kế CSDL — PostgreSQL

```sql
-- Khởi tạo:  psql -U postgres -f database/bacaudb.sql
CREATE DATABASE bacaudb ENCODING 'UTF8';
\c bacaudb

-- 1. Người dùng
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50)  UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,          -- PBKDF2, KHÔNG lưu plain text
    display_name  VARCHAR(100),
    email         VARCHAR(100) UNIQUE,
    avatar_url    VARCHAR(255),
    balance       DECIMAL(15,2) DEFAULT 0,
    status        VARCHAR(20)  DEFAULT 'ACTIVE',  -- ACTIVE / BLOCKED
    created_at    TIMESTAMPTZ  DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  DEFAULT NOW()
);

-- 2. Phòng chơi
CREATE TABLE rooms (
    id          BIGSERIAL PRIMARY KEY,
    room_code   VARCHAR(20)   UNIQUE NOT NULL,
    room_name   VARCHAR(100)  NOT NULL,
    owner_id    BIGINT        NOT NULL REFERENCES users(id),
    min_players INT           DEFAULT 2,
    max_players INT           DEFAULT 8,
    bet_amount  DECIMAL(15,2) NOT NULL,
    status      VARCHAR(20)   DEFAULT 'WAITING',  -- WAITING / PLAYING / FINISHED
    created_at  TIMESTAMPTZ   DEFAULT NOW(),
    updated_at  TIMESTAMPTZ   DEFAULT NOW()
);

-- 3. Người chơi trong phòng
CREATE TABLE room_players (
    id          BIGSERIAL PRIMARY KEY,
    room_id     BIGINT NOT NULL REFERENCES rooms(id),
    user_id     BIGINT NOT NULL REFERENCES users(id),
    seat_number INT    NOT NULL,
    joined_at   TIMESTAMPTZ DEFAULT NOW(),
    left_at     TIMESTAMPTZ,
    status      VARCHAR(20) DEFAULT 'PLAYING',    -- PLAYING / SPECTATOR / LEFT
    UNIQUE (room_id, user_id)
);

-- 4. Ván chơi
CREATE TABLE games (
    id          BIGSERIAL PRIMARY KEY,
    round_id    VARCHAR(36)   UNIQUE NOT NULL,    -- UUID, chống ghi lặp
    room_id     BIGINT        NOT NULL REFERENCES rooms(id),
    game_number INT           NOT NULL,
    started_at  TIMESTAMPTZ,
    ended_at    TIMESTAMPTZ,
    status      VARCHAR(20)   DEFAULT 'WAITING',  -- WAITING/PLAYING/FINISHED/CANCELLED
    base_point  DECIMAL(15,2) NOT NULL
);

-- 5. Người chơi trong ván
CREATE TABLE game_players (
    id               BIGSERIAL PRIMARY KEY,
    game_id          BIGINT NOT NULL REFERENCES games(id),
    user_id          BIGINT NOT NULL REFERENCES users(id),
    seat_number      INT    NOT NULL,
    score            INT,
    hand_type        VARCHAR(20),                 -- SAP / LIENG / BO_DOI / DIEM
    result           VARCHAR(20),                 -- WIN / LOSE / DRAW / FOLD
    profit           DECIMAL(15,2),
    final_multiplier DECIMAL(8,2),
    UNIQUE (game_id, user_id)                     -- chống ghi lặp cùng người trong ván
);

CREATE TABLE cards (           -- 52 lá bài tham chiếu (tĩnh, DO block nạp sẵn)
    id INT PK, suit VARCHAR(10), rank VARCHAR(5),
    point INT, rank_value INT, suit_value INT
);
CREATE TABLE game_cards (      -- lịch sử chia bài, ghi khi ván kết thúc
    game_id BIGINT FK, user_id BIGINT FK, card_id INT FK,
    card_position INT (1-3), revealed BOOLEAN,
    UNIQUE (game_id, user_id, card_position),
    UNIQUE (game_id, card_id)
);
CREATE TABLE point_bets (      -- lịch sử tố chi tiết
    game_id BIGINT FK, user_id BIGINT FK,
    target_user_id BIGINT FK nullable,
    action VARCHAR(20),        -- RAISE/CALL/FOLD/ALL_IN/ANTE
    raised_point DECIMAL(15,2), raise_amount DECIMAL(15,2),
    multiplier_before DECIMAL(8,2), multiplier_after DECIMAL(8,2),
    sequence_no INT, created_at TIMESTAMPTZ
);
CREATE TABLE transactions (    -- audit trail đầy đủ
    user_id BIGINT FK, game_id BIGINT FK nullable,
    type VARCHAR(20),          -- BET/WIN/LOSE/REFUND
    amount DECIMAL(15,2),
    balance_before DECIMAL(15,2), balance_after DECIMAL(15,2),
    created_at TIMESTAMPTZ
);
-- games: có round_id VARCHAR(36) UNIQUE (chống ghi lặp), KHÔNG có winner_id
-- game_players: hand_type = SAP/LIENG/BO_DOI/DIEM (không phải NORMAL)
-- rooms: CHECK (min_players <= max_players); seat_number CHECK (1..8)
-- users: CHECK (balance >= 0)
);
```

**Dependency JDBC trong `server/pom.xml`:**
```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.3</version>
</dependency>
```

---

## 5. Model Dùng Chung (`shared/`)

```
shared/
├── network/
│   ├── Packet.java              (protocolVersion, type, requestId, data)
│   ├── PacketType.java          (enum — 9 nhóm, 30+ type)
│   └── ErrorCode.java           (enum — 23 mã lỗi)
├── model/
│   ├── Card.java                (Rank + Suit, immutable)
│   ├── Rank.java                (TWO..ACE, rankValue, pointValue)
│   ├── Suit.java                (CHUON/BICH/CO/RO, compareValue)
│   ├── Hand.java                (3 Card + HandType + score)
│   ├── HandType.java            (SAP/LIENG/BO_DOI/DIEM, strength)
│   ├── BetAction.java           (RAISE/CALL/FOLD/ALL_IN/ANTE)
│   ├── BetRecord.java           (1 lượt tố → 1 dòng point_bets; sequenceNo, multiplier)
│   ├── RoomSnapshot.java        (trạng thái phòng công khai + stateVersion)
│   ├── BetStateSnapshot.java    (pot, lượt, mức cược — công khai)
│   ├── GameStateSnapshot.java   (phase, currentTurn, activePlayers — công khai)
│   ├── PlayerStateSnapshot.java (bài + số dư — bí mật, chỉ gửi riêng)
│   ├── RoundResult.java         (playerResults + betHistory + sidePots — sau ván)
│   ├── PlayerResult.java        (hand, rank, result, profit — một người)
│   ├── SidePot.java             (amount + eligiblePlayerIds)
│   ├── ChatMessage.java         (senderId, content, type, timestampMillis)
│   ├── PageResult.java          (generic, dùng cho History + Leaderboard)
│   ├── GameHistoryItem.java     (một dòng lịch sử ván)
│   └── LeaderboardItem.java     (một hàng bảng xếp hạng)
└── security/
    └── PasswordUtil.java        (hash/verify PBKDF2WithHmacSHA256)
```

---

## 6. Logic Nghiệp Vụ Bắt Buộc

### 6.1 Xác định loại bài — `HandEvaluator` (Trọng - G02)

| Loại | Điều kiện |
|---|---|
| **Sáp** | 3 lá cùng Rank; A-A-A cao nhất |
| **Liêng** | 3 lá liên tiếp theo Tiến Lên: A-2-3 (thấp nhất) → ... → Q-K-A (cao nhất). **Ace vừa đứng đầu vừa đứng cuối:** A-2-3 ✅ và Q-K-A ✅ đều hợp lệ; K-A-2 ❌ không hợp lệ. |
| **Bồ đội** | Cả 3 lá thuộc {J, Q, K}; không cần thứ tự |
| **Điểm** | Tổng điểm lấy hàng đơn vị; J/Q/K/10 = 0; 9 là cao nhất |

### 6.2 So sánh — `HandComparator` (Trọng - G03)

Thứ tự: **Loại bài** (`SAP > LIENG > BO_DOI > DIEM`) → giá trị trong loại → **chất** (`RO > CO > BICH > CHUON`) của lá cao nhất, so tuần tự nếu vẫn hoà.

### 6.3 Luật tố — `BettingEngine` (Trọng - G03, phối hợp Khánh - N05)

- Trước ván: mọi người đặt `ante` = `bet_amount`, ghi `transactions` type `BET`.
- Vòng tố theo chiều kim đồng hồ từ người kế người chia: RAISE / CALL / FOLD / ALL_IN.
- Mức tố ≥ mức cược cao nhất hiện tại; ≤ giới hạn bàn (nếu có) hoặc số dư còn lại (ALL_IN).
- Vòng tố kết thúc khi tất cả còn lại đặt bằng nhau, hoặc chỉ còn 1 người chưa FOLD.
- 1 người còn lại chưa FOLD → thắng ngay, nhận toàn bộ pot, **không bắt buộc lật bài**.
- ≥ 2 người còn lại → so bài theo `HandComparator` để chia pot.

### 6.4 Side pot — `SidePotCalculator` (Trọng - G04)

Nhiều mức ALL_IN khác nhau → tính **side pot**: mỗi side pot chỉ những người góp đủ mức đó mới được tranh phần.

### 6.5 Chat — `ChatManager` (Khánh - N07)

- Mỗi phòng có kênh chat riêng — chỉ thành viên/spectator trong phòng nhận được.
- Hỗ trợ TEXT + EMOJI (danh sách emoji cố định, gửi bằng mã code).
- Server tự chèn `CHAT_SYSTEM`: vào/rời phòng, bắt đầu ván, kết quả ván, hành động tố/theo/úp.
- Lọc từ ngữ nhạy cảm/spam trước khi broadcast (danh sách từ cấm cấu hình được).
- Mặc định **không lưu lâu dài** lịch sử chat (chỉ giữ RAM theo phiên phòng); ghi vào `chat_messages` khi `ENABLE_CHAT_LOG=true`.

---

## 7. Cấu Trúc Thư Mục Maven

```
bacay-online/                     ← Parent Maven (pom.xml)
├── shared/                       ← Module dùng chung
│   └── src/main/java/com/bacay/shared/
│       ├── network/              (Packet, PacketType, ErrorCode)
│       ├── model/                (Card, Hand, RoomSnapshot, ...)
│       └── security/             (PasswordUtil)
├── server/                       ← Module server
│   └── src/main/java/com/bacay/server/
│       ├── RunServer.java
│       ├── network/              (ClientHandler, SessionManager, HeartbeatManager)
│       ├── room/                 (RoomManager, ChatManager)
│       ├── game/                 (GameEngine, BettingEngine, SidePotCalculator,
│       │                          HandEvaluator, HandComparator)
│       ├── bus/                  (UserBUS, RoomBUS, GameBUS, BetBUS)
│       └── dal/                  (UserDAL, RoomDAL, GameDAL, BetDAL,
│                                  TransactionDAL, ChatDAL, DbConnector)
├── client/                       ← Module client
│   └── src/main/java/com/bacay/client/
│       ├── RunClient.java
│       ├── controller/           (SocketHandler)
│       └── view/                 (LoginFrame, RegisterFrame, LobbyFrame,
│                                  RoomFrame, InGameFrame, ProfileFrame,
│                                  HistoryFrame, RankingFrame)
├── database/
│   └── bacaudb.sql               (Script tạo schema PostgreSQL — mục 4)
└── docs/
    ├── Protocol.md               (Giao thức chi tiết — Khánh)
    ├── Game_Rules.md             (Luật game — Trọng)
    └── Database.md               (Schema + hướng dẫn DB — Nhật)
```

---

## 8. Lộ Trình Triển Khai (M0 → M6)

Khớp với bảng mốc trong `Ban_giao_viec_BTL_Lap_trinh_mang.md` và file tiến độ CSV.

| Mốc | Nội dung | Ai làm |
|---|---|---|
| **M0** | Khung Maven build được; `Packet`/`PacketType`/`shared`; `Protocol.md`; schema DB phác thảo; luật game chốt; hợp đồng BUS | Cả nhóm — Khánh tổng hợp |
| **M1** | Client kết nối TCP, gửi/nhận nhiều Packet, nhận sự kiện server push; PING/PONG | Thành + Khánh (T01, N01, N02, N06) |
| **M2** | Đăng ký / đăng nhập Swing → server → PostgreSQL | Thành + Khánh + Nhật (T02, N03, D01-D03) |
| **M3.1** | Lobby, tạo/vào/rời phòng, sẵn sàng; tranh chỗ cuối; chủ phòng rời | Thành + Khánh (T03, N04) |
| **M3.2** | Chat đúng phòng; emoji; giới hạn độ dài/tần suất | Thành + Khánh (T05, N07) |
| **M4.1** | Bắt đầu ván, đặt cược, kết thúc, chơi tiếp; side pot | Thành + Khánh + Trọng (T04, N05, G01-G04, G06) |
| **M4.2** | Dữ liệu riêng (DEAL_CARD, PLAYER_STATE); rời/mất kết nối giữa ván | Thành + Khánh + Trọng (T07, N05-N06, G05) |
| **M5.1** | Lưu kết quả, cập nhật thống kê, chống ghi lặp theo `round_id` | Khánh + Trọng + Nhật (N08, G04, D04) |
| **M5.2** | Hồ sơ, lịch sử, bảng xếp hạng | Thành + Khánh + Nhật (T02, T06, N08, D03, D05) |
| **M6** | Đóng gói, chạy LAN ≥4 client/2 máy, hoàn thiện README, kịch bản demo | Cả nhóm |

**Giai đoạn chi tiết để dev theo:**

| Bước | Nội dung |
|---|---|
| 1 | Tạo DB PostgreSQL (schema mục 4), viết `DbConnector`, DAL cơ bản (Users) |
| 2 | Khung Socket: `RunServer`/`RunClient`, `Packet`, `LOGIN`/`REGISTER` chạy end-to-end |
| 3 | Lobby: `CREATE_ROOM`/`JOIN_ROOM`/`ROOM_LIST`, hỗ trợ 2-8 người, broadcast `ROOM_UPDATED` |
| 4 | `HandEvaluator`/`HandComparator` — viết **JUnit test riêng** trước khi tích hợp |
| 5 | Chia bài (`DEAL_CARD` riêng từng client) + tích hợp so bài, `ROUND_RESULT` |
| 6 | `BettingEngine`: vòng tố raise/call/fold/all-in, `SidePotCalculator`, ghi `point_bets`/`transactions` |
| 7 | `ChatManager`: tin nhắn, emoji, mute, lọc từ, thông báo hệ thống |
| 8 | Hồ sơ, lịch sử (`GET_HISTORY` phân trang), bảng xếp hạng (`GET_LEADERBOARD`) |
| 9 | Xử lý ngắt kết nối/thoát giữa ván, spectator khi hết điểm |
| 10 | Kiểm thử tích hợp nhiều Client trên LAN, hoàn thiện giao diện Swing, viết báo cáo |

---

## 9. Tiêu Chí Nghiệm Thu (Definition of Done)

- [ ] Chạy 1 `RunServer` + tối thiểu 2-8 `RunClient` (Swing) trên cùng LAN, chơi trọn 1 ván từ tạo phòng đến chia pot.
- [ ] Vòng tố xử lý đúng raise/call/fold/all-in; side pot chia đúng khi nhiều mức all-in khác nhau.
- [ ] Không client nào nhận được bài của người khác trước khi kết quả được công bố.
- [ ] Chat hoạt động realtime trong phòng; lọc được từ cấm; mute hoạt động phía client mute.
- [ ] Người chơi hết điểm tự chuyển trạng thái Spectator, không bị văng khỏi phòng.
- [ ] Thoát Client giữa ván → Server phát hiện, thông báo người còn lại, cập nhật đúng trạng thái phòng.
- [ ] Tắt/bật lại Server không mất dữ liệu người dùng/lịch sử (đọc lại từ PostgreSQL).
- [ ] Bảng xếp hạng và lịch sử dùng dữ liệu PostgreSQL, sắp xếp đúng theo tổng điểm rồi đến số trận thắng.
- [ ] Mọi mốc thời gian hiển thị lấy từ đồng hồ Server.
- [ ] Thử lưu cùng `round_id` hai lần không ghi kết quả hai lần.

---

## 10. Phân Chia Công Việc

| Thành viên | Module chính | Bàn giao |
|---|---|---|
| **Nhật** | D01-D06: Schema PostgreSQL, `DbConnector`, DAL/BUS cho Users; đăng ký/đăng nhập; hồ sơ cá nhân; lưu kết quả; lịch sử; bảng xếp hạng | Script SQL, `Database.md`, interface BUS + mã lỗi, kết quả kiểm thử |
| **Khánh** | N01-N08: `RunServer`, kết nối đa client, giao thức, xác thực phiên, phòng, điều phối game, chat, heartbeat, ghép dữ liệu | `RunServer`, `shared/`, `Protocol.md`, cấu hình mẫu, hướng dẫn chạy LAN |
| **Trọng** | G01-G06: `HandEvaluator`, `HandComparator`, `BettingEngine`, `SidePotCalculator`, `GameEngine`, vòng đời ván, kết quả | `Game_Rules.md`, Engine API doc, JUnit test luật bài, mẫu snapshot/kết quả |
| **Thành** | T01-T07: `RunClient`, tất cả JFrame (Login, Lobby, Room, InGame, Profile, History, Ranking), `SocketHandler`, luồng EDT | Client JAR, hướng dẫn thao tác, kịch bản demo UI |

---

*Tóm tắt thay đổi so với bản trước: chuyển hoàn toàn từ MySQL sang **PostgreSQL** (cú pháp `BIGSERIAL`, `TIMESTAMPTZ`, `DEFAULT NOW()`); bỏ quy trình web/WebSocket; bổ sung đầy đủ tên thành viên và mã module (T/G/N/D); đồng bộ mốc M0-M6 với `Ban_giao_viec_BTL_Lap_trinh_mang.md` và file tiến độ CSV; `Packet` bổ sung `protocolVersion` và `requestId` theo `Protocol.md` v1.0.*