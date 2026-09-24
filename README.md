# BaCay Online — Game Bài 3 Cây Đối Kháng

Ứng dụng desktop nhiều người chơi đồng thời | Java 21 · Swing · TCP Socket · PostgreSQL | BTL Lập Trình Mạng

---

## Thành viên nhóm

| Thành viên | Vai trò | Module | Branch |
|---|---|---|---|
| Thành | UI / Client (Swing) | T01–T07 | `feature/T-...` |
| Trọng | Game Engine | G01–G06 | `feature/G-...` |
| **Khánh** | Server / Mạng / Tích hợp | N01–N08 + `shared` | `feature/N-...` |
| Nhật | DB / BUS (PostgreSQL) | D01–D06 | `feature/D-...` |

---

## Yêu cầu môi trường

| Công cụ | Phiên bản | Ghi chú |
|---|---|---|
| JDK | **21** (LTS) | Đặt `JAVA_HOME` → JDK 21 |
| Maven | 3.9+ | Dùng `mvnw.cmd` nếu chưa cài toàn cục |
| PostgreSQL | 15+ | Phải chạy trước khi khởi server |

> ⚠️ **Không commit** `server.properties` có mật khẩu thật — file này đã được `.gitignore`.

---

## Cấu trúc dự án

```
bacay-online/
├── pom.xml                        # Parent Maven — Java 21, PostgreSQL JDBC 42.7.3
├── shared/                        # Module dùng chung (client + server)
│   └── src/main/java/com/bacay/shared/
│       ├── network/               # Packet, PacketType (9 nhóm), ErrorCode (23 mã)
│       ├── model/                 # Card, Hand, Snapshot, RoundResult, PageResult...
│       └── security/              # PasswordUtil (PBKDF2WithHmacSHA256)
├── server/                        # Module server
│   └── src/main/java/com/bacay/server/
│       ├── RunServer.java
│       ├── network/               # ClientHandler, SessionManager (Khánh — N01-N03)
│       ├── room/                  # RoomManager, ChatManager   (Khánh — N04, N07)
│       ├── game/                  # GameEngine (interface), ActionResult (Trọng — G01-G06)
│       ├── bus/                   # UserBUS, GameBUS (interface + impl) (Nhật — D03-D06)
│       └── dal/                   # UserDAL, GameDAL...            (Nhật — D02)
├── client/                        # Module client
│   └── src/main/java/com/bacay/client/
│       ├── RunClient.java
│       ├── controller/            # SocketHandler                  (Thành — T01)
│       └── view/                  # LoginFrame, LobbyFrame, InGameFrame... (Thành — T02-T06)
├── database/
│   └── bacaudb.sql                # Schema PostgreSQL — 9 bảng (Nhật — D01)
└── docs/
    ├── Protocol.md                # Giao thức đầy đủ v1.0         (Khánh)
    ├── Database.md                # Hướng dẫn thiết lập PostgreSQL (Nhật)
    ├── Game_Rules.md              # Luật game đầy đủ               (Trọng)
    └── Screen_Data_Mapping.md     # Ánh xạ màn hình → dữ liệu      (Thành)
```

---

## Hướng dẫn build & chạy

### 1. Tạo Database

```bash
# Lần đầu — tạo DB và toàn bộ schema (9 bảng):
psql -U postgres -f database/bacaudb.sql

# Xem hướng dẫn chi tiết (tạo user riêng, CHECK constraint...):
# docs/Database.md
```

### 2. Cấu hình server

Mở `server/src/main/resources/server.properties` và điền:

```properties
db.url=jdbc:postgresql://localhost:5432/bacaudb
db.username=bacay_user
db.password=YOUR_PASSWORD
```

> File mẫu đã có sẵn với giá trị mặc định cho mọi tham số khác.

### 3. Build toàn bộ

```bat
# Windows — có Maven trong PATH:
set JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot
mvn clean install -DskipTests

# Windows — dùng wrapper (không cần cài Maven):
set JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot
mvnw.cmd clean install -DskipTests
```

### 4. Chạy server

```bat
set JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot
java -jar server\target\bacay-server.jar

# Override cổng:
java -Dserver.port=9999 -jar server\target\bacay-server.jar
```

### 5. Chạy client (mở nhiều cửa sổ để test)

```bat
java -jar client\target\bacay-client.jar

# Chạy LAN — trỏ đến IP server:
java -Dserver.host=192.168.1.10 -Dserver.port=8888 -jar client\target\bacay-client.jar
```

---

## Cấu hình mặc định

