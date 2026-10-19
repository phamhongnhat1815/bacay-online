# Giao thức hiện hành

Client và server cùng dùng `shared.jar`, Packet protocol version 2. Mỗi yêu cầu có UUID request ID; phản hồi riêng giữ ID đó. Sự kiện push có request ID null. Chỉ DTO và kiểu dữ liệu trong allowlist của `PacketIO` được đọc từ mạng.

| Yêu cầu | Phản hồi |
|---|---|
| REGISTER, LOGIN | AUTH_RESULT; đăng ký không bind phiên, client đăng nhập ngay sau khi tạo thành công |
| LOGOUT | ACK |
| ROOM_LIST | ROOM_LIST_RESULT, luôn 20 RoomSummary theo thứ tự ID |
| JOIN_ROOM | ROOM_UPDATED; ID 1–20, chỉ bàn WAITING và còn ghế |
| LEAVE_ROOM | ACK; xử lý người đang chơi rời bàn tại server |
| SET_READY | ROOM_UPDATED |
| START_GAME_REQUEST | ACK và các push START_GAME, DEAL_CARD riêng, GAME_UPDATED, BET_UPDATED, PLAYER_STATE riêng |
| GAME_ACTION | ACK rồi / hoặc các push trạng thái; không được chọn user ID |
| CHAT_MESSAGE | ACK; CHAT_MESSAGE / CHAT_SYSTEM có RoomChatEvent để tách từng bàn |
| GET_PROFILE, UPDATE_PROFILE | PROFILE_RESULT của chính phiên đăng nhập |
| CHANGE_PASSWORD | ACK |
| GET_HISTORY, GET_LEADERBOARD | HISTORY_RESULT, LEADERBOARD_RESULT với PageResult |

Sau kết thúc: ROUND_RESULT, PLAY_AGAIN_PROMPT và ROOM_UPDATED trạng thái WAITING. ROUND_RESULT chỉ chứa bài được công khai; PLAYER_STATE cuối chứa số dư mới của chính người nhận. ROOM_UPDATED và ROOM_LIST_RESULT được phát khi bàn thay đổi. Phản hồi ERROR giữ request ID và có mã + thông báo nghiệp vụ.

Một số enum cũ được giữ để đọc source tương thích, nhưng giao diện sản phẩm không gọi CREATE_ROOM / ECHO_REQUEST / MUTE_PLAYER. CREATE_ROOM bị từ chối vì người chơi chọn trong 20 bàn có sẵn. PING/PONG là heartbeat nội bộ, không có nút hay log mạng trong giao diện người chơi.
