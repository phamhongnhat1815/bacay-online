# Bàn giao Nhật đến M3

Code nhóm tại abacb8a đã ghép JdbcUserBUS vào ServerApplication/ClientHandler,
SessionRegistry quản lý phiên, RoomManager quản lý phòng/chat trong RAM.
Phần Nhật ở M3 là bảo đảm dữ liệu tài khoản và hỗ trợ kiểm thử ghép.

## Phần bổ sung

- Test email UNIQUE trả INVALID_REQUEST, username trùng trả USERNAME_EXISTS.
- Test bốn người đăng ký/đăng nhập qua JdbcUserBUS/DAO, chia hai phòng, ready,
  chat tiếng Việt với sender lấy từ UserDTO và chỉ gửi tới người đúng phòng.
- Test người rời/ngắt kết nối được chuyển chủ phòng, phòng trống bị xóa,
  tài khoản vẫn đăng nhập lại được sau khi phòng được dọn.
- DatabaseCheck kiểm tra MySQL thật, cột users và seed 52 lá chỉ bằng SELECT.

## Chạy

`mvn clean package` chạy test tự động bằng H2 MySQL mode.
Test ghép gọi RoomManager trực tiếp, không xác nhận Swing/socket hay phiên online.

Sau khi tạo schema và đặt BACAY_DB_URL/BACAY_DB_USER/BACAY_DB_PASSWORD theo
Database.md, chạy `mvn install` và `mvn -f server/pom.xml dependency:copy-dependencies`.
macOS/Linux:

```bash
java -cp 'server/target/classes:shared/target/classes:server/target/dependency/*' com.game3cay.server.dal.DatabaseCheck
```

Windows dùng classpath `server\target\classes;shared\target\classes;server\target\dependency\*`.
DatabaseCheck không tạo bảng, tài khoản hoặc sửa dữ liệu. Exit 0 là đạt, exit 1
là chưa đạt. Không đưa mật khẩu thật lên Git.

## Nghiệm thu nhóm còn cần

Chạy với MySQL thật và bốn client Swing chia hai phòng. Kiểm tra phiên trùng,
chưa đăng nhập bị chặn, tranh chỗ cuối, logout/ngắt kết nối rồi đăng nhập lại,
chat không lẫn phòng, heartbeat và DB lỗi. H2 không thay thế bằng chứng MySQL/LAN.
Không cần lưu room/chat vào DB ở M3; schema các bảng đó là chuẩn bị cho mốc sau.
updateProfile/changePassword/updateBalance vẫn NOT_IMPLEMENTED theo phạm vi bàn giao;
Engine/lưu kết quả/lịch sử thuộc M4–M5.
