# Giao diện và dữ liệu

Client và admin dùng Java Swing. Mọi cập nhật component chạy trên EDT; kết nối, ghi socket và chờ phản hồi chạy ngoài EDT. PacketIO.Input/Output dùng Packet v4; ứng dụng cũ dùng object stream trực tiếp không tương thích.

## Client

MainFrame dùng CardLayout cho AuthPanel, LobbyPanel và RoomPanel. ConnectionController điều phối requestId, pending request, timeout, reconnect và marshal sự kiện mạng về EDT. SocketClient có sender queue giới hạn, reader và heartbeat watchdog.

| Giao diện | Gửi | Nhận |
|---|---|---|
| Đăng nhập/đăng ký | LOGIN/AuthRequest, REGISTER/RegisterRequest | AUTH_RESULT hoặc ERROR |
| Sảnh | ROOM_LIST, JOIN_ROOM/JoinRoomRequest | ROOM_LIST_RESULT, ROOM_UPDATED |
| Bàn chờ | SET_READY/Boolean, CHANGE_ROOM_BET/BigDecimal, START_GAME_REQUEST | ROOM_UPDATED |
| Ván | GAME_ACTION/GameActionRequest | START_GAME, DEAL_CARD, GAME_UPDATED, BET_UPDATED, PLAYER_STATE |
| Kết quả | PLAY_AGAIN_VOTE/Boolean | ROUND_RESULT, PLAY_AGAIN_PROMPT, ROOM_UPDATED |
| Chat | CHAT_MESSAGE/ChatMessage loại TEXT | CHAT_MESSAGE/CHAT_SYSTEM bọc RoomChatEvent |
| Hồ sơ | GET_PROFILE, UPDATE_PROFILE, CHANGE_PASSWORD | PROFILE_RESULT hoặc ACK |
| Lịch sử/BXH | GET_HISTORY hoặc GET_LEADERBOARD/PageRequest | HISTORY_RESULT hoặc LEADERBOARD_RESULT/PageResult |

SessionHeader gom Xếp hạng, Lịch sử ván và Xin / Cho điểm ở thanh đầu; tài khoản và số dư ở bên phải. Menu ••• chứa Luật chơi, Âm thanh và Đăng xuất. Tên và số dư dài có tooltip đầy đủ.

LobbyPanel có tìm theo số bàn và lọc Tất cả / Có người chờ / Bàn trống. Thẻ bàn giữ chiều rộng tối thiểu 224px, tự đổi số cột theo cửa sổ và cuộn dọc. Bộ lọc chỉ áp dụng ở client; Chơi ngay tìm trên toàn bộ danh sách bàn. Trạng thái bàn được thể hiện bằng chữ, số chỗ và màu. Dữ liệu chưa tải không được tính là bàn trống.

RoomPanel tách Trò chuyện và Điểm bạn bè. Nút cược được gom theo trình tự Úp bài / Kiểm tra hoặc Theo / nhập tổng điểm và Tố đến / Tất tay. Bàn chờ giữ nguyên cỡ chữ và nút khi hình bàn co giãn; trạng thái hướng dẫn dựa trên số người đủ điều kiện và sẵn sàng.

Theme thống nhất màu, font, khoảng cách, bảng dữ liệu, tab, trạng thái hover/focus/disabled. Nút và nền vẽ bằng Java2D có sẵn trong Swing, không thêm dependency. Điểm hiển thị theo định dạng Việt Nam; trường nhập thập phân dùng dấu chấm và không nhập dấu phân cách hàng nghìn.

Nặn bài dùng evaluator chung của shared: J-Q-K là Liêng, tổng modulo 10 bằng 0 là 0 điểm. Các asset còn dùng được preload ngoài EDT; animation chỉ chạy khi cần và component đang hiển thị. UiLayoutTest kiểm tra lọc/chọn bàn, chiều rộng thẻ, thanh đầu và bàn chờ ở kích thước tối thiểu. UiPreview xuất ảnh offline các màn hình để kiểm tra trực quan, không kết nối máy chủ hoặc DB.

Reconnect có hạn số lần và deadline cho từng bước. Lỗi xác thực tạm thời có thể retry; lỗi tài khoản kết thúc về đăng nhập. Khi server báo WAITING, client xóa ván cũ bất kể đã nhận kết quả trước đó hay chưa. UI được phục hồi từ trạng thái server thay vì suy diễn từ chuỗi sự kiện đã mất.

## Admin

AdminMainFrame gồm DashboardPanel, UsersPanel, RoomsPanel, LedgerPanel. AdminSocketClient dùng sender executor và deadline socket; việc trả CompletableFuture không đồng nghĩa được phép ghi socket trực tiếp trên EDT.

| Giao diện | Request |
|---|---|
| Thống kê | ADMIN_STATS_REQ |
| Người dùng | ADMIN_USERS_PAGE_REQ, ADMIN_LOCK_USER_REQ, ADMIN_ADJUST_BALANCE_REQ |
| Danh sách bàn | ADMIN_ROOMS_MONITOR_REQ |
| Soi bàn | ADMIN_INSPECT_ROOM_REQ |
| Reset quản trị | ADMIN_FORCE_RESET_ROOM |
| Thông báo | ADMIN_BROADCAST_REQ |
| Sổ cái | ADMIN_LEDGER_PAGE_REQ |
| Đổi mật khẩu quản trị | CHANGE_PASSWORD |

Sổ cái hiển thị cả giao dịch GAME và ADMIN, người thực hiện và lý do điều chỉnh. Response soi bàn chỉ được áp dụng khi roomId và instance/request generation còn khớp; mở bàn mới không nhận dữ liệu trễ của bàn cũ. Chuỗi do người dùng nhập được hiển thị như văn bản thuần, không được Swing diễn giải thành HTML.

Mật khẩu bootstrap có thể đổi trực tiếp trong ứng dụng admin. Tệp bootstrap cần được xóa sau khi người vận hành lưu thông tin mới an toàn.

## Xin điểm và cho điểm

RoomPanel có Xin điểm / Cho điểm và Yêu cầu điểm ở khối Điểm bạn bè. MainFrame có nút Xin / Cho điểm trong thanh đầu, truy cập được cả từ sảnh. PointAmountDialog chọn người cùng bàn, số điểm, lời nhắn, các mức 50/100/500 và bước xác nhận trước khi gửi.

PointExchangeDialog không modal: hộp yêu cầu nhận/đã gửi, đồng hồ hết hạn, cho điểm/từ chối/hủy/bỏ qua, cùng lịch sử đã cho/đã nhận có phân trang. Khi bàn đang chơi hoặc khác bàn, nút cho điểm từ yêu cầu bị khóa. Client vẫn cho phép hủy/từ chối yêu cầu còn chờ; máy chủ quyết định trạng thái cuối cùng.

ConnectionController xử lý POINT_REQUEST_UPDATED, POINT_TRANSFER_RESULT và BALANCE_UPDATED, đồng thời xếp hàng các lần đọc hộp thư/lịch sử theo bất biến một request đang chờ. PointOperationJournal lưu UUID/nội dung bằng Preferences trước khi gửi; reconnect/relaunch xác minh đúng thao tác cũ. Snapshot PENDING cũ không ghi đè yêu cầu đã kết thúc. Số dư đối tác không được hiển thị trong biên nhận.
