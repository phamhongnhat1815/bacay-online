# BÀN GIAO NỀN VÀ HƯỚNG DẪN TRIỂN KHAI M1–M3

Dự án: bacay-online — BTL Lập trình mạng  
Thành viên: Thành, Khánh, Nhật, Trọng  
Cập nhật: 30/09/2026

## 1. Đọc trước khi bắt đầu

Phần nền hiện tại dùng để cả nhóm tiếp tục triển khai trên cùng cấu trúc và hợp đồng. Chưa hoàn thành toàn bộ M1–M3 và chưa có game chạy đầy đủ.

| Mốc theo bảng tiến độ nhóm | Mục tiêu | Tình trạng tại thời điểm bàn giao |
|---|---|---|
| M1 | Kết nối TCP và nhận sự kiện | Đã có code server/client, gửi thử và heartbeat. Cần nghiệm thu chạy thực tế giữa hai phía. |
| M2 | Tài khoản xuyên suốt Swing → server → MySQL | Đã có DTO và interface BUS; chưa nối nghiệp vụ tài khoản và phiên đăng nhập. |
| M3.1 | Lobby, phòng chờ, quyền thao tác | Đã có DTO, RoomService và RoomEvents; chưa có triển khai quản lý phòng. |
| M3.2 | Chat và thông báo đúng phòng | Đã có ChatMessage và điểm ghép; chưa có xử lý/broadcast chat. |

**Bằng chứng hiện có:** log Maven của Thành lúc 04:25 ngày 30/09/2026 báo `BUILD SUCCESS` cho shared, server, client trên Java 21. Log ghi `No tests to run`: đây là xác nhận biên dịch/đóng gói, không phải xác nhận toàn bộ chức năng đã được kiểm thử.

Tài liệu đối chiếu bản phân công, bảng tiến độ, mã nguồn trong `bacay-online(6).zip` và các sửa lỗi sau ZIP được phản ánh trong log build. ZIP này có trước bước bổ sung `Packet.ErrorPayload` và sửa cấu hình/test Spring. **Bản nền để mọi người lấy phải là code Thành đã sửa và build thành công, được commit/push lên main; không lấy lại ZIP cũ làm chuẩn.** Chưa có mã commit của bản build thành công trong thông tin bàn giao; Thành ghi mã đó khi đẩy lên main.

## 2. Quy ước dùng chung

- Java 21, Maven Wrapper; client Java Swing; server Java TCP; MySQL/JDBC cho phần Nhật triển khai.
- Không dùng Spring Boot/REST/JWT cho luồng hiện tại. Nhật không dựng một web server riêng.
- Ba module `shared`, `server`, `client`; package thống nhất `com.game3cay`. Client hiện có các package như `com.game3cay.network`; không tự chuyển tất cả sang package mới.
- `shared` là thư viện dùng chung, không có chương trình cần chạy riêng. Không chép các lớp shared sang client/server.
- Giữ các hợp đồng đã ghép từ Khánh: `UserBUS`, `BUSException`, `UserDTO`, `RoomSnapshot`, `ChatMessage`. Sửa hợp đồng phải phối hợp các bên dùng nó.
- Không chép gói `Nen_bo_sung` cũ đè lên nền này.
- `main` là nơi nhận nền và tích hợp theo từng phần. Theo cách làm hiện tại của nhóm, không bắt buộc mở nhánh mới; cần phối hợp thứ tự push và người sửa file chung.

### Những điểm tài liệu/code chưa thống nhất

