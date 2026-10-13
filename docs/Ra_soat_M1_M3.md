# Kết quả rà soát M1 M3 ngày 13 10 2026

Đối chiếu README, bản bàn giao 30/9, Protocol.md, Database.md, CLIENT_M0.md,
Nhat_M3.md với code tại abacb8a và thay đổi local. Rà soát tập trung đến M3.

## Bằng chứng kiểm thử

Build sạch `mvn clean verify` đạt. JdbcUserBUSTest có 7 test đạt: tài khoản,
hash, tên trùng, khóa, validation, đăng ký đồng thời, email trùng, lỗi connection,
ghép JDBC với hai phòng và test TCP thực tế bằng hai kết nối.
Test TCP xác nhận UNAUTHENTICATED, đăng ký không tạo phiên, chống login trùng,
tạo phòng, chat không tin tên/ID client và logout giải phóng phiên cho client khác.
DB kiểm thử là H2 MySQL mode. Chưa xác nhận MySQL thật, UI Swing, LAN, idle 30 giây,
timeout heartbeat, tranh chỗ cuối qua mạng hoặc phòng 8 người.
Một lượt incremental test gặp class trung gian chứa lỗi unresolved compilation;
build sạch sau đó đạt, chưa đủ bằng chứng kết luận nguyên nhân từ IDE.
JDK runtime hiện là 24.0.2, compiler target release 21; cần test runtime JDK 21.

## Công việc theo phân công

- Nhật: BUS/DAO/schema đã có; còn nghiệm thu MySQL thật trên DB mới và DB cũ,
  chứng minh hash/salt, email/username UNIQUE, lỗi DB và lưu tài khoản qua restart.
  CREATE TABLE IF NOT EXISTS không migration bảng users cũ.
- Thành và Khánh: còn bằng chứng bốn client Swing chia hai phòng, chat tiếng Việt,
  ready, snapshot cũ, logout/mất kết nối, tranh chỗ cuối, giới hạn 8 người,
  heartbeat timeout và client/server lỗi không ảnh hưởng client còn lại.
- Trọng: repo mới có RoundContext, chưa thấy Engine hay tài liệu G01/API kết quả.
  Engine hoàn chỉnh thuộc M4, nhưng luật bắt đầu/hòa/rời/hủy và ý nghĩa điểm cần
  chốt trước tích hợp. Không tính đây là lỗi biên dịch M3.

## Điểm chưa thống nhất

1. Protocol đầu file ghi phiên bản String 1.0; cuối file và code dùng int 1.
   Luồng lỗi login cũ dùng AUTH_RESULT thất bại, code dùng ERROR.
   Tạo phòng cũ ghi ACK, bản ghép dùng ROOM_UPDATED giữ requestId.
   Bảng cấu hình ghi 100 kết nối/server.properties nhưng code giới hạn 32,
   không có server.properties. Cần Khánh thống nhất thân tài liệu với phần cuối.
2. Bản bàn giao 30/9 là ảnh chụp lịch sử, còn ghi M2/M3 NOT_IMPLEMENTED;
   cần đánh dấu rõ tài liệu nền cũ. Nó yêu cầu PasswordUtil ở server/security,
   code vẫn ở shared/security; thống nhất vị trí trước khi di chuyển.
3. Schema games chưa có roundId UUID UNIQUE như protocol, nên chưa đáp ứng
   chống lưu lặp kết quả M5. game_players.result chưa có FOLD và point_bets.action
   chưa có ALL_IN/ANTE; hand_type NORMAL khác tên DIEM trong lịch sử protocol.
   Nhật, Trọng, Khánh cần chốt trước khi viết lưu kết quả.
4. Quy tắc game không đặt cược trong bàn giao chưa khớp schema/tài liệu tố điểm.
   Seed 52 lá, 10/J/Q/K=0 là giả định đã ghi trong schema; phải xác nhận G01.
5. RoomManager gọi RoomEvents trong synchronized. Bản ghép chỉ xếp gửi vào queue
   nên không trực tiếp ghi socket chậm; mọi callback RoomEvents sau này cũng phải
   không block để tránh khóa toàn bộ room manager.
6. Room M3 luôn WAITING; chặn JOIN phòng PLAYING chỉ có thể nghiệm thu sau khi
   bổ sung trạng thái game M4. START_GAME_REQUEST trả NOT_IMPLEMENTED đúng phạm vi.

Các hàm updateProfile/changePassword/updateBalance và DTO game/history chưa làm
thuộc M4–M5, không yêu cầu triển khai để đóng phần Nhật đến M3.
