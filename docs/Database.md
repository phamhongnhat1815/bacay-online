# Database và BUS tài khoản M2 của Nhật

M2 cung cấp `JdbcUserBUS` cho đăng ký, xác thực và đọc hồ sơ bằng MySQL/JDBC.
Khánh ghép BUS vào handler và quản lý phiên online. Thành ghép màn tài khoản.
Các hàm updateProfile/changePassword/updateBalance giữ nguyên interface và trả
NOT_IMPLEMENTED. Chưa triển khai lưu kết quả, lịch sử, leaderboard hay cược.

## Khởi tạo

Dùng MySQL 8.0.16+ và JDK 21. Đăng nhập MySQL bằng tài khoản quản trị rồi tạo
database riêng và tài khoản ứng dụng; thay mật khẩu ví dụ tại máy của mình:

```sql
CREATE DATABASE bacay_online CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'bacay_app'@'localhost' IDENTIFIED BY 'replace_locally';
GRANT SELECT, INSERT, UPDATE ON bacay_online.* TO 'bacay_app'@'localhost';
USE bacay_online;
SOURCE server/src/main/resources/schema.sql;
```

Chạy SOURCE khi mysql được mở từ thư mục gốc repo. Script không xóa dữ liệu;
CREATE TABLE IF NOT EXISTS chỉ dành cho schema mới, không tự nâng cấp bảng cũ.
Không commit mật khẩu thật. Cấu hình dùng biến môi trường BACAY_DB_URL,
BACAY_DB_USER, BACAY_DB_PASSWORD; xem database.env.example. application.yaml
cũ không được TCP server đọc. Java không tự đọc file .env.

Ví dụ PowerShell:

```powershell
$env:BACAY_DB_USER = 'bacay_app'
$env:BACAY_DB_PASSWORD = '<mat-khau-local>'
$env:BACAY_DB_URL = 'jdbc:mysql://localhost:3306/bacay_online?connectTimeout=5000&socketTimeout=10000&connectionTimeZone=UTC'
.\server\mvnw.cmd -f .\pom.xml clean package
.\server\mvnw.cmd -f .\pom.xml install
.\server\mvnw.cmd -f .\server\pom.xml dependency:copy-dependencies
java -cp 'server\target\classes;shared\target\classes;server\target\dependency\*' com.game3cay.server.ServerApplication
```

macOS/Linux dùng `export BACAY_DB_USER=...`, `export BACAY_DB_PASSWORD=...`,
`mvn clean package`, `mvn install`, `mvn -f server/pom.xml dependency:copy-dependencies`;
classpath dùng dấu `:`: `server/target/classes:shared/target/classes:server/target/dependency/*`.
Server hiện chưa gọi BUS; các lệnh trên chuẩn bị driver cho Khánh tích hợp.

## Điểm ghép cho Khánh

```java
UserBUS users = JdbcUserBUS.fromEnvironment();
UserDTO created = users.register(username, password, displayName, email);
UserDTO authenticated = users.authenticate(username, password);
```

Tạo BUS một lần ở composition root và truyền cho các handler. Khi BUSException,
đọc getErrorCode(), gửi message phù hợp qua ERROR; không gửi exception/cause hay
hash. UserDTO là nội bộ server, handler chuyển sang AuthResult của shared.
Đăng ký không tạo phiên. Phiên online, logout và ALREADY_LOGGED_IN do Khánh quản lý.

Username 3–50 ký tự ASCII chữ/số/gạch dưới, bỏ khoảng trắng đầu/cuối và lưu lowercase;
login không phân biệt hoa/thường. Mật khẩu 8–128 ký tự khi đăng ký, không trim.
Display name rỗng dùng username; email tùy chọn, tối đa 100 ký tự, không yêu cầu
duy nhất trong M2 (interface hiện chưa có mã lỗi email trùng). Password hash dùng
PasswordUtil hiện có. Balance khởi tạo 0 chỉ để giữ DTO tương thích.

Mã lỗi: INVALID_REQUEST cho đầu vào đăng ký sai; USERNAME_EXISTS cho tên trùng;
INVALID_CREDENTIALS cho login sai; USER_BLOCKED sau khi mật khẩu đúng của tài khoản
khóa; INTERNAL_ERROR khi DB lỗi. Mỗi truy vấn mở/đóng connection riêng, PreparedStatement;
UNIQUE username bảo vệ tranh chấp đăng ký. Chưa dùng connection pool.

## Kiểm thử và nghiệm thu

`mvn test` chạy test BUS bằng H2 MySQL mode: hash/login, tên trùng, khóa, validation,
hai đăng ký đồng thời và lỗi connection. H2 không thay thế nghiệm thu MySQL thật.
Trên MySQL, gọi BUS với username mới `nhat_demo`, mật khẩu `DemoPass123`, display
name `Nhật`; login đúng/sai rồi đăng ký lại tên hoa `NHAT_DEMO`. Khóa user bằng
`UPDATE users SET status='BLOCKED' WHERE username='nhat_demo';` và thử login.
Thử DB không truy cập được bằng URL cổng khác, kiểm tra INTERNAL_ERROR.
Không dùng tài khoản thật cho các thao tác kiểm thử. Để phục hồi trạng thái,
đổi status về ACTIVE; dùng username mới nếu muốn lặp bài đăng ký.

Đối chiếu file thiết kế Word: BIGSERIAL được đổi sang BIGINT AUTO_INCREMENT;
thêm thống kê theo UserDTO. Các bảng rooms/games/point_bets/transactions và JPA
chưa đưa vào M2 vì luật điểm và phạm vi lưu trữ cần chốt ở M4–M5.