| Điểm | Cách làm việc tiếp |
|---|---|
| Bản phân công có dòng PostgreSQL nhưng các mốc và phần việc Nhật ghi MySQL | Bàn giao này theo MySQL/JDBC đã thống nhất. Nhật sửa tài liệu/config mẫu cho nhất quán; không triển khai hai hệ DB. |
| Bản phân công mô tả game giải trí không đặt cược, nhưng code có `balance`, `betAmount`, `updateBalance` | Các trường đang tồn tại không phải bằng chứng đã chốt luật. Giữ chữ ký hiện tại khi ghép; Trọng, Khánh, Nhật và Thành cần chốt ý nghĩa hoặc bỏ đồng bộ trước khi hoàn thiện tạo phòng/game. Không tự làm thêm chức năng tiền/cược từ các tên trường này. |
| `PacketIO` hiện bắt `betAmount > 0` | Đây là giới hạn kỹ thuật của nền hiện tại, đang lệch với mô tả không đặt cược. Gửi 0/null sẽ bị từ chối. Nhóm phải sửa đồng bộ DTO/validation/UI nếu chọn bỏ trường; không lách bằng dữ liệu giả để nghiệm thu. |
| Comment cũ trong UserBUS nhắc PasswordUtil ở shared và connection pool | Vị trí đã thống nhất cho PasswordUtil là `server/security`. Nhật cập nhật comment/import; comment về pool không có nghĩa đã có pool hoặc đã an toàn đồng thời. |

## 3. Nền có gì và ai tiếp quản?

Các đường dẫn Java dưới đây tính từ `src/main/java/` của module tương ứng.

| Khu vực/file | Đã chuẩn bị | Người tiếp tục |
|---|---|---|
| POM gốc và `server/pom.xml` | Build chung, server phụ thuộc shared; bỏ nền Spring | Thành bàn giao bản build được; các bên giữ tương thích |
| shared: `com/game3cay/shared/network/Packet`, `PacketType`, `ErrorCode` | Khung packet, enum, mã lỗi; ErrorPayload nằm trong Packet | Khánh quản lý, Thành phối hợp |
| shared: `PacketIO` | Đọc/ghi Object Stream, lọc lớp, kiểm tra payload, reset/flush và đồng bộ đường ghi | Khánh |
| shared: `AuthRequest`, `RegisterRequest`, `AuthResult` | Dữ liệu tài khoản qua mạng | Nhật + Khánh + Thành |
| shared: `CreateRoomRequest`, `JoinRoomRequest`, `RoomSummary`, `RoomSnapshot`, `ChatMessage` | Dữ liệu phòng và chat qua mạng | Khánh + Thành |
| server: `com/game3cay/server/ServerApplication` | Nghe TCP, mặc định cổng 8888; tối đa 32 worker xử lý kết nối | Khánh |
| server: `network/ClientHandler` | Một handler/kết nối; gửi SERVER_NOTICE, trả ACK, xử lý heartbeat | Khánh |
| server: `bus/UserBUS`, `UserDTO`, `BUSException` | Hợp đồng nghiệp vụ tài khoản nội bộ server | Nhật triển khai; Khánh gọi |
| server: `room/RoomService`, `RoomEvents` | Điểm ghép quản lý phòng và phát cập nhật | Khánh triển khai |
| server: `game/RoundContext` | Dữ liệu khởi tạo ván nội bộ, chưa phải Engine | Trọng |
| client: `RunClient`, `SocketClient`, `ConnectionController`, `MainFrame` | Client thử kết nối; nhận nền, gửi thử và tự trả PONG | Thành |

`ClientHandler` hiện vẫn trả `ERROR / NOT_IMPLEMENTED` cho LOGIN, REGISTER, LOGOUT, ROOM_LIST, CREATE_ROOM, JOIN_ROOM, LEAVE_ROOM, SET_READY, START_GAME_REQUEST và CHAT_MESSAGE. Có enum/DTO không có nghĩa chức năng đã hoạt động.

## 4. Hợp đồng mạng phải dùng

### 4.1. Packet và kết nối

