# BaCay Online — Game Bài 3 Cây Socket Java

Ứng dụng desktop nhiều người dùng | Java Swing + TCP Socket + MySQL | BTL Lập Trình Mạng

---

## Thành viên nhóm

| Thành viên | Vai trò | Module |
|---|---|---|
| Thành | UI / Client | T01–T07 |
| Trọng | Game Engine | G01–G06 |
| **Khánh** | Server / Mạng | N01–N08 + shared |
| Nhật | DB / BUS | D01–D06 |

---

## Yêu cầu môi trường

| Công cụ | Phiên bản | Ghi chú |
|---|---|---|
| JDK | **17** LTS | Đặt `JAVA_HOME` về JDK 17 |
| Maven | 3.9+ | Dùng `mvnw.cmd` nếu chưa cài |
| MySQL | 8.x | Cần cài và chạy trước khi khởi server |

> **Lưu ý:** Không commit file chứa mật khẩu DB. Tham khảo `server/src/main/resources/server.properties` để biết cách cấu hình.

---

## Cấu trúc thư mục

```
bacay-online/
├── pom.xml                   # Parent Maven (3 module)
├── shared/                   # Giao thức + model dùng chung
│   └── src/main/java/com/bacay/shared/
│       ├── network/          # Packet, PacketType, ErrorCode
│       ├── model/            # Card, Hand, RoomSnapshot, RoundResult...
│       └── security/         # PasswordUtil (PBKDF2)
├── server/                   # RunServer + logic phía server
│   └── src/main/java/com/bacay/server/
│       ├── RunServer.java
│       ├── network/          # ClientHandler, SessionManager (Khánh)
│       ├── room/             # RoomManager, ChatManager (Khánh)
│       ├── game/             # GameEngine, BettingEngine (Trọng)
│       ├── bus/              # UserBUS, GameBUS... (Nhật)
│       └── dal/              # UserDAL, GameDAL... (Nhật)
├── client/                   # RunClient + Swing UI
│   └── src/main/java/com/bacay/client/
│       ├── RunClient.java
│       ├── controller/       # SocketHandler (Thành)
│       └── view/             # LoginFrame, LobbyFrame... (Thành)
├── database/
│   └── bacaudb.sql           # Script tạo schema MySQL
└── docs/
    └── Protocol.md           # Giao thức thông điệp (Khánh viết)
```

---

## Hướng dẫn build và chạy

### 1. Tạo Database (Nhật phụ trách)

```bash
mysql -u root -p < database/bacaudb.sql
```

### 2. Cấu hình server

Tạo file `server/src/main/resources/server.properties` từ mẫu (xem file mẫu đã có).
Điền các dòng DB:
```properties
db.url=jdbc:mysql://localhost:3306/bacaudb?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=UTF-8
db.username=YOUR_USER
db.password=YOUR_PASSWORD
```

> ⚠️ Không commit file này lên Git.

### 3. Build toàn bộ project

**Windows (có Maven trong PATH):**
```bat
mvn clean install -DskipTests
```

**Windows (dùng wrapper — không cần cài Maven):**
```bat
mvnw.cmd clean install -DskipTests
```

**Chỉ compile (không đóng gói):**
```bat
mvnw.cmd clean compile
```

### 4. Chạy server

```bat
set JAVA_HOME=C:\Program Files\Java\jdk-17.0.18
java -jar server\target\bacay-server.jar

# Override cổng:
java -Dserver.port=9999 -jar server\target\bacay-server.jar
```

### 5. Chạy client (nhiều instance)

```bat
java -jar client\target\bacay-client.jar

# Override IP server (khi chạy LAN):
java -Dserver.host=192.168.1.10 -Dserver.port=8888 -jar client\target\bacay-client.jar
```

---

## Cấu hình mặc định

| Tham số | Giá trị | File |
|---|---|---|
| Server port | `8888` | `server.properties` |
| Max connections | `100` | `server.properties` |
| Heartbeat interval | `5s` | `server.properties` |
| Heartbeat timeout | `15s` | `server.properties` |
| Chat max length | `500 ký tự` | `server.properties` |
| Chat rate limit | `5 tin / 10s` | `server.properties` |
| Người/phòng | `2–8` | hardcode server |
| Phân trang mặc định | `20` bản ghi | `server.properties` |

---

## Kịch bản demo chính

1. Đăng ký → đăng nhập → xem lobby
2. Tạo phòng (2–8 người), set ready, bắt đầu ván
3. Vòng đặt cược: RAISE / CALL / FOLD / ALL_IN
4. Kết quả: lật bài, chia pot (+ side pot nếu có ALL_IN)
5. Chat trong phòng; kiểm tra chat không sang phòng khác
6. Thoát client đột ngột → server phát hiện → thông báo phòng còn lại
7. Lịch sử ván + bảng xếp hạng

---

## Git workflow

- Nhánh tích hợp: `main`
- Nhánh feature: `feature/<module>-<mô-tả>` (ví dụ `feature/N01-tcp-server`)
- Chỉ merge sau khi build thành công và ít nhất 1 người liên quan review
- Không force push lên `main`
- Thay đổi `shared/` phải báo cho cả nhóm trước

---

## Tiến độ

Xem file `Tien_do_va_diem_ghep_BTL_LTM - Tien do va diem ghep.csv` để cập nhật trạng thái từng mốc.
