# Giao thức Packet v4

Packet v4 chạy trên TCP/TLS, bổ sung xin/cho điểm và đồng bộ số dư riêng tư. Phải cập nhật shared, server, client, admin cùng một bản; ứng dụng v2/v3 bị từ chối rõ ràng khi sai phiên bản.

## Khung dữ liệu

Mỗi frame gồm header 8 byte big-endian rồi một Java object stream độc lập:

| Trường | Độ dài | Giá trị |
|---|---|---|
| Magic | 2 byte | 0x3C33 |
| Version | 1 byte | 4 |
| Type | 1 byte | PacketType.ordinal() + 1 |
| Payload length | 4 byte | 1–1.048.576 byte |
| Payload | N byte | Một Packet đã serialize |

Không được đổi thứ tự PacketType trong một phiên bản protocol. Thêm/sửa định dạng không tương thích phải tăng Packet.VERSION. PacketIO kiểm tra magic, version, type và chiều dài **trước** khi cấp phát payload hoặc deserialize. Payload có bộ lọc maxdepth=24, maxarray=4096, maxrefs=20000 và maxbytes=1 MiB cùng whitelist. Mỗi frame có object stream riêng nên giới hạn tài nguyên không cộng dồn suốt đời kết nối.

Sau byte đầu của frame, toàn header/payload phải đọc xong trong 10 giây. Idle timeout được điều chỉnh ở từng phía; heartbeat server gửi PING có requestId và client phản hồi PONG cùng id. Máy chủ chỉ ghi nhận PONG khớp yêu cầu đang chờ.

## Request và push

Request có requestId dài tối đa 64 ký tự. Reply lặp lại id của request; push dùng null. ACK không có payload; ERROR có Packet.ErrorPayload(ErrorCode, message). Server lưu tối đa 128 reply gần nhất trên một kết nối để chống thực hiện lặp request cùng id. Đây không thay thế cơ chế idempotency của giao dịch DB qua roundId/fingerprint.

Các tên dưới đây là tên enum thực tế; mã số được tính từ thứ tự trong shared/network/PacketType.java:

| Nhóm | Request | Phản hồi / push |
|---|---|---|
| Tài khoản | REGISTER, LOGIN, LOGOUT | AUTH_RESULT, ACK, ERROR |
| Hồ sơ | GET_PROFILE, UPDATE_PROFILE, CHANGE_PASSWORD | PROFILE_RESULT, ACK |
| Sảnh/bàn | ROOM_LIST, JOIN_ROOM, LEAVE_ROOM, SET_READY, CHANGE_ROOM_BET, START_GAME_REQUEST | ROOM_LIST_RESULT, ROOM_UPDATED, ACK |
| Ván bài | GAME_ACTION, PLAY_AGAIN_VOTE | START_GAME, DEAL_CARD, PLAYER_STATE, GAME_UPDATED, BET_UPDATED, ROUND_RESULT, PLAY_AGAIN_PROMPT |
| Chat | CHAT_MESSAGE | CHAT_MESSAGE, CHAT_SYSTEM, ACK |
| Tra cứu | GET_HISTORY, GET_LEADERBOARD | HISTORY_RESULT, LEADERBOARD_RESULT |
| Quản trị | ADMIN_STATS_REQ, ADMIN_USERS_PAGE_REQ, ADMIN_LOCK_USER_REQ, ADMIN_ADJUST_BALANCE_REQ | ADMIN_STATS_RESP, ADMIN_USERS_PAGE_RESP, ACK |
| Quản trị bàn | ADMIN_ROOMS_MONITOR_REQ, ADMIN_INSPECT_ROOM_REQ, ADMIN_FORCE_RESET_ROOM | ADMIN_ROOMS_MONITOR_RESP, ADMIN_INSPECT_ROOM_RESP, ACK |
| Quản trị thông báo/sổ cái | ADMIN_BROADCAST_REQ, ADMIN_LEDGER_PAGE_REQ | CHAT_SYSTEM, ADMIN_LEDGER_PAGE_RESP, ACK |
| Cho điểm trực tiếp | POINT_TRANSFER (PointTransferRequest) | POINT_TRANSFER_RESULT (PointTransferReceipt), BALANCE_UPDATED riêng từng người |
| Xin điểm | POINT_REQUEST_CREATE, POINT_REQUEST_REPLY | POINT_REQUEST_UPDATED, POINT_TRANSFER_RESULT khi đồng ý |
| Hộp thư | POINT_REQUEST_LIST (PageRequest) | POINT_REQUEST_LIST_RESULT (PageResult<PointRequestItem>) |
| Lịch sử điểm | POINT_TRANSFER_HISTORY (PageRequest) | POINT_TRANSFER_HISTORY_RESULT (PageResult<PointTransferReceipt>) |