- `Packet.VERSION = 1` là số nguyên; không đổi thành chuỗi `"1.0"` hoặc tự nâng lên phiên bản 2.
- Client tạo yêu cầu bằng `Packet.request(type, data)` để có requestId. Phản hồi giữ đúng requestId của yêu cầu.
- Sự kiện chủ động từ server như cập nhật phòng/chat có thể dùng requestId null; heartbeat PING có ID và PONG phải giữ ID đó.
- Server kiểm tra heartbeat theo nhịp 5 giây, timeout 15 giây; client tự trả PONG. Người dùng không cần bấm nút để giữ kết nối. Thời điểm phát hiện có thể trễ thêm một nhịp kiểm tra.
- Mỗi socket chỉ có một bộ Object Stream và một luồng đọc. Tạo OutputStream, flush rồi tạo InputStream ở hai phía.
- Gửi qua `PacketIO`; không tạo stream mới mỗi tin. Kiểm tra payload và ObjectInputFilter phải được cập nhật cùng DTO mới.
- Danh sách truyền qua mạng dùng `ArrayList`, `List.of()` hoặc `List.copyOf()` như nền đã hỗ trợ; không tùy ý đổi sang loại collection khác mà chưa thử serialize.
- Payload sai định dạng hiện gây lỗi I/O và có thể đóng kết nối. Muốn trả mã lỗi nghiệp vụ cho từng trường hợp phải thiết kế lại ranh giới validation/handler có chủ đích; chưa được coi là sẵn có.

### 4.2. Payload đã có

| PacketType | Data hiện được chấp nhận | Công dụng |
|---|---|---|
| ECHO_REQUEST | String không trống, tối đa 1000 ký tự | Thử yêu cầu/phản hồi; hiện server trả ACK, không trả lại chuỗi |
| ACK | null | Xác nhận, giữ requestId |
| SERVER_NOTICE | String không trống, tối đa 1000 ký tự | Thông báo từ server |
| LOGIN | AuthRequest(username, password) | Nhật xác thực, Khánh gắn phiên |
| REGISTER | RegisterRequest(username, password, displayName, email) | Nhật tạo tài khoản |
| AUTH_RESULT | AuthResult | Kết quả tài khoản; Khánh chuyển từ UserDTO |
| ERROR | Packet.ErrorPayload(code, message) | UI xử lý theo code; message để hiển thị |
| LOGOUT, ROOM_LIST, LEAVE_ROOM, START_GAME_REQUEST | null | Người thực hiện được lấy từ phiên |
| CREATE_ROOM | CreateRoomRequest(roomName, minPlayers, maxPlayers, betAmount) | Tạo phòng; lưu ý điểm chưa thống nhất ở mục 2 |
| JOIN_ROOM | JoinRoomRequest(roomId), roomId > 0 | Tham gia phòng theo ID |
| SET_READY | Boolean | Đổi sẵn sàng |
| ROOM_LIST_RESULT | List<RoomSummary>, tối đa 100 phần tử | Danh sách phòng; chưa có phân trang hoàn chỉnh |
| ROOM_UPDATED | RoomSnapshot | Trạng thái phòng gồm chủ phòng, players, stateVersion |
| CHAT_MESSAGE, CHAT_SYSTEM | ChatMessage | Tin nhắn không trống, tối đa 500 ký tự |

`AuthResult` gồm success, userId, username, displayName, balance, errorCode, message. `UserDTO` nằm ở server; không gửi trực tiếp qua socket. `ErrorCode` nằm ở shared; server chỉ import.

Client gửi chat TEXT bằng ChatMessage nhưng server chỉ sử dụng nội dung hợp lệ, tự xác định phòng/người gửi từ phiên và tạo thời gian mới. Không tin senderId, tên hoặc timestamp do client khai. Client không được tự phát CHAT_SYSTEM; emoji chưa thuộc nghiệm thu M3.2.

### 4.3. Khánh cần chốt trước khi Thành nối UI nghiệp vụ

Những quy ước sau là **đề xuất ghép, chưa được ClientHandler hiện tại triển khai**; ghi quyết định vào `docs/Protocol.md` và báo Thành/Nhật trước khi dùng:

