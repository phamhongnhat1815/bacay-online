# Database.md — Hướng Dẫn Thiết Lập CSDL PostgreSQL

**Người phụ trách:** Nhật (module D01–D02)  
**Schema version:** 1.1  
**Cập nhật lần cuối:** 2026-09-27

---

## 1. Yêu Cầu

| Công cụ | Phiên bản | Ghi chú |
|---|---|---|
| PostgreSQL | 15+ | Khuyên dùng 16 LTS |
| JDBC Driver | `postgresql-42.7.3` | Đã khai báo trong `pom.xml` |
| Java | 21 | Cần để chạy server |

---

## 2. Cài Đặt PostgreSQL

### Windows
```powershell
winget install PostgreSQL.PostgreSQL
# Hoặc tải installer từ https://www.postgresql.org/download/windows/
```

### Khởi động service sau khi cài
```powershell
net start postgresql-x64-16   # tên service có thể khác theo phiên bản
```

---

## 3. Tạo Database

```bash
# Chạy trực tiếp từ terminal:
psql -U postgres -f "C:\...\bacay-online\database\bacaudb.sql"

# Hoặc trong psql:
\i /đường/dẫn/bacaudb.sql
```

> ⚠️ File `bacaudb.sql` có lệnh `CREATE DATABASE bacaudb` — chỉ chạy **1 lần đầu**.  
> Để reset sạch: `DROP DATABASE IF EXISTS bacaudb;` rồi chạy lại file.

---

## 4. Tạo User Riêng (Khuyên Dùng)

```sql
-- Trong psql với quyền superuser:
CREATE USER bacay_user WITH PASSWORD 'your_strong_password';
GRANT ALL PRIVILEGES ON DATABASE bacaudb TO bacay_user;
\c bacaudb
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO bacay_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO bacay_user;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO bacay_user;
```

---

## 5. Cấu Hình Kết Nối Server

Điền vào `server/src/main/resources/server.properties`:

```properties
db.url=jdbc:postgresql://localhost:5432/bacaudb
db.username=bacay_user
db.password=your_strong_password
db.poolSize=10
```

> ⚠️ **Không commit `server.properties` có mật khẩu thật** — file này đã có trong `.gitignore`.

---

## 6. Schema v1.1 — 11 Bảng

```
0. cards            ← bảng tham chiếu 52 lá bài (dữ liệu tĩnh, không đổi)
1. users            ← tài khoản người dùng
2. rooms            ← phòng chơi
3. room_players     ← lịch sử vào/rời phòng
4. games            ← ván chơi  (round_id UNIQUE — chống ghi lặp)
5. game_players     ← kết quả mỗi người trong ván
6. game_cards       ← lịch sử chia bài (ghi khi ván kết thúc, phục vụ audit)
7. point_bets       ← từng lượt tố: RAISE/CALL/FOLD/ALL_IN/ANTE + multiplier
8. side_pots        ← side pot khi có ALL_IN nhiều mức
   side_pot_players ← người được tranh từng side pot
9. transactions     ← giao dịch điểm (balance_before + balance_after)
10. chat_messages   ← lịch sử chat (chỉ ghi khi enable_chat_log=true)
```

Xem DDL đầy đủ trong [`database/bacaudb.sql`](../database/bacaudb.sql).

---

## 7. ERD

```
cards ──────────────────< game_cards >──── games
                                            │
users ──< room_players >── rooms ──────────┤
   │                                        │
   └──< game_players >─────────────────────┤
   │                                        │
   └──< point_bets >────────────────────────┤
   │                                        │
   │        games ──< side_pots ──< side_pot_players ──── users
   │
   └──< transactions >──── games (nullable)

rooms ──< chat_messages ──── users (nullable sender)
```

### Ràng buộc quan trọng

