# Ba Cây Online — Bài tập lớn Lập trình mạng

Dự án game Ba Cây nhiều người chơi, sử dụng kiến trúc client–server qua TCP Socket.

Bản hiện tại triển khai nền M1–M3: kết nối, tài khoản, lobby, phòng chờ và chat. Khởi tạo và chơi ván thật thuộc bước tích hợp M4.

## Công nghệ

- Java 21, Maven và Maven Wrapper.
- Client: Java Swing.
- Server: Java TCP Socket.
- Giao thức: `Packet` qua `ObjectInputStream` và `ObjectOutputStream`, đọc/ghi thông qua `PacketIO`.
- Tài khoản: MySQL/JDBC; mật khẩu được hash bằng PBKDF2WithHmacSHA256.
- Kiểm thử BUS: JUnit 5 và H2 ở chế độ MySQL.

## Cấu trúc dự án

Dự án gồm ba module Maven:

| Module | Vai trò | Thành phần chính |
|---|---|---|
| `shared` | Hợp đồng dữ liệu dùng chung | `Packet`, `PacketType`, `ErrorCode`, `PacketIO` và các DTO |
| `server` | Kết nối, tài khoản, phiên và phòng | `ServerApplication`, `ClientHandler`, `SessionRegistry`, `JdbcUserBUS`, `RoomManager` |
| `client` | Giao diện và kết nối phía người chơi | `RunClient`, `ConnectionController`, `MainFrame`, `LobbyPanel`, `RoomPanel`, `SocketClient` |

Điểm khởi chạy:

- Server: `com.game3cay.server.ServerApplication`.
- Client: `com.game3cay.RunClient`.
- `shared` là thư viện dùng chung, không có chương trình khởi chạy riêng.

## Chức năng hiện tại

| Mốc | Phần đã triển khai |
|---|---|
| M1 | Kết nối TCP, SERVER_NOTICE, ECHO/ACK, heartbeat PING/PONG và xử lý mất kết nối |
| M2 | Đăng ký, đăng nhập, đăng xuất, gọi BUS/JDBC và quản lý phiên online |
| M3.1 | Danh sách phòng, tạo/vào/rời phòng, sẵn sàng, kiểm tra quyền chủ phòng và chuyển chủ phòng |
| M3.2 | Chat đúng phòng, thông báo vào/rời, giới hạn 500 ký tự và 5 tin trong 10 giây |

Phòng hiện được quản lý trong RAM. Phòng trống bị xóa.

`START_GAME_REQUEST` kiểm tra chủ phòng, số người và trạng thái sẵn sàng. Khi chưa ghép engine M4, server trả `NOT_IMPLEMENTED`.

Các chức năng cập nhật hồ sơ, đổi mật khẩu, cập nhật số dư, lưu kết quả, lịch sử và bảng xếp hạng chưa được triển khai trong bản M1–M3.

## Cấu hình và chạy

Dùng JDK 21. Khởi tạo MySQL và đặt các biến môi trường theo `docs/Database.md`:

- `BACAY_DB_URL`
- `BACAY_DB_USER`
- `BACAY_DB_PASSWORD`

Đặt biến trong terminal khởi chạy server. `database.env.example` là cấu hình mẫu; chương trình không tự đọc file `.env`. TCP server hiện không sử dụng `application.yaml`.

Chạy từ thư mục gốc dự án bằng PowerShell:

```powershell
.\server\mvnw.cmd -f .\pom.xml clean install
.\server\mvnw.cmd -f .\server\pom.xml dependency:copy-dependencies

java -cp 'server\target\classes;shared\target\classes;server\target\dependency\*' com.game3cay.server.ServerApplication
```

Server mặc định nghe cổng `8888`.

Mở terminal khác để chạy client:

```powershell
java -cp 'client\target\classes;shared\target\classes' com.game3cay.RunClient
```

Có thể mở nhiều client để kiểm tra phòng và chat.

## Kiểm thử trước khi tích hợp

```powershell
.\server\mvnw.cmd -f .\pom.xml clean verify
```

Kiểm thử BUS bằng H2 không thay thế nghiệm thu với MySQL thật.

Nghiệm thu M1–M3 cần thử nhiều client: đăng ký/đăng nhập, chống đăng nhập trùng, phòng và sẵn sàng, chat hai phòng độc lập, chuyển chủ phòng và dọn trạng thái khi mất kết nối.

Client, server và shared phải được build từ cùng bản mã nguồn. Quy ước giao tiếp hiện tại nằm trong mục “Quy ước bản ghép M1–M3” của `docs/Protocol.md`.