- REGISTER thành công: trả AUTH_RESULT, không tự đăng nhập; người dùng thực hiện LOGIN để tạo phiên.
- LOGIN thành công: gắn phiên trước khi trả AUTH_RESULT. Lỗi nghiệp vụ trả ERROR/ErrorPayload; thống nhất cách dùng các trường lỗi trong AuthResult, tránh UI phải đoán hai cơ chế.
- LOGOUT thành công: dọn phòng/phiên, trả ACK; nếu giữ socket mở thì client về màn đăng nhập. Lỗi mạng thì đóng kết nối và dọn đúng một lần.
- CREATE_ROOM/JOIN_ROOM/SET_READY: xác định rõ phản hồi cho người yêu cầu và ROOM_UPDATED broadcast. Chọn một đầu mối phát sự kiện để tránh broadcast hai lần từ cả RoomManager và handler.
- LEAVE_ROOM: trả ACK cho người rời, gửi ROOM_UPDATED cho người còn lại; người rời quay lại lobby và tải danh sách.
- CHAT_MESSAGE: thống nhất ACK cho người gửi và sự kiện chat cho phòng; requestId phải đủ để UI kết thúc trạng thái chờ.
- START_GAME_REQUEST: RoomService chưa có hàm start. Chốt điểm kiểm tra quyền/sẵn sàng với G01; khởi tạo/chạy ván thật thuộc M4. Chưa có Engine thì không trả thành công giả.

## 5. Việc cần làm của từng người

### Khánh — mạng server, phiên, phòng/chat

1. Lấy nền main đã build được, chạy nghiệm thu M1 cùng Thành. Cập nhật Protocol.md theo code hiện tại.
2. Bổ sung quản lý phiên và ánh xạ userId → kết nối để gửi tới người dùng đã xác thực. Các handler phải dùng chung dịch vụ/quản lý phòng, không mỗi client một RoomManager độc lập.
3. Nối REGISTER/LOGIN tới UserBUS của Nhật. Chỉ gắn danh tính sau xác thực thành công; chặn đăng nhập đồng thời cùng tài khoản bằng kiểm tra/cập nhật nguyên tử.
4. Bổ sung kiểm tra UNAUTHENTICATED cho chức năng yêu cầu đăng nhập. Logout, lỗi socket và timeout đi qua đường dọn phiên/phòng thống nhất, không dọn hai lần.
5. Viết `RoomManager implements RoomService`: listRooms, create, join, leave, setReady, chat, onDisconnected; dùng đúng chữ ký hiện có.
6. Hiện thực RoomEvents để gửi ROOM_UPDATED/CHAT_MESSAGE/CHAT_SYSTEM đến danh sách người nhận. Tạo snapshot độc lập, tăng stateVersion khi phòng đổi.
7. Kiểm tra phòng 2–8 người, một người tối đa một phòng; chỉ vào phòng WAITING. Chủ phòng rời thì chuyển cho người vào sớm nhất còn lại; phòng trống thì xóa.
8. Bảo đảm hai yêu cầu tranh chỗ cuối không làm vượt sức chứa. Cập nhật cùng phòng theo thứ tự; không giữ khóa phòng khi truy vấn DB hoặc gửi socket chậm. Nền chưa có hàng đợi gửi riêng phía server, cần xử lý điều này khi triển khai broadcast.
9. Chat chỉ tới đúng phòng, giới hạn 500 ký tự và 5 tin/10 giây. Giới hạn tần suất chưa có trong nền. Phân biệt tin sai cấu trúc với lỗi nghiệp vụ cần trả mã.

Bàn giao cho Thành: server chạy được, bảng request/response/event, mã lỗi, ví dụ dữ liệu và bước thử nhiều client. Bàn giao cho Nhật: cách khởi tạo/gắn UserBUS vào server và quy tắc xử lý BUSException bằng `getErrorCode()`.

### Nhật — UserBUS, MySQL và JDBC