| Ràng buộc | Bảng / Cột | Ý nghĩa |
|---|---|---|
| `UNIQUE` | `games.round_id` | Chống ghi lặp kết quả — lớp DB |
| `UNIQUE` | `game_players(game_id, user_id)` | 1 bản ghi/người/ván |
| `UNIQUE` | `game_cards(game_id, card_id)` | Mỗi lá chỉ chia 1 lần/ván |
| `UNIQUE` | `game_cards(game_id, user_id, card_position)` | Đúng 3 lá/người/ván |
| `UNIQUE` | `room_players(room_id, user_id)` | Không ngồi 2 ghế cùng lúc |
| `CHECK` | `users.balance >= 0` | Không cho số dư âm ở tầng DB |
| `CHECK` | `rooms.min_players BETWEEN 2 AND 8` | Ràng buộc quy mô phòng |
| `CHECK` | `rooms.min_players <= max_players` | Tính nhất quán min/max |
| `CHECK` | `seat_number BETWEEN 1 AND 8` | Ghế hợp lệ |

---

## 8. Mô Tả Chi Tiết Các Bảng

### `cards` — Tham chiếu 52 lá bài

| Cột | Kiểu | Mô tả |
|---|---|---|
| `id` | INT PK | 1–52 (suit_order×13 + rank_order) |
| `suit` | VARCHAR(10) | CHUON / BICH / CO / RO |
| `rank` | VARCHAR(5) | A / 2-10 / J / Q / K |
| `point` | INT | Điểm Ba Cây: A=1, 2-9=mặt bài, 10/J/Q/K=0 |
| `rank_value` | INT | Thứ tự tự nhiên: A=1, 2=2 … K=13 |
| `suit_value` | INT | Cho so sánh chất: CHUON=1, BICH=2, CO=3, RO=4 |

> Bảng này chỉ cần đọc — không bao giờ INSERT/UPDATE/DELETE sau khi tạo DB.

### `games` — Ván chơi

| Cột | Kiểu | Mô tả |
|---|---|---|
| `round_id` | VARCHAR(36) UNIQUE | UUID do server sinh trước khi gọi GameEngine |
| `status` | VARCHAR(20) | WAITING / PLAYING / FINISHED / CANCELLED |
| `base_point` | DECIMAL(15,2) | Mức ante thực tế (khác `rooms.bet_amount` nếu chủ phòng đổi) |

> `winner_id` **không có** trong bảng này — truy vấn `game_players WHERE result='WIN'` thay thế.

### `game_cards` — Lịch sử chia bài

| Cột | Kiểu | Mô tả |
|---|---|---|
| `card_position` | INT (1–3) | Thứ tự lá bài của người đó |
| `revealed` | BOOLEAN | TRUE = lá đã lật ở showdown; FALSE = bài úp (FOLD) |

> Ghi **sau khi ván kết thúc** (trong transaction của `GameBUS.saveResult()`), không ghi trong realtime.  
> Lấy dữ liệu từ `RoundResult.playerResults[i].hand()` trong Java.

### `point_bets` — Lịch sử tố

| Cột | Kiểu | Mô tả |
|---|---|---|
| `target_user_id` | BIGINT nullable | Đối thủ cụ thể nếu luật yêu cầu; null = tố toàn bàn |
| `raised_point` | DECIMAL(15,2) | Mức cược tích lũy sau action |
| `raise_amount` | DECIMAL(15,2) | Số điểm tăng thêm so với lượt trước |
| `multiplier_before/after` | DECIMAL(8,2) | Hệ số nhân trước và sau action |
| `sequence_no` | INT | Thứ tự action trong ván (ANTE đầu = 1) |

### `transactions` — Giao dịch điểm

| Cột | Kiểu | Mô tả |
|---|---|---|
| `balance_before` | DECIMAL(15,2) | Số dư trước giao dịch |
| `balance_after` | DECIMAL(15,2) | Số dư sau giao dịch (before + amount = after) |

> Ghi ít nhất 2 dòng/người/ván: BET (âm, khi ante) và WIN/LOSE (dương/âm, khi kết thúc).

---

## 9. Quy Tắc Triển Khai BUS/DAL (Nhật)

### DbConnector (D02)
```java
// Connection pool tối giản: synchronized pool, maxSize = db.poolSize
// Mỗi request BUS lấy 1 connection từ pool → dùng → trả lại (try-with-resources)
// Không chia sẻ Connection giữa các thread
```