| Tham số | Giá trị mặc định | File |
|---|---|---|
| Server port | `8888` | `server.properties` |
| Max connections | `100` | `server.properties` |
| Heartbeat interval | `5s` | `server.properties` |
| Heartbeat timeout | `15s` | `server.properties` |
| Chat max length | `500 ký tự` | `server.properties` |
| Chat rate limit | `5 tin / 10s` | `server.properties` |
| Người/phòng | `2 – 8` | `server.properties` |
| Phân trang mặc định | `20` bản ghi (tối đa 100) | `server.properties` |
| Chat log lâu dài | `false` | `server.properties` |

---

## Tính năng

- **Đăng ký / đăng nhập** — mật khẩu hash PBKDF2, không lưu plain text
- **Lobby** — danh sách phòng realtime, tạo/vào/rời phòng
- **Ván chơi 3 Cây** — chia bài riêng, tố RAISE/CALL/FOLD/ALL_IN, side pot, so bài
- **Chat theo phòng** — văn bản + emoji, lọc từ ngữ server-side, thông báo hệ thống
- **Hồ sơ** — xem/sửa thông tin, đổi mật khẩu
- **Lịch sử ván** — phân trang, theo tài khoản đã xác thực
- **Bảng xếp hạng** — sắp theo tổng điểm → số thắng → ID

---

## Kịch bản demo

1. Đăng ký → đăng nhập → xem lobby
2. Tạo phòng (min 2 người), tất cả set ready → chủ phòng bắt đầu
3. Vòng đặt cược: RAISE / CALL / FOLD / ALL_IN (kể cả side pot)
4. Showdown: lật bài, chia pot, cập nhật số dư
5. Chat trong phòng — kiểm tra không sang phòng khác
6. Thoát client đột ngột → server phát hiện sau ≤ 15s → thông báo phòng còn lại
7. Xem lịch sử ván + bảng xếp hạng

---

## Git workflow

```
main            ← nhánh tích hợp, luôn build được
feature/N01-... ← nhánh feature theo module (ví dụ feature/G02-hand-evaluator)
```

- Chỉ merge vào `main` sau khi `mvn clean compile` thành công và ≥ 1 người liên quan review
- **Thay đổi bất kỳ class nào trong `shared/`** → mở issue/thông báo nhóm trước khi merge
- Không force push lên `main`

---

## Mốc tiến độ (M0 → M6)

| Mốc | Nội dung | Trạng thái |
|---|---|---|
| **M0** | Khung project, giao thức, hợp đồng BUS/Engine, luật game | ✅ **Hoàn thành** |
| **M1** | TCP kết nối, PING/PONG, đăng nhập end-to-end | 🔄 Đang thực hiện |
| **M2** | Đăng ký/đăng nhập Swing → PostgreSQL | ⏳ Chờ |
| **M3** | Lobby + phòng chờ + chat | ⏳ Chờ |
| **M4** | Ván chơi đầy đủ (chia bài, tố, kết quả) | ⏳ Chờ |
| **M5** | Lưu kết quả, lịch sử, bảng xếp hạng, hồ sơ | ⏳ Chờ |
| **M6** | Đóng gói, kiểm thử LAN, hoàn thiện | ⏳ Chờ |

Xem chi tiết: [`Tien_do_va_diem_ghep_BTL_LTM - Tien do va diem ghep.csv`](Tien_do_va_diem_ghep_BTL_LTM%20-%20Tien%20do%20va%20diem%20ghep.csv)

---

## Tài liệu kỹ thuật

| Tài liệu | Nội dung | Người phụ trách |
|---|---|---|
| [`docs/Protocol.md`](docs/Protocol.md) | Giao thức gói tin đầy đủ v1.0 | Khánh |
| [`docs/Database.md`](docs/Database.md) | Thiết lập PostgreSQL, ERD, SQL mẫu | Nhật |
| [`docs/Screen_Data_Mapping.md`](docs/Screen_Data_Mapping.md) | Ánh xạ màn hình → packet | Thành |
| [`Game_Rules.md`](Game_Rules.md) | Luật bài, tố, side pot, chat | Trọng |
| [`PROMPT_Xay_dung_BaCay_Online_BTL_LTM.md`](PROMPT_Xay_dung_BaCay_Online_BTL_LTM.md) | Đặc tả kỹ thuật tổng thể | Cả nhóm |
| [`Ban_giao_viec_BTL_Lap_trinh_mang.md`](Ban_giao_viec_BTL_Lap_trinh_mang.md) | Phân công module, milestone, quy tắc | Cả nhóm |
