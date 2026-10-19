# Cấu trúc bản game

## Đường chạy

`RunClient` mở Swing và `ConnectionController`. Khi bấm đăng nhập/đăng ký, client tự mở TCP tới máy chủ đã chọn. Không có nút ECHO, bảng log hay bước thử kết nối trong giao diện.

Server `ServerApplication` kiểm tra MySQL trước khi nhận client, tạo 20 bàn một lần và giữ nguyên ID 1–20 trong suốt phiên chạy. Bàn trống không bị xóa. `RoomManager` quyết định số ghế, chủ bàn, sẵn sàng và ai được bắt đầu. Thay đổi bàn được phát tới người trong bàn và tới sảnh bằng `ROOM_LIST_RESULT` không có request ID. Client cũng đồng bộ danh sách định kỳ để khôi phục hiển thị nếu cần.

`DefaultGameEngine` là nguồn xác định duy nhất cho chia bài, lượt, cược, pot phụ và kết quả. Client chỉ vẽ dữ liệu nhận được. `PLAYER_STATE` gửi riêng từng người; snapshot công khai không chứa bài chưa lật. Mỗi request có ID, server lưu phản hồi gần nhất để tránh xử lý lặp một hành động trong cùng kết nối. Heartbeat chạy nền để dọn phiên mất mạng.

`ResultPersistence` ghi kết quả vào journal trước khi phát kết quả ván, rồi `JdbcGameBUS` lưu transaction vào MySQL. Chống ghi trùng bằng round ID và nội dung. Tiền còn ở ván đang hoạt động hoặc đang chờ ghi không được dùng tiếp ở ván khác.

## Chỉnh giao diện ở đâu

| File trong `client/src/main/java/com/game3cay` | Phần phụ trách |
|---|---|
| `gui/AuthPanel.java` | Đăng nhập / đăng ký và hình bài ở màn chào |
| `gui/MainFrame.java` | Header điểm, hồ sơ, luật, âm thanh, máy chủ |
| `gui/LobbyPanel.java` | Lưới 4 hàng × 5 cột, đủ 20 bàn |
| `gui/RoomPanel.java` | Nút sẵn sàng/bắt đầu/rời bàn và chat |
| `gui/GameTablePanel.java` | Vẽ bàn asset, 8 ghế, bài, pot và kết quả |
| `gui/GamePanel.java` | Trạng thái lượt và nút theo/tố/úp/tất tay |
| `gui/DataDialog.java` | Bảng lịch sử, xếp hạng và phân trang |
| `gui/Theme.java` | Màu sắc, font, input và nút dùng asset |
| `gui/Assets.java` | Ánh xạ 52 lá và nạp ảnh ở độ phân giải hiển thị |
| `gui/Sound.java` | Âm thanh bấm/chia bài/đến lượt/thắng thua |
| `controller/ConnectionController.java` | Nối sự kiện giao diện với giao thức |
| `network/SocketClient.java` | TCP, hàng đợi gửi, nhận và heartbeat |

Asset nằm ở `client/src/main/resources/assets`. Avatar mặc định được chọn ổn định theo tài khoản. Nút ẩn bài chỉ thay đổi hiển thị trên máy hiện tại. Bàn luôn xoay để ghế của bạn ở phía dưới. Ảnh tham khảo có chữ và những phần của poker Texas Hold'em không được dùng làm giao diện Ba Cây.

## Chỉnh server ở đâu

- `server/room/RoomManager.java`: 20 bàn, 2–8 ghế, điểm cơ bản 10, sẵn sàng, chuyển chủ bàn, phát trạng thái.
- `server/game/DefaultGameEngine.java`, `HandEvaluator.java`: luật và thứ tự so bài theo `Game_Rules.md`.
- `server/game/ResultPersistence.java`: journal, retry ghi MySQL và khôi phục kết quả đã chốt.
- `server/bus/JdbcUserBUS.java`, `server/dal/UserDAO.java`: đăng ký 1.000 điểm, PBKDF2, hồ sơ, mật khẩu.
- `server/bus/JdbcGameBUS.java`: điểm, lịch sử, bảng xếp hạng, transaction.
- `server/network/ClientHandler.java`: xác thực request, quyền người chơi và định tuyến packet.

Các đường dẫn trên thuộc `server/src/main/java/com/game3cay`. `shared` chứa DTO bất biến và bộ lọc Object Stream theo danh sách kiểu cho phép. Giới hạn độ sâu/mảng và kiểm tra payload giúp chặn dữ liệu không đúng giao thức.

## Vận hành và giới hạn

Game chạy nhiều client trên một server/LAN, không có bot. Mỗi tài khoản chỉ có một phiên. Tối đa mặc định 256 kết nối; mỗi bàn tối đa 8 người. Server giữ trạng thái ván đang diễn ra trong RAM. Restart server đưa mọi bàn về trống; chỉ những kết quả đã chốt được phục hồi từ journal. Không chạy hai server trỏ vào cùng database vì khóa ván đang hoạt động nằm trong bộ nhớ từng process.

Cổng và heartbeat ở `server.properties` / `client.properties`. Bản giao giữ lượt 30 giây. Nếu thay `game.turnTimeoutSeconds` ở server, cập nhật thời gian minh họa trong `GameTablePanel`, câu hướng dẫn `GamePanel` và luật tương ứng. Không gửi tài khoản MySQL tới client. TCP Object Stream hiện tại dùng cho LAN tin cậy, chưa có TLS; đây là việc cần bổ sung nếu triển khai Internet.

## Kiểm tra của mã nguồn

Maven chạy các bài kiểm tra tài khoản, cuộc đua giành ghế cuối, 20 bàn tồn tại sau khi rỗng, chuyển chủ bàn, so bài, thứ tự lượt, all-in/pot phụ, riêng tư bài, chat đúng bàn, ghi kết quả lặp, khóa điểm và phục hồi journal. Integration test mặc định dùng H2 riêng trong bộ nhớ; có thể chọn MySQL test qua `BACAY_TEST_MYSQL_URL`. Không cần chạy các kiểm tra này trong luồng người chơi.
