# Dữ liệu và giao dịch

MySQL 8.0.16 trở lên, InnoDB, UTF-8. database/setup.sql là nguồn schema cài mới/nâng cấp; bản server/src/main/resources/schema.sql được đồng bộ để phân phối cùng JAR. Cần chạy toàn bộ script trước khi mở server. Script lặp lại được và giữ tài khoản, số dư và lịch sử ván hiện có. Khi nâng cấp, script xóa các bảng và lịch sử xin/cho điểm đã ngừng hỗ trợ.

## Schema

| Bảng | Mục đích |
|---|---|
| users | Hồ sơ, role USER/ADMIN/MODERATOR, trạng thái, số dư và thống kê |
| app_migrations | Các migration đã áp dụng, gồm schema_v2_secure_admin |
| play_rounds | roundId duy nhất, content_hash, bàn runtime, ante, FINISHED/CANCELLED, ended_at |
| play_results | Kết quả từng participant, placement, profit, loại bài và điểm |
| play_cards | Bài riêng phục vụ lịch sử/kiểm toán; mỗi lá không trùng trong ván |
| play_bets | Thứ tự hành động và điểm đóng góp |
| play_pots / play_pot_players | Pot và các người đủ điều kiện nhận |
| play_ledger | Sổ cái do chốt ván: balance_before + delta = balance_after |
| admin_adjustments | Sổ cái điều chỉnh: mã ADJ, admin, tài khoản đích, delta, lý do và thời gian |

Sổ cái admin hợp nhất GAME và ADMIN.

Không có tài khoản admin dùng mật khẩu chung trong SQL. AdminBootstrap tạo mật khẩu riêng trước khi server bắt đầu nghe TCP; thông tin bootstrap được lưu trong tệp chỉ chủ sở hữu đọc được. Tài khoản admin tùy chỉnh không bị ghi đè.

## Tiền cược và tính nguyên tử

Engine quản lý số dư/vốn cược trong RAM trong lúc chơi. Kết quả được journal bền vững, rồi JdbcGameBUS lưu một transaction gồm: khóa users theo userId tăng dần; kiểm roundId/fingerprint; ghi kết quả, bài, cược, pot, ledger; cập nhật số dư và thống kê; commit. Tổng profit phải bằng 0. Retry cùng kết quả không cộng/trừ lần nữa; cùng roundId mà nội dung khác bị từ chối.

Admin điều chỉnh qua RoomManager, phối hợp trạng thái active/pending/start để tránh sửa số dư đang làm nền cho engine. UserDAO dùng FOR UPDATE, không cho số dư âm và ghi adjustment cùng transaction. Lý do từ 1 đến 255 ký tự là bắt buộc. Trang sổ cái hợp nhất play_ledger và admin_adjustments, có loại giao dịch, actor và lý do, sắp xếp ổn định.

Kết quả CANCELLED là luồng hủy quản trị rõ ràng. Leave/disconnect thông thường xử lý như fold theo Game_Rules.md, không hủy ván miễn phí khi bài xấu.

Engine cũng hủy và hoàn cược nếu không còn bất kỳ người nào chưa úp/chưa rời. Trường hợp còn người chơi nhưng một tầng pot không còn ai đủ điều kiện, chỉ tầng pot đó được hoàn cho người đã đóng góp; ván vẫn `FINISHED`. Chia tiền tính bằng số nguyên đơn vị 0,01 điểm rồi chuyển lại BigDecimal, tránh dùng số thực. Test engine kiểm bảo toàn tổng profit, số dư không âm và hoàn các tầng pot có số lẻ.

## Luật hiện tại và lịch sử cũ

Ván mới chỉ chia 36 lá A–9 và sinh loại bài `SAP` hoặc `DIEM`, với điểm từ 1 đến 10. Các enum 10/J/Q/K, `LIENG` và `BO_DOI` vẫn tồn tại để giải mã bài/kết quả đã lưu trước đó. Không đổi tên hoặc xóa các giá trị này để dọn code; không cần migration/xóa lịch sử cho đợt refactor này. Bài dùng chung và `Hand` bất biến được giữ trong RAM; không thêm cột cache đánh giá bài vào schema.

## Truy vấn và kết nối

DatabaseConfig dùng DriverManager và MySQL Connector/J như stack ban đầu. Semaphore của JDK giới hạn mặc định 12 kết nối JDBC đồng thời; chờ lượt tối đa 5 giây trước khi báo lỗi. Mỗi open() tạo kết nối độc lập. Connection.close() rollback giao dịch chưa commit, đóng kết nối vật lý và trả lượt, kể cả khi đóng nhiều lần. Không giữ connection nhàn rỗi hoặc tái sử dụng connection giữa các request.

Cấu hình BACAY_DB_MAX_CONNECTIONS cho phép 1–64 kết nối; BACAY_DB_ACQUIRE_TIMEOUT_MS cho phép 250–60000 ms chờ lượt mở. Giới hạn này bảo vệ số kết nối đồng thời; timeout kết nối/socket vẫn do các tham số JDBC URL điều khiển. Giải pháp không thêm dependency runtime; chi phí tạo kết nối mới cần được đo khi đánh giá tải.

checkSchema kiểm migration và các bảng/cột cần thiết trước khi nhận kết nối game. Schema không được dò INFORMATION_SCHEMA trên mọi yêu cầu leaderboard. Leaderboard có cache theo trang tối đa 64 mục, TTL 3 giây, được vô hiệu hóa khi chốt ván thành công. Tổng hợp lịch sử vẫn cần được đo với dữ liệu thực trước khi chọn thêm index hoặc thay bằng bảng thống kê.

Các khóa ngoại/unique/check của setup.sql là một phần tính toàn vẹn. Test H2 kiểm logic; MySqlSchemaTest chạy script sản phẩm hai lần trên một database ngẫu nhiên, kiểm nâng cấp schema cũ, giữ user, bootstrap admin, transaction và idempotency. CI chạy kiểm thử này với MySQL 8.4. Có thể bật tại máy phát triển bằng các biến BACAY_TEST_MYSQL_*.

Build Windows và CI kiểm hai bản schema giống nhau trước khi kiểm thử/đóng gói. Cách cấu hình MySQL test và kết quả lần xác minh gần nhất nằm trong [Testing.md](Testing.md); các test MySQL chỉ tạo/xóa database ngẫu nhiên của chính chúng, không dùng database game làm nơi chứa fixture.