1. Dựng schema/config MySQL, driver JDBC trong server/pom.xml và DAL/DAO; cung cấp script cùng hướng dẫn khởi tạo trên máy khác. File SQL/config tồn tại trong repo chưa chứng minh đã kết nối DB thành công.
2. Triển khai `UserBUS.register(String username, String rawPassword, String displayName, String email)` trả UserDTO.
3. Triển khai `UserBUS.authenticate(String username, String rawPassword)` trả UserDTO; thất bại ném BUSException với mã đúng hợp đồng.
4. Dùng hash/verify mật khẩu ở phía server, không log mật khẩu/hash, không đưa chúng vào UserDTO. Kiểm tra nội dung PasswordUtil thực tế trước khi dùng.
5. Username duy nhất được ràng buộc trong DB, kể cả hai đăng ký đồng thời. Dùng PreparedStatement và quản lý connection/tài nguyên đúng vòng đời.
6. Chạy thử BUS trực tiếp trước khi có UI: tạo tài khoản, đăng nhập đúng/sai, tên trùng, tài khoản bị khóa, DB không truy cập được.
7. Giữ các hàm getProfile/updateProfile/changePassword/updateBalance trong interface. Phần chưa triển khai cần thể hiện rõ, không trả dữ liệu thành công giả; hồ sơ/lưu kết quả chưa là điều kiện hoàn thành M2.

Bàn giao cho Khánh: lớp triển khai và cách tạo UserBUS, config mẫu không chứa mật khẩu thật, SQL, dữ liệu thử, danh sách mã lỗi và kết quả kiểm tra. Phiên online/socket do Khánh quản lý, không giao cho DB thay thế.

### Thành — Swing và mạng client

1. Chốt M1 với Khánh bằng server thật: gửi thử nhiều lần, nhận thông báo chủ động, giữ kết nối khi không thao tác và phát hiện mất kết nối.
2. Làm màn đăng ký/đăng nhập/logout, gửi các DTO có sẵn và xử lý AUTH_RESULT/ERROR theo quy ước đã chốt.
3. Làm lobby, danh sách phòng, tạo/vào/rời phòng, chủ phòng và trạng thái sẵn sàng; cập nhật từ RoomSnapshot. Không tự quyết định kết quả thao tác chỉ vì send() trả true.
4. Làm chat theo phòng; xử lý ROOM_UPDATED, CHAT_MESSAGE, CHAT_SYSTEM và các mã lỗi. Bỏ snapshot cũ dựa trên roomId/stateVersion, dọn trạng thái khi đổi/rời phòng.
5. Giữ một kết nối cho phiên sử dụng. Đọc mạng ở luồng nền, cập nhật Swing qua EDT; có trạng thái chờ, timeout phản hồi và xử lý mất kết nối.

Có thể làm UI với dữ liệu mẫu ngay; đặt mock riêng và tắt khi thử tích hợp. Bàn giao ảnh/log thao tác, bước tái hiện lỗi và commit đã kiểm tra cho Khánh.

### Trọng — chốt luật và làm Engine độc lập

1. Chốt G01: điều kiện bắt đầu, cách tính điểm/hòa, người rời, ván hủy và ý nghĩa các trường balance/betAmount đang lệch tài liệu.
2. Dùng RoundContext làm đầu vào tham khảo hiện có, phối hợp Khánh/Nhật khi cần chỉnh. Đây là dữ liệu nội bộ server, không gửi socket.
3. Viết bộ bài/chia bài/tính điểm và trạng thái ván trong server/game; kiểm thử bằng dữ liệu mẫu, không phụ thuộc Swing/socket/DB.
4. Công bố API Engine và dữ liệu kết quả cho Khánh/Nhật chuẩn bị M4–M5. Không cần chờ M2/M3 xong mới làm Engine.

