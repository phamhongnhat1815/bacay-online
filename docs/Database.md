# Database và BUS tài khoản M2

`JdbcUserBUS` cung cấp đăng ký, xác thực và đọc hồ sơ bằng MySQL/JDBC.

Trong bản ghép M1–M3, `ServerApplication` đã khởi tạo BUS và truyền cho các `ClientHandler`. Client Swing gửi yêu cầu tài khoản qua TCP; handler gọi BUS, truy cập database và trả kết quả bằng các DTO của module shared.

`SessionRegistry` quản lý phiên online. `RoomManager` quản lý phòng và chat trong RAM.

Các hàm `updateProfile`, `changePassword` và `updateBalance` giữ nguyên interface và trả `NOT_IMPLEMENTED`. Chưa triển khai lưu kết quả, lịch sử, leaderboard hoặc nghiệp vụ cược.

## 1. Yêu cầu môi trường

- JDK 21.
- Maven hoặc Maven Wrapper trong thư mục `server`.
- MySQL 8.0.16 trở lên.
- JDBC driver `mysql-connector-j`, đã khai báo trong `server/pom.xml`.

Luồng hiện tại sử dụng JDBC trực tiếp. `application.yaml` không được TCP server đọc.

## 2. Khởi tạo database

Mở MySQL bằng tài khoản quản trị, từ thư mục gốc dự án. Tạo database và tài khoản ứng dụng; thay mật khẩu mẫu bằng mật khẩu trên máy của mình:

```sql
CREATE DATABASE IF NOT EXISTS bacay_online
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE USER 'bacay_app'@'localhost'
    IDENTIFIED BY 'replace_locally';

GRANT SELECT, INSERT, UPDATE
    ON bacay_online.*
    TO 'bacay_app'@'localhost';

USE bacay_online;

SOURCE server/src/main/resources/schema.sql;
```

Lệnh `SOURCE` được chạy trong MySQL client, không phải PowerShell. Đường dẫn trên tính từ thư mục gốc dự án.

Nếu tài khoản `bacay_app` đã tồn tại, sử dụng tài khoản đó hoặc điều chỉnh tài khoản/mật khẩu phù hợp trước khi chạy ứng dụng.

Script dùng `CREATE TABLE IF NOT EXISTS` và không xóa dữ liệu hiện có. Tuy nhiên, lệnh này không cập nhật cấu trúc bảng cũ. Database đã tạo bằng schema trước đó cần được đối chiếu và migration riêng.

Không commit mật khẩu thật hoặc dữ liệu cấu hình cá nhân vào repository.

## 3. Cấu hình kết nối

Chương trình lấy cấu hình từ biến môi trường:

| Biến | Ý nghĩa |
|---|---|
| `BACAY_DB_URL` | URL JDBC kết nối MySQL |
| `BACAY_DB_USER` | Tài khoản database |
| `BACAY_DB_PASSWORD` | Mật khẩu database |

Nếu không đặt `BACAY_DB_URL`, chương trình dùng:

```text
jdbc:mysql://localhost:3306/bacay_online?connectTimeout=5000&socketTimeout=10000&connectionTimeZone=UTC
```

Cần đặt `BACAY_DB_USER` và `BACAY_DB_PASSWORD` trước khi chạy server.

`server/src/main/resources/database.env.example` là cấu hình mẫu. Java không tự đọc file `.env`; cần đặt các biến trong môi trường của tiến trình chạy server.

Ví dụ PowerShell:

```powershell
$env:BACAY_DB_USER = 'bacay_app'
$env:BACAY_DB_PASSWORD = '<mat-khau-local>'
$env:BACAY_DB_URL = 'jdbc:mysql://localhost:3306/bacay_online?connectTimeout=5000&socketTimeout=10000&connectionTimeZone=UTC'
```

Các biến trên chỉ áp dụng cho terminal hiện tại và các tiến trình khởi chạy từ terminal đó. Nếu thay đổi cấu hình, cần khởi động lại server.

Khi thiếu cấu hình tài khoản database, server vẫn có thể chạy phần kết nối M1; chức năng tài khoản M2 chưa sử dụng được.

## 4. Build và chạy

Chạy các lệnh sau từ thư mục gốc dự án bằng PowerShell và JDK 21:

```powershell
.\server\mvnw.cmd -f .\pom.xml clean install

.\server\mvnw.cmd -f .\server\pom.xml dependency:copy-dependencies

java -cp 'server\target\classes;shared\target\classes;server\target\dependency\*' com.game3cay.server.ServerApplication
```

`clean install` build các module, chạy các test đã khai báo và cài artifact vào Maven repository cục bộ.

`dependency:copy-dependencies` chuẩn bị các dependency của server, bao gồm JDBC driver.

Server mặc định nghe cổng `8888`.

