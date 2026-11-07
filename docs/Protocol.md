# Giao thức Packet v5

Packet v5 chạy trên TCP/TLS. Phải cập nhật shared, server, client, admin cùng một bản; ứng dụng phiên bản cũ bị từ chối rõ ràng khi sai phiên bản.

## Khung dữ liệu

Mỗi frame gồm header 8 byte big-endian rồi một Java object stream độc lập:

| Trường | Độ dài | Giá trị |
|---|---|---|
| Magic | 2 byte | 0x3C33 |
| Version | 1 byte | 5 |
| Type | 1 byte | PacketType.ordinal() + 1 |
| Payload length | 4 byte | 1–1.048.576 byte |
| Payload | N byte | Một Packet đã serialize |

Không được đổi thứ tự PacketType trong một phiên bản protocol. Thêm/sửa định dạng không tương thích phải tăng Packet.VERSION. PacketIO kiểm tra magic, version, type và chiều dài **trước** khi cấp phát payload hoặc deserialize. Payload có bộ lọc maxdepth=24, maxarray=4096, maxrefs=20000 và maxbytes=1 MiB cùng whitelist. Mỗi frame có object stream riêng nên giới hạn tài nguyên không cộng dồn suốt đời kết nối.

Sau byte đầu của frame, toàn header/payload phải đọc xong trong 10 giây. Idle timeout được điều chỉnh ở từng phía; heartbeat server gửi PING có requestId và client phản hồi PONG cùng id. Máy chủ chỉ ghi nhận PONG khớp yêu cầu đang chờ.

Admin dùng connect/handshake timeout 5 giây và idle timeout 5 giây khi chờ byte đầu của lời chào, chỉ chấp nhận `SERVER_NOTICE` làm lời chào đầu. Sau đó idle timeout là 300 giây; giới hạn đọc một frame vẫn là 10 giây tính từ byte đầu. Request có deadline 10 giây và hết hạn sẽ đóng phiên. PONG được ghi qua sender queue, không chuyển PING thành push giao diện admin. Đóng phiên làm thất bại các request chưa nhận phản hồi. Client người chơi dùng các timeout trong `client.properties` và vẫn chuyển PING tới listener sau khi xếp hàng PONG.

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

CREATE_ROOM vẫn có enum để xử lý yêu cầu cũ nhưng sản phẩm dùng 20 bàn cố định; server từ chối tạo thêm. Một số enum dành dự phòng không được cung cấp thành chức năng ở UI. Server từ chối packet đi sai chiều hoặc thiếu quyền; không dựa vào việc nút bị ẩn trên client.

Chat người chơi tối đa 500 ký tự. Nội dung broadcast admin tối đa 500 ký tự, sự kiện hệ thống tối đa 600 ký tự để chứa tiền tố máy chủ. Validation lỗi được trả trước khi gửi broadcast; lỗi packet nội bộ không được xử lý như lỗi đường truyền của mọi người nhận.

## Quyền và dữ liệu riêng

Chỉ phiên ADMIN truy cập endpoint quản trị, bao gồm soi bài trực tiếp. ADMIN không tham gia bàn với tư cách người chơi. LOGIN/REGISTER có giới hạn tốc độ trước khi tính hash. TLS bảo vệ mật khẩu, bài riêng và quyền quản trị trên đường truyền; certificate được xác minh theo hostname/IP.

Máy chủ phát bài riêng qua DEAL_CARD/PLAYER_STATE cho đúng userId. GAME_UPDATED/BET_UPDATED không chứa bài đối thủ. ROUND_RESULT chỉ phát thông tin theo chính sách kết thúc ván. Client không tự quyết định profit hay người thắng.

Luật hiện tại dùng 40 lá A–10, chỉ sinh `SAP`/`DIEM`; J/Q/K và `LIENG`/`BO_DOI` vẫn giữ tên enum để đọc dữ liệu cũ. Refactor không đổi frame, thứ tự PacketType hoặc phiên bản v5. Khi chỉ còn một người chưa úp, hoặc ván bị hủy, ROUND_RESULT không công khai bài riêng; khi showdown, chỉ tay bài của người chưa úp được công khai. Đối chiếu [luật chơi](../Game_Rules.md) khi thay đổi ý nghĩa dữ liệu, và chạy [kiểm thử protocol/TLS](Testing.md) trước khi phân phối đồng bộ các ứng dụng.

Snapshot có stateVersion. Client bỏ dữ liệu cũ và phải reconcile đầy đủ khi khôi phục kết nối; không được giả định đã nhận đủ mọi push trong thời gian mất mạng. Khi phòng trở về WAITING, trạng thái ván cũ phải được xóa dù client bỏ lỡ ROUND_RESULT.

Sau khi admin điều chỉnh điểm, máy chủ gửi PROFILE_RESULT dạng push riêng cho tài khoản đó, trước khi cho phép bắt đầu ván hoặc điều chỉnh tiếp. Client áp dụng số dư này ở sảnh, bàn chờ hoặc sau kết quả; khi đang cược, PLAYER_STATE là nguồn số dư của ván. Khóa tài khoản gửi ERROR(USER_BLOCKED) dạng push trước khi đóng kết nối; client dừng khôi phục phiên tự động. Máy chủ kiểm tra lại trạng thái tài khoản sau khi gắn phiên đăng nhập để khóa tài khoản có hiệu lực cả khi đăng nhập đang xử lý.

Danh mục ErrorCode chính xác nằm tại shared/network/ErrorCode.java. Các mã đang dùng gồm INVALID_REQUEST, UNAUTHENTICATED, FORBIDDEN, INVALID_CREDENTIALS, ALREADY_LOGGED_IN, USER_BLOCKED, INVALID_STATE, STALE_ROUND, NOT_YOUR_TURN, INVALID_BET_AMOUNT, RATE_LIMIT_EXCEEDED và INVALID_PAGE.
