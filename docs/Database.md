# Database.md — Hướng Dẫn Thiết Lập CSDL PostgreSQL

**Người phụ trách:** Nhật (module D01–D02)  
**Cập nhật lần cuối:** 2026-09-25

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

### Sau khi cài — khởi động service
```powershell
net start postgresql-x64-16   # tên service có thể khác
```

---

## 3. Tạo Database

```bash
# Đăng nhập với superuser
psql -U postgres

# Trong psql:
\i C:/Users/.../bacay-online/database/bacaudb.sql

# Hoặc chạy trực tiếp:
psql -U postgres -f "C:\Users\...\bacay-online\database\bacaudb.sql"
```

**Lưu ý:** File `bacaudb.sql` đã có lệnh `CREATE DATABASE bacaudb` — chỉ chạy **1 lần đầu**. Chạy lại sẽ lỗi nếu DB đã tồn tại.

Để reset DB:
```sql
DROP DATABASE IF EXISTS bacaudb;
-- Sau đó chạy lại bacaudb.sql
```

---

## 4. Tạo User Riêng (Khuyên Dùng)

Không nên dùng `postgres` superuser trong code. Tạo user riêng:

```sql
-- Trong psql với quyền superuser:
CREATE USER bacay_user WITH PASSWORD 'your_strong_password';
GRANT ALL PRIVILEGES ON DATABASE bacaudb TO bacay_user;
\c bacaudb
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO bacay_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO bacay_user;
```

---

## 5. Cấu Hình Kết Nối Server

Sao chép `server/src/main/resources/server.properties` (đã có mẫu), điền:

```properties
db.url=jdbc:postgresql://localhost:5432/bacaudb
db.username=bacay_user
db.password=your_strong_password
db.poolSize=10
```

> ⚠️ **Không commit `server.properties` có mật khẩu thật.** File này đã có trong `.gitignore`.

---

## 6. Schema — 9 Bảng

```
users           ← tài khoản người dùng
rooms           ← phòng chơi
room_players    ← người chơi trong phòng (lịch sử ra vào)
games           ← ván chơi (round_id UNIQUE — chống ghi lặp)
game_players    ← kết quả mỗi người trong ván
point_bets      ← chi tiết từng hành động đặt cược (RAISE/CALL/FOLD/ALL_IN)
side_pots       ← side pot khi có ALL_IN nhiều mức
side_pot_players← người được tranh từng side pot
transactions    ← giao dịch điểm (cộng/trừ balance)
chat_messages   ← (tùy chọn) lịch sử chat khi ENABLE_CHAT_LOG=true
```

Xem DDL đầy đủ trong [`database/bacaudb.sql`](../database/bacaudb.sql).

---

## 7. ERD Sơ Lược

```
users ──< room_players >── rooms
users ──< game_players >── games ──── rooms
games ──< point_bets ──── users
games ──< side_pots ──< side_pot_players ──── users
users ──< transactions ──── games (nullable)
rooms ──< chat_messages ──── users (nullable sender)
```

**Ràng buộc quan trọng:**
- `games.round_id` — UNIQUE, dùng để chống ghi lặp kết quả
- `game_players(game_id, user_id)` — UNIQUE, mỗi người chỉ có 1 bản ghi/ván
- `room_players(room_id, user_id)` — UNIQUE, không join phòng 2 lần đồng thời
- Balance không được âm — Nhật kiểm tra trong BUS trước khi UPDATE

---

## 8. Quy Tắc Triển Khai BUS/DAL (Nhật)

### DbConnector (D02)
```java
// Dùng connection pool đơn giản hoặc HikariCP nếu muốn
// Tối thiểu: synchronized pool với maxSize = db.poolSize
// Không tạo Connection mới mỗi query
```

### Chống ghi lặp (D04)
```java
// Trong saveResult():
// 1. BEGIN TRANSACTION
// 2. INSERT INTO games (round_id, ...) -- sẽ throw nếu UNIQUE vi phạm
// 3. INSERT game_players, point_bets, side_pots, transactions
// 4. UPDATE users SET balance = balance + profit WHERE id = userId
// 5. COMMIT
// Nếu bất kỳ bước nào lỗi → ROLLBACK toàn bộ
```

### Phân trang (D05)
```sql
-- Lịch sử ván:
SELECT g.id, g.round_id, r.room_name, gp.result, gp.profit, gp.hand_type, g.ended_at
FROM games g
JOIN rooms r ON r.id = g.room_id
JOIN game_players gp ON gp.game_id = g.id AND gp.user_id = ?
ORDER BY g.ended_at DESC
LIMIT ? OFFSET ?;

-- Count:
SELECT COUNT(*) FROM games g
JOIN game_players gp ON gp.game_id = g.id AND gp.user_id = ?;

-- Bảng xếp hạng:
SELECT ROW_NUMBER() OVER (ORDER BY SUM(CASE WHEN t.amount > 0 THEN t.amount ELSE 0 END) DESC,
                                   SUM(CASE WHEN gp.result = 'WIN' THEN 1 ELSE 0 END) DESC,
                                   u.id ASC) AS rank,
       u.id, u.display_name,
       COALESCE(SUM(CASE WHEN t.amount > 0 THEN t.amount ELSE 0 END), 0) AS total_points,
       COALESCE(SUM(CASE WHEN gp.result = 'WIN' THEN 1 ELSE 0 END), 0)  AS total_wins,
       COUNT(DISTINCT gp.game_id) AS total_games
FROM users u
LEFT JOIN game_players gp ON gp.user_id = u.id
LEFT JOIN transactions t ON t.user_id = u.id AND t.type = 'WIN'
WHERE u.status = 'ACTIVE'
GROUP BY u.id, u.display_name
ORDER BY total_points DESC, total_wins DESC, u.id ASC
LIMIT ? OFFSET ?;
```

---

## 9. Kiểm Tra Nhanh

```bash
# Kết nối và kiểm tra bảng
psql -U bacay_user -d bacaudb -c "\dt"

# Xem dữ liệu mẫu
psql -U bacay_user -d bacaudb -c "SELECT id, username, display_name, balance FROM users;"
```

Kết quả mong đợi: 5 bản ghi (admin, player1–4) với `PLACEHOLDER_HASH`.  
Chạy `PasswordUtil.hash("password")` để lấy hash thật.

---

## 10. Lịch Sử Thay Đổi Schema

| Phiên bản | Ngày | Nội dung |
|---|---|---|
| 1.0 | 2026-09-25 | Tạo ban đầu — 9 bảng PostgreSQL, chuyển từ MySQL |