Mở terminal khác để chạy client:

```powershell
java -cp 'client\target\classes;shared\target\classes' com.game3cay.RunClient
```

Trên macOS/Linux, đặt biến môi trường bằng `export`. Nếu có Maven trong PATH:

```bash
export BACAY_DB_USER='bacay_app'
export BACAY_DB_PASSWORD='<mat-khau-local>'
export BACAY_DB_URL='jdbc:mysql://localhost:3306/bacay_online?connectTimeout=5000&socketTimeout=10000&connectionTimeZone=UTC'

mvn -f pom.xml clean install

mvn -f server/pom.xml dependency:copy-dependencies

java -cp 'server/target/classes:shared/target/classes:server/target/dependency/*' com.game3cay.server.ServerApplication
```

Classpath trên macOS/Linux dùng dấu `:`; trên Windows dùng dấu `;`.

## 5. Luồng tài khoản đã tích hợp

`ServerApplication` tạo một BUS dùng chung thông qua:

```java
UserBUS users = JdbcUserBUS.fromEnvironment();
```

BUS được truyền vào các `ClientHandler`.

### Đăng ký

1. Client gửi `REGISTER` với `RegisterRequest`.
2. Handler gọi `UserBUS.register()`.
3. BUS kiểm tra dữ liệu, hash mật khẩu và gọi DAO thêm tài khoản.
4. Handler chuyển `UserDTO` thành `AuthResult`, trả `AUTH_RESULT` giữ nguyên requestId.

Đăng ký thành công không tạo phiên đăng nhập. Người dùng cần thực hiện `LOGIN`.

### Đăng nhập

1. Client gửi `LOGIN` với `AuthRequest`.
2. Handler gọi `UserBUS.authenticate()`.
3. BUS đọc tài khoản, xác minh mật khẩu và kiểm tra trạng thái.
4. Handler đăng ký phiên trong `SessionRegistry`.
5. Khi đăng ký phiên thành công, handler gắn danh tính vào kết nối rồi trả `AUTH_RESULT`.

Nếu tài khoản đã có phiên hoạt động, server trả `ALREADY_LOGGED_IN` và giữ phiên cũ.

Danh tính cho các thao tác sau đăng nhập được lấy từ phiên server.

### Đăng xuất và mất kết nối

`LOGOUT` dọn trạng thái phòng, giải phóng phiên và trả `ACK`. Socket được giữ mở để người dùng có thể đăng nhập lại.

Khi mất kết nối hoặc heartbeat timeout, server dọn phòng và giải phóng phiên. Nếu người rời là chủ phòng, `RoomManager` chuyển quyền cho người còn lại vào phòng sớm nhất; phòng trống bị xóa.

### Xử lý lỗi

Khi gặp `BUSException`, handler lấy `getErrorCode()` và trả `ERROR` cùng thông báo nghiệp vụ.

Không gửi exception, SQL cause hoặc password hash cho client.

`UserDTO` chỉ dùng nội bộ server. Dữ liệu tài khoản qua socket sử dụng `AuthResult` của module shared.

## 6. Quy tắc dữ liệu tài khoản

| Trường | Quy tắc |
|---|---|
| Username | 3–50 ký tự ASCII gồm chữ, số hoặc gạch dưới; bỏ khoảng trắng đầu/cuối và lưu lowercase |
| Mật khẩu đăng ký | 8–128 ký tự, không được chỉ chứa khoảng trắng; không trim |
| Tên hiển thị | Tối đa 100 ký tự; rỗng thì dùng username |
| Email | Tùy chọn, tối đa 100 ký tự; nếu có phải đúng định dạng được BUS kiểm tra |
| Trạng thái | `ACTIVE` hoặc `BLOCKED` |
| Balance | Khởi tạo bằng 0 để giữ tương thích với DTO |

Đăng nhập không phân biệt hoa/thường đối với username.

Theo `schema.sql` hiện tại, username và email đều có ràng buộc UNIQUE. Email không được cung cấp được lưu bằng `NULL`.

Khi trùng username, BUS trả `USERNAME_EXISTS`. Khi trùng email, BUS trả `INVALID_REQUEST` với thông báo “Email đã được sử dụng”.

Mật khẩu được hash bằng `PasswordUtil`, sử dụng PBKDF2WithHmacSHA256 và salt ngẫu nhiên. Không lưu mật khẩu thô hoặc đưa hash vào `UserDTO`.

DAO sử dụng `PreparedStatement`. Mỗi truy vấn mở và đóng connection riêng; bản hiện tại chưa dùng connection pool.

Ràng buộc UNIQUE trong database bảo vệ trường hợp hai yêu cầu đăng ký cùng username đến đồng thời.

## 7. Mã lỗi