Trọng không có đầu ra Engine chạy thật bắt buộc ở M1–M3 theo bảng tiến độ; cần cung cấp điều kiện bắt đầu từ G01 cho phần phòng. Một ván hoàn chỉnh thuộc M4.

## 6. Thứ tự làm và ghép lên main

| Bước | Người thực hiện | Đầu ra để bên tiếp theo dùng |
|---|---|---|
| 1. Bàn giao nền | Thành | Commit/push bản build thành công, ghi mã commit; cả nhóm pull cùng nền |
| 2. Nghiệm thu M1 | Thành + Khánh | Client ↔ server thật, log ACK/sự kiện/heartbeat; sửa lỗi rồi tích hợp |
| 3. Làm song song | Nhật làm BUS/DB; Thành làm UI; Khánh làm phiên/handler/phòng; Trọng làm luật/Engine | Mỗi phần chạy độc lập bằng hợp đồng/mẫu đã chốt |
| 4. Ghép BUS → handler | Nhật + Khánh | BUS chạy với MySQL được gọi từ server; đăng nhập gắn phiên thật |
| 5. Ghép UI tài khoản | Thành + Khánh + Nhật | UI → socket → BUS → DB → phản hồi; nghiệm thu M2 |
| 6. Ghép phòng | Khánh + Thành, Trọng cung cấp điều kiện G01 | Đăng nhập thật → lobby/phòng/sẵn sàng; nghiệm thu M3.1 |
| 7. Ghép chat | Khánh + Thành | Hai phòng chat độc lập, dọn người ngắt kết nối; nghiệm thu M3.2 |

Các bước là thứ tự phụ thuộc khi nghiệm thu, không phải yêu cầu mọi người ngồi chờ. Khánh có thể test phòng với UserDTO mẫu, nhưng nghiệm thu M3 phải dùng phiên đăng nhập thật từ M2.

Khi làm trực tiếp trên main:

1. Trước khi sửa, xem `git status`; cập nhật main khi thay đổi cục bộ đã được giữ an toàn. Không reset/xóa công việc để pull cho nhanh.
2. Thống nhất người đang sửa các file chung như PacketIO, enum, POM và Protocol.md. Nhật báo trước thay đổi dependency; Khánh tổng hợp thay đổi giao thức.
3. Commit từng phần build được, tên nêu rõ chức năng. Trước push, fetch và tích hợp thay đổi mới; nếu push bị từ chối thì xử lý chênh lệch, không force push main.
4. Chạy lại build sau ghép; thử luồng bị ảnh hưởng. Ghi mã commit và kết quả để người nhận tái hiện được.
5. Không commit target, .class, dữ liệu local-dev, mật khẩu DB hoặc cấu hình cá nhân. Chỉ commit cấu hình mẫu và mã nguồn/tài liệu cần thiết.

## 7. Chạy nền hiện tại

Mở PowerShell ở thư mục ngoài cùng `bacay-online`, dùng JDK 21. M1 hiện chưa cần DB.

### Build toàn bộ

```powershell
.\server\mvnw.cmd -f .\pom.xml clean package
```

Lệnh này xóa kết quả build cũ, biên dịch và đóng gói; không khởi động server/client. `shared` được dùng như thư viện, không chạy một terminal riêng.

### Terminal 1: server thật

```powershell
java -cp "server\target\classes;shared\target\classes" com.game3cay.server.ServerApplication
```

Mong đợi: `TCP server đang nghe cổng 8888`. Tắt MockServer cũ nếu đang chiếm cổng. Có thể truyền cổng khác làm tham số cuối, ví dụ `8889`.

### Terminal 2 và các terminal khác: client

```powershell
java -cp "client\target\classes;shared\target\classes" com.game3cay.RunClient
```

Máy chạy server nhập `localhost:8888`; máy khác cùng LAN nhập IP máy server và cổng 8888, với firewall cho phép. Hai client kết nối tới server trung gian; chỉ cùng cổng chưa đủ nếu sai IP. Server nền hiện chưa chuyển chat giữa client.