CREATE_ROOM vẫn có enum để xử lý yêu cầu cũ nhưng sản phẩm dùng 20 bàn cố định; server từ chối tạo thêm. Một số enum dành dự phòng không được cung cấp thành chức năng ở UI. Server từ chối packet đi sai chiều hoặc thiếu quyền; không dựa vào việc nút bị ẩn trên client.

Chat người chơi tối đa 500 ký tự. Nội dung broadcast admin tối đa 500 ký tự, sự kiện hệ thống tối đa 600 ký tự để chứa tiền tố máy chủ. Validation lỗi được trả trước khi gửi broadcast; lỗi packet nội bộ không được xử lý như lỗi đường truyền của mọi người nhận.

## Quyền và dữ liệu riêng

Chỉ phiên ADMIN truy cập endpoint quản trị, bao gồm soi bài trực tiếp. ADMIN không tham gia bàn với tư cách người chơi. LOGIN/REGISTER có giới hạn tốc độ trước khi tính hash. TLS bảo vệ mật khẩu, bài riêng và quyền quản trị trên đường truyền; certificate được xác minh theo hostname/IP.

Máy chủ phát bài riêng qua DEAL_CARD/PLAYER_STATE cho đúng userId. GAME_UPDATED/BET_UPDATED không chứa bài đối thủ. ROUND_RESULT chỉ phát thông tin theo chính sách kết thúc ván. Client không tự quyết định profit hay người thắng.

Snapshot có stateVersion. Client bỏ dữ liệu cũ và phải reconcile đầy đủ khi khôi phục kết nối; không được giả định đã nhận đủ mọi push trong thời gian mất mạng. Khi phòng trở về WAITING, trạng thái ván cũ phải được xóa dù client bỏ lỡ ROUND_RESULT.

Danh mục ErrorCode chính xác nằm tại shared/network/ErrorCode.java. Các mã đang dùng gồm INVALID_REQUEST, UNAUTHENTICATED, FORBIDDEN, INVALID_CREDENTIALS, ALREADY_LOGGED_IN, USER_BLOCKED, INVALID_STATE, STALE_ROUND, NOT_YOUR_TURN, INVALID_BET_AMOUNT, RATE_LIMIT_EXCEEDED và INVALID_PAGE.

## Xin/cho điểm và gửi lại

operationId là UUID nghiệp vụ, tách biệt requestId của socket. Chuyển điểm có UNIQUE(sender_id,operation_id), lưu fingerprint nội dung và biên nhận trong DB. Gửi lại cùng thao tác sau mất ACK/reconnect trả đúng biên nhận cũ; dùng lại mã với nội dung khác bị từ chối. Chỉ người được xin mới được đồng ý/từ chối; người xin được hủy. Chuyển điểm và chuyển yêu cầu sang FULFILLED commit cùng transaction.

POINT_REQUEST_UPDATED chỉ gửi hai bên. BALANCE_UPDATED chứa số dư của chính người nhận packet; PointTransferReceipt không chứa số dư hoặc thông tin riêng của đối tác. Client lưu thao tác chưa rõ kết quả bằng Preferences trước khi gửi, giữ nguyên operationId để xác minh sau khôi phục; không tự tạo một giao dịch mới thay thế.
