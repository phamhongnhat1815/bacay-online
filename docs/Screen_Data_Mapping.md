# Giao diện và dữ liệu

Client và admin dùng Java Swing. Mọi cập nhật component chạy trên EDT; kết nối, ghi socket và chờ phản hồi chạy ngoài EDT. PacketIO.Input/Output dùng Packet v3; ứng dụng cũ dùng object stream trực tiếp không tương thích.

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

Nặn bài dùng evaluator chung của shared: J-Q-K là Liêng, tổng modulo 10 bằng 0 là 0 điểm. Variant nút được ánh xạ tới tài nguyên tồn tại và có fallback. Các asset thường dùng được preload ngoài EDT; animation chỉ chạy khi cần và component đang hiển thị.

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
