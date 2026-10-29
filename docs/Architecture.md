# Kiến trúc Ba Cây Online

Bốn module Maven dùng Java 21. shared chứa model Serializable, evaluator thuần và protocol v4; server chứa room/game/JDBC; client và admin là hai ứng dụng Swing độc lập. Đầu vào không được tin cậy chỉ vì được gửi từ ứng dụng chính thức.

Bộ thư viện chạy ứng dụng giữ như ban đầu. JDBC dùng DriverManager và MySQL Connector/J; TLS/JSSE, JUL, Semaphore và executor đều có sẵn trong JDK. Các thay đổi bảo mật giao thức không yêu cầu thêm framework hoặc thư viện runtime.

## Luồng chính

Client hoặc admin kết nối TLS và xác minh chứng chỉ máy chủ. PacketIO đọc frame có chiều dài giới hạn trước deserialization, lọc cấu trúc rồi validate payload. ClientHandler xác thực phiên, phân quyền và điều phối request. RoomManager quản lý 20 bàn cố định, membership, ready/countdown, active round và chốt kết quả. DefaultGameEngine xử lý bài, cược, fold, pot, kết quả; evaluator chung đảm bảo UI và máy chủ dùng cùng quy tắc.

Các gói bài riêng chỉ được gửi đến đúng người chơi. Endpoint ADMIN_INSPECT_ROOM_REQ được bảo vệ bằng quyền ADMIN. Tài khoản quản trị không tham gia chơi. Mật khẩu đăng nhập được kiểm bằng PBKDF2WithHmacSHA256 có salt 16 byte, hiện 310.000 vòng; TLS bảo vệ mật khẩu khi truyền.

## Đồng thời và I/O

RoomManager hiện dùng một monitor chung ngắn cho trạng thái RAM, **không phải khóa độc lập từng bàn**. Các tác vụ JDBC/fsync được tách khỏi monitor để bàn khác có thể tiếp tục hoạt động. Không đưa I/O chậm vào callback đang giữ monitor.

start() giữ monitor để kiểm trạng thái và reserve startingUsers, đọc hồ sơ/số dư mới ngoài khóa, rồi kiểm room version trước khi khởi tạo engine. adjustingUsers phối hợp điều chỉnh số dư admin; user đang active, starting hoặc pending không được điều chỉnh. Kiểm tra và reservation nằm cùng vùng đồng bộ để tránh race giữa điều chỉnh và snapshot đầu ván.

Khi hoàn tất, RoomManager chụp CompletedRound và đánh dấu journaling dưới khóa; enqueue/fsync chạy ngoài khóa; chỉ khi journal thành công mới giải phóng active keys, phát kết quả và cleanup engine. Journal thất bại giữ trạng thái có thể retry. ResultPersistence có khóa journal riêng; pendingFor() không cần chờ disk I/O. Auto-start JDBC được điều phối ngoài tick monitor bằng executor; RoomManager.close() dừng/drain tác vụ khi shutdown.

Server có giới hạn kết nối, sender queue giới hạn và heartbeat. Client có reader/sender/watchdog riêng; admin cũng có sender executor và timeout, không ghi socket đồng bộ từ EDT. Mọi cập nhật Swing trở về EDT. Reconnect có giới hạn và reset trạng thái theo snapshot server; phản hồi cũ không được cập nhật dialog soi bàn mới.

## Kết quả bền vững

CompletedRound gồm RoundResult, bàn runtime, ante và bài riêng dùng cho kiểm toán. ResultPersistence ghi Java serialization ra file .tmp, force(true), rồi đổi tên thành .round bằng ATOMIC_MOVE khi hỗ trợ. Tệp journal không phải JSON. Thư mục mặc định data/pending-results, có thể thay bằng BACAY_DATA_DIR.

Worker một luồng chốt DB ngay khi enqueue và thử lại mỗi 3 giây. Transaction khóa users theo ID tăng dần, kiểm roundId/fingerprint, ghi toàn bộ kết quả/ledger rồi cập nhật số dư/thống kê. Sau commit, worker xóa journal và pending state. Nếu crash sau commit nhưng trước xóa file, lần replay kế tiếp xác minh kết quả trùng và không trả tiền hai lần. Người chơi có kết quả pending bị chặn khỏi ván mới.

Không tự ý bỏ qua journal lỗi hoặc xóa file để giải phóng người chơi; việc này có thể làm mất kết quả tài chính. Giữ bản sao và xử lý nguyên nhân phục hồi trước khi mở lại các tài khoản liên quan.

## Quản trị và dữ liệu

AdminBootstrap tạo mật khẩu cài đặt riêng trước khi server nghe TCP, hoặc thay mật khẩu seed công khai cũ. Tệp bootstrap được bảo vệ quyền chủ sở hữu; mật khẩu không xuất hiện trong log. Admin dùng CHANGE_PASSWORD để đổi và xóa tệp bootstrap sau đó.

Điều chỉnh số dư được phối hợp qua RoomManager rồi ghi UserDAO transaction kèm actor/lý do. Sổ cái hợp nhất GAME và ADMIN. Explicit admin reset sử dụng luồng hủy/kết toán chung; leave/disconnect bình thường được xử lý như fold để tránh né thua bằng ngắt mạng.

DatabaseConfig dùng DriverManager với Semaphore của JDK để giới hạn số kết nối JDBC đồng thời và thời gian chờ lượt mở. Mỗi lần open() tạo một kết nối độc lập; Connection.close() rollback giao dịch chưa commit, đóng kết nối vật lý và trả lượt. Không giữ connection nhàn rỗi để tái sử dụng. checkSchema yêu cầu migration cùng các cột/bảng sản phẩm trước khi nhận client. Leaderboard cache có TTL 3 giây và giới hạn 64 trang; commit kết quả xóa cache. Connection provider, room executor và worker được đóng khi shutdown.

## Cấu hình và quan sát

RuntimeSettings đọc resource properties và Java system properties. Script Start-Server xử lý tham số, biến môi trường và mặc định mà không ghi đè tùy tiện cấu hình đã đặt. TLS mặc định bật; xem README để phân phối chứng chỉ công khai và giữ riêng keystore/password.

JUL ghi console và xoay vòng file. Ghi ngữ cảnh request/room/round khi cần, tránh mật khẩu và bài riêng. Kiểm thử gồm unit, H2 integration, protocol sockets, client/admin regression và MySQL schema/transaction thực trong CI. Việc test thành công không thay thế đo tải với số lượng bàn/người chơi và dữ liệu vận hành thực tế.