Sau khi Nhật thêm driver JDBC, các lệnh classpath trên có thể chưa đủ cho M2 vì chỉ chứa classes nội bộ. Nhật/Khánh phải bàn giao lệnh chạy có dependency ngoài hoặc cấu hình đóng gói phù hợp. Không coi các JAR hiện tại là JAR chạy độc lập bằng `java -jar` khi chưa cấu hình.

## 8. Checklist nghiệm thu

### M1 — Thành và Khánh

- [ ] Main build thành công trên Java 21; ghi commit kiểm tra.
- [ ] Ít nhất hai client kết nối server thật đồng thời, nhận SERVER_NOTICE.
- [ ] Mỗi client gửi nhiều ECHO_REQUEST, nhận ACK đúng requestId.
- [ ] Để yên ít nhất 30 giây, client vẫn kết nối; server gửi PING, client tự trả PONG.
- [ ] Đóng một client không làm server/client còn lại dừng.
- [ ] Tắt server: client báo mất kết nối, UI không treo.
- [ ] Thử client không trả heartbeat: server dọn kết nối; có log/bằng chứng kiểm tra.

### M2 — Thành, Khánh và Nhật

- [ ] Đăng ký từ Swing tạo bản ghi thật trong MySQL; mật khẩu được hash.
- [ ] Đăng nhập đúng thành công; sai thông tin, trùng username và tài khoản khóa trả lỗi đúng.
- [ ] Hai client đăng nhập cùng tài khoản: chỉ một phiên hoạt động.
- [ ] Chưa đăng nhập thì không được dùng chức năng phòng/chat.
- [ ] Logout hoặc ngắt kết nối giải phóng phiên; có thể đăng nhập lại theo luồng đã chốt.
- [ ] DB lỗi có phản hồi kiểm soát, không làm toàn server dừng hoặc UI treo.

### M3.1 — Thành và Khánh

- [ ] Hai tài khoản tạo/vào phòng và thấy cùng danh sách thành viên, chủ phòng, ready.
- [ ] Rời phòng cập nhật cả người rời và người ở lại; phòng trống bị xóa.
- [ ] Một người không ở hai phòng; không vào phòng đầy hoặc không WAITING.
- [ ] Hai client tranh chỗ cuối chỉ một người thành công.
- [ ] Chủ phòng rời được chuyển quyền theo quy ước; ngắt kết nối không để lại người ảo.
- [ ] Phòng khác không bị cập nhật nhầm; snapshot cũ không ghi đè snapshot mới.
- [ ] Điều kiện bắt đầu được chốt/kiểm tra; chưa có Engine thì không coi là đã chạy game.

### M3.2 — Thành và Khánh

- [ ] Ít nhất bốn client chia hai phòng: chat chỉ xuất hiện đúng phòng, gồm tiếng Việt.
- [ ] Người gửi/thời gian do server xác định; client không giả người gửi hoặc tin hệ thống.
- [ ] Giới hạn độ dài/tần suất được kiểm tra; thống nhất phản hồi lỗi và hành vi khi payload sai.
- [ ] Leave/logout/ngắt kết nối không tiếp tục nhận chat của phòng cũ.
- [ ] Chat trong RAM, chưa yêu cầu lưu lịch sử chat dài hạn trong DB.

## 9. Cách báo kết quả để ghép tiếp

Mỗi người cập nhật ngắn gọn theo mẫu:

```text
Người / mốc:
Commit đã push:
Phần đã chạy được:
File hoặc API chung thay đổi:
Cách chạy và dữ liệu thử:
Kết quả mong đợi / kết quả thực tế:
Người cần ghép cùng:
Còn thiếu hoặc đang chờ:
```

Chỉ đánh dấu một mốc hoàn thành khi đã ghép thật và có bằng chứng. Biên dịch được, có interface hoặc có enum chưa đủ để nghiệm thu nghiệp vụ.