| Mã lỗi | Trường hợp |
|---|---|
| `INVALID_REQUEST` | Dữ liệu đăng ký không hợp lệ hoặc email đã được sử dụng |
| `USERNAME_EXISTS` | Username đã tồn tại |
| `INVALID_CREDENTIALS` | Username hoặc mật khẩu đăng nhập không đúng |
| `USER_BLOCKED` | Mật khẩu đúng nhưng tài khoản bị khóa |
| `ALREADY_LOGGED_IN` | Tài khoản đang có phiên khác hoặc kết nối hiện tại đã đăng nhập |
| `UNAUTHENTICATED` | Thao tác cần đăng nhập nhưng kết nối chưa có danh tính |
| `INTERNAL_ERROR` | Lỗi truy cập database hoặc lỗi nội bộ được xử lý |
| `NOT_IMPLEMENTED` | Gọi chức năng chưa triển khai trong bản M2 |

`ALREADY_LOGGED_IN` và `UNAUTHENTICATED` được xử lý ở tầng phiên/handler, không do database thay thế.

Các hàm chưa triển khai:

```java
updateProfile(...)
changePassword(...)
updateBalance(...)
```

Các hàm này trả `NOT_IMPLEMENTED`, không trả thành công giả.

## 8. Kiểm thử và nghiệm thu

### Kiểm thử tự động

Chạy từ thư mục gốc bằng JDK 21:

```powershell
.\server\mvnw.cmd -f .\pom.xml clean verify
```

`JdbcUserBUSTest` dùng H2 ở chế độ MySQL để kiểm tra:

- Đăng ký, hash mật khẩu và đăng nhập đúng/sai.
- Username trùng.
- Tài khoản bị khóa.
- Validation và các hàm chưa triển khai.
- Hai yêu cầu đăng ký cùng username đồng thời.
- Lỗi mở connection.

H2 không thay thế nghiệm thu với MySQL thật hoặc kiểm tra luồng Swing–TCP–BUS–database.

### Nghiệm thu MySQL thật

Sử dụng tài khoản thử, ví dụ username `nhat_demo`, mật khẩu `DemoPass123`, tên hiển thị `Nhật`.

| Thao tác | Kết quả mong đợi |
|---|---|
| Đăng ký username mới | Tạo tài khoản và trả `AUTH_RESULT`; chưa đăng nhập |
| Đăng nhập đúng mật khẩu | Thành công, có phiên online |
| Đăng nhập sai mật khẩu | `INVALID_CREDENTIALS` |
| Đăng ký lại bằng `NHAT_DEMO` | `USERNAME_EXISTS` |
| Đăng ký username khác với email đã sử dụng | `INVALID_REQUEST` |
| Client thứ hai đăng nhập cùng tài khoản đang online | `ALREADY_LOGGED_IN` |
| Đăng xuất rồi đăng nhập lại | Thành công trên socket đang mở |
| Ngắt kết nối rồi kết nối và đăng nhập lại | Phiên cũ được giải phóng |
| Database không truy cập được | `INTERNAL_ERROR` |

Để thử tài khoản bị khóa, thực hiện bằng tài khoản database có quyền cập nhật:

```sql
UPDATE users
SET status = 'BLOCKED'
WHERE username = 'nhat_demo';
```

Đăng nhập bằng mật khẩu đúng phải nhận `USER_BLOCKED`.

Sau khi kiểm tra, khôi phục trạng thái:

```sql
UPDATE users
SET status = 'ACTIVE'
WHERE username = 'nhat_demo';
```

Có thể thử lỗi database bằng URL trỏ đến cổng không có MySQL, sau đó khởi động lại server và gửi yêu cầu tài khoản. Kết quả cần là `INTERNAL_ERROR`, không lộ SQL cause cho client.

Sử dụng tài khoản thử riêng. Khi lặp bài đăng ký, dùng username mới để tránh nhầm lỗi trùng tài khoản với lỗi kết nối.

## 9. Phạm vi database hiện tại

Đối chiếu thiết kế database, `BIGSERIAL` được chuyển thành `BIGINT AUTO_INCREMENT`. Bảng `users` có các trường thống kê tương ứng với `UserDTO`.

`schema.sql` hiện có các bảng:

- `users`
- `rooms`
- `room_players`
- `games`
- `game_players`
- `cards`
- `game_cards`
- `point_bets`
- `transactions`

Luồng runtime M1–M3 mới truy cập bảng `users` cho nghiệp vụ tài khoản. Phòng và chat được giữ trong RAM.

Việc có bảng trong schema chưa đồng nghĩa đã triển khai nghiệp vụ lưu trữ. Lưu kết quả, lịch sử, giao dịch và cập nhật thống kê cần được tích hợp ở M4–M5.

Luồng hiện tại sử dụng JDBC, chưa sử dụng JPA.