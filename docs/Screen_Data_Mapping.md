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

RoomPanel tách Trò chuyện và Điểm bạn bè. Nút cược được gom theo trình tự Úp bài / Kiểm tra hoặc Theo / nhập tổng điểm và Tố đến / Tất tay. Trạng thái chuẩn bị và đếm ngược nằm trong thanh 64px dưới bàn, không gắn component sẵn sàng vào GameTablePanel. Thanh chỉ hiện khi room WAITING và client không có ván đang chạy; START_GAME ẩn ngay cả khi snapshot PLAYING chưa đến. Nút Tạm nghỉ / Chơi tiếp vẫn gửi SET_READY qua controller, nút Bắt đầu ngay chỉ dành cho chủ bàn khi hợp lệ.

Client không còn checkbox, Preferences hoặc timer tự sẵn sàng trùng với quy trình đếm ngược của máy chủ. Snapshot chỉ cập nhật hiển thị, không tự gửi SET_READY; do đó không tự hủy lựa chọn Tạm nghỉ hoặc xác nhận lại mức cược/số dư vừa thay đổi. Luồng server hiện có về tự chuẩn bị sau kết quả, kiểm tra đủ người, số dư và hủy đếm ngược khi có người chưa sẵn sàng được giữ nguyên. ReadinessUiTest bao phủ không che bàn, ẩn khi bắt đầu, pause/resume chỉ phát request khi bấm, trạng thái người xem và cửa sổ tối thiểu.

Theme thống nhất màu, font, khoảng cách, bảng dữ liệu, tab, trạng thái hover/focus/disabled. Nút và nền vẽ bằng Java2D có sẵn trong Swing, không thêm dependency. Điểm hiển thị theo định dạng Việt Nam; trường nhập thập phân dùng dấu chấm và không nhập dấu phân cách hàng nghìn.

Nặn bài dùng evaluator chung của shared: J-Q-K là Liêng, tổng modulo 10 bằng 0 là 0 điểm. Các asset còn dùng được preload ngoài EDT; animation chỉ chạy khi cần và component đang hiển thị. UiLayoutTest kiểm tra lọc/chọn bàn, chiều rộng thẻ, thanh đầu và bàn chờ ở kích thước tối thiểu. UiPreview xuất ảnh offline các màn hình để kiểm tra trực quan, không kết nối máy chủ hoặc DB.

Khi chia bài, cả ba lá riêng của người chơi được úp. HandRevealState chỉ đổi khi người chơi chủ động nặn hoặc bấm Mở hết; PLAYER_STATE/GAME_UPDATED và START_GAME lặp cùng ván không tự lật bài. CardSqueezePanel cho giữ phần dưới lá rồi kéo lên: lưng bài trượt lên theo chuột, mặt bài hé từ mép dưới với viền giấy cuộn. Thả sau khi hé ít nhất 60% để mở; kéo ngắn thì phần hé thu lại. Kéo ngược xuống không làm mở bài. Có nút mở lá đang chọn/mở cả ba và phím mũi tên/Space. Phân loại bài chỉ hiển thị sau khi cả ba lá đã mở. Đóng hộp giữ tiến độ của ván đó; generation ngăn input cũ mở bài trong ván tiếp theo.

CardSqueezeDialog không modal, hiển thị thời gian còn lại khi đến lượt. Đồng hồ không khởi động lại khi chỉ stateVersion thay đổi trong cùng lượt. Kết quả ván, ván mới và rời bàn đóng hộp nặn cũ; mở/ẩn bài chỉ là lựa chọn hiển thị cục bộ, không thay đổi luật cược hoặc nội dung gửi máy chủ. PlayingCardArt dùng viền bo góc và bóng cho cả mặt trước/mặt sau; ba lá của mình có khe hở. GameIcons để khoảng trống quanh toàn bộ chân tròn của chip. HandExperienceTest bao phủ thao tác nặn, đóng/mở lại, snapshot/round reset, không lộ mặt bài, đồng hồ và vùng vẽ chip.

Assets giải mã ảnh gốc rồi thu nhỏ từng bước có lọc; không bỏ hàng/cột pixel bằng source subsampling. Master đã lọc được giữ ở độ phân giải giới hạn (lá bài 512px, nhân vật 512px, avatar 256px), không giữ toàn bộ 52 ảnh scan gốc trong RAM. Khi vẽ, kích thước ảnh được tính theo transform thực của Graphics2D, gồm HiDPI và tỷ lệ của bàn. Cache các bản co giãn dùng LRU, giới hạn 8 triệu pixel (khoảng 32 MB dữ liệu pixel) và 192 mục. Alpha premultiplied tránh viền màu từ pixel trong suốt. Icon chip và vương miện được vẽ bằng Java2D, không có nền trắng và không phụ thuộc độ phân giải PNG.

AssetRenderingTest kiểm tra chi tiết nhỏ không bị alias, mép trong suốt không lẫn màu và icon ở 200% dùng đủ pixel vật lý. Chạy UiPreview với `-Dpreview.scale=2` để xuất ảnh ở 200%; `-Xmx256m` dùng kiểm tra bộ nhớ trong phiên preview, không thay cấu hình ứng dụng.

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