### saveResult — 8 bước trong 1 transaction (D04)
```java
conn.setAutoCommit(false);
try {
    // 1. INSERT INTO games (round_id UNIQUE) — lỗi ngay nếu ghi lặp
    // 2. INSERT INTO game_players — kết quả từng người
    // 3. INSERT INTO game_cards  — 3 lá/người từ PlayerResult.hand()
    // 4. INSERT INTO point_bets  — toàn bộ RoundResult.betHistory()
    // 5. INSERT INTO side_pots + side_pot_players — nếu sidePots không rỗng
    // 6. INSERT INTO transactions — BET (âm) rồi WIN/LOSE (dương/âm) với balance_before
    // 7. UPDATE users SET balance = balance + profit WHERE id = userId
    conn.commit();
} catch (Exception e) {
    conn.rollback();   // Rollback toàn bộ — không ghi lửng
    throw new BUSException(ErrorCode.INTERNAL_ERROR, e.getMessage(), e);
}
```

### Kiểm tra balance trước khi UPDATE (D04)
```sql
-- Dùng SELECT ... FOR UPDATE để lock hàng trước khi trừ điểm
SELECT balance FROM users WHERE id = ? FOR UPDATE;
-- Nếu balance + delta < 0 → ném BUSException INVALID_BET_AMOUNT ngay
-- Không để DB constraint bắt lỗi thay (mặc dù CHECK (balance >= 0) vẫn có)
```

### Phân trang — Lịch sử ván (D05)
```sql
SELECT g.id, g.round_id, r.room_name, gp.result, gp.profit,
       gp.hand_type, g.ended_at,
       COUNT(*) OVER (PARTITION BY g.id) AS player_count
FROM games g
JOIN rooms r        ON r.id = g.room_id
JOIN game_players gp ON gp.game_id = g.id AND gp.user_id = ?
WHERE g.status = 'FINISHED'
ORDER BY g.ended_at DESC
LIMIT ? OFFSET ?;

-- Đếm tổng để tính totalPages:
SELECT COUNT(*) FROM games g
JOIN game_players gp ON gp.game_id = g.id AND gp.user_id = ?
WHERE g.status = 'FINISHED';
```

### Phân trang — Bảng xếp hạng (D05)
```sql
WITH stats AS (
    SELECT
        gp.user_id,
        COALESCE(SUM(CASE WHEN gp.profit > 0 THEN gp.profit ELSE 0 END), 0) AS total_points,
        COUNT(CASE WHEN gp.result = 'WIN'  THEN 1 END)  AS total_wins,
        COUNT(CASE WHEN gp.result = 'LOSE' THEN 1 END)  AS total_losses,
        COUNT(gp.game_id)                                AS total_games
    FROM game_players gp
    GROUP BY gp.user_id
)
SELECT
    ROW_NUMBER() OVER (ORDER BY s.total_points DESC, s.total_wins DESC, u.id ASC) AS rank,
    u.id, u.display_name, s.total_points, s.total_wins, s.total_losses, s.total_games
FROM users u
LEFT JOIN stats s ON s.user_id = u.id
WHERE u.status = 'ACTIVE'
ORDER BY s.total_points DESC NULLS LAST, s.total_wins DESC NULLS LAST, u.id ASC
LIMIT ? OFFSET ?;
```

---

## 10. Kiểm Tra Nhanh

```bash
# Liệt kê bảng (nên thấy 11 bảng + cards đã có 52 hàng)
psql -U bacay_user -d bacaudb -c "\dt"
psql -U bacay_user -d bacaudb -c "SELECT COUNT(*) FROM cards;"   -- kết quả: 52

# Xem dữ liệu mẫu
psql -U bacay_user -d bacaudb -c "SELECT id, username, display_name, balance FROM users;"
```

Thay `PLACEHOLDER_HASH` bằng kết quả của `PasswordUtil.hash("password")` trước khi test.

---

## 11. Lịch Sử Thay Đổi Schema

| Phiên bản | Ngày | Nội dung |
|---|---|---|
| 1.0 | 2026-09-25 | Tạo ban đầu — 9 bảng PostgreSQL (chuyển từ MySQL) |
| 1.1 | 2026-09-27 | Thêm `cards` (52 lá, lookup), `game_cards` (lịch sử chia bài + `revealed`); mở rộng `point_bets` (target, multiplier, sequence_no); thêm `balance_before` vào `transactions`; thêm `pot_order` vào `side_pots`; bỏ `winner_id` khỏi `games`; thêm 11 index; bổ sung CHECK constraints |
