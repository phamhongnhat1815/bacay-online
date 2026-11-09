# Tổng quan mã nguồn Ba Cây Online dành cho nhóm phát triển

Ngày cập nhật: 08/11/2026. Mốc mã nguồn tham chiếu: `943abd6`.

Tài liệu giúp thành viên trong nhóm hiểu dự án làm gì, mỗi phần code chịu trách nhiệm gì và một thao tác của người dùng đi qua những lớp nào. Sau khi đọc, thành viên có thể xác định nơi cần sửa khi nhận nhiệm vụ về giao diện, luật chơi, kết nối, dữ liệu hoặc quản trị.

Ba Cây Online là ứng dụng chơi bài nhiều người qua mạng. Người chơi dùng ứng dụng Java Swing; máy chủ giữ trạng thái bàn, thực thi luật và tính kết quả; MySQL lưu tài khoản, lịch sử và biến động điểm. Ứng dụng quản trị là một chương trình riêng nhưng kết nối vào cùng máy chủ. **Máy chủ quyết định kết quả và số điểm; giao diện gửi yêu cầu và hiển thị dữ liệu máy chủ trả về.**

## 1 Cách đọc tài liệu

- Thành viên mới: đọc mục 2–4 để nắm kiến trúc, sau đó mục 9 để theo dõi các luồng thực tế.
- Làm giao diện người chơi: tập trung mục 6, bảng dữ liệu ở mục 4 và quy tắc đồng bộ ở mục 10.
- Làm máy chủ hoặc cơ sở dữ liệu: tập trung mục 5, 8, 9 và 10.
- Làm quản trị: đọc mục 7 cùng luồng điều chỉnh điểm tại mục 9.
- Khi nhận một yêu cầu sửa cụ thể: tra bảng ở mục 12 rồi mở file liên quan.

Các đường dẫn trong tài liệu tính từ thư mục gốc repository. Trong bảng lớp, đường dẫn thư mục được nêu trước bảng; tên file là liên kết mở trực tiếp mã nguồn khi đọc bản Markdown trong repository.

### Một số thuật ngữ trong code

| Thuật ngữ | Hiểu trong dự án này |
|---|---|
| Module | Một phần dự án Maven có `pom.xml` riêng, ví dụ `client` hoặc `server`. |
| DTO và record | Đối tượng mang dữ liệu giữa các phần, ví dụ thông tin đăng nhập hoặc trạng thái bàn; thường không chứa xử lý nghiệp vụ phức tạp. |
| BUS | Lớp nghiệp vụ, kiểm tra điều kiện và thực hiện chức năng như đăng ký, đổi mật khẩu, lưu kết quả. |
| DAO và JDBC | DAO chứa thao tác dữ liệu tài khoản; JDBC là cách Java kết nối và chạy SQL. Riêng nghiệp vụ ván, `JdbcGameBUS` trực tiếp thực hiện SQL. |
| Packet | Gói thông điệp gửi qua mạng, có loại thông điệp, mã yêu cầu và dữ liệu. |
| Snapshot | Bản chụp trạng thái ở một thời điểm, dùng để giao diện cập nhật theo máy chủ. |
| Push | Máy chủ chủ động gửi sự kiện, ví dụ đổi lượt hoặc có tin nhắn mới. |
| EDT | Luồng sự kiện của Swing, nơi cập nhật giao diện; không thực hiện truy vấn DB hoặc chờ mạng tại đây. |
| Ante và pot | Ante là điểm góp đầu ván; pot là quỹ điểm các người chơi đã góp. Side pot là phần quỹ phụ có nhóm người được tranh riêng. |
| Journal | File kết quả ván đã ghi bền vững trên đĩa, chờ lưu vào DB hoặc dùng để thử lại sau sự cố. |
| Transaction và idempotency | Transaction giúp nhiều lệnh SQL cùng thành công hoặc cùng hoàn tác. Idempotency giúp lưu lại cùng một kết quả mà không cộng trừ điểm lần thứ hai. |

## 2 Kiến trúc tổng thể

### Công nghệ và quan hệ giữa các phần

| Thành phần | Công nghệ và vai trò |
|---|---|
| Ngôn ngữ và build | Java 21, Maven nhiều module, mã nguồn UTF-8. |
| Giao diện | Swing và Java2D cho cả người chơi và quản trị. |
| Kết nối | TCP, mặc định có TLS; giao thức riêng Packet v5 dùng Java serialization trong frame có kiểm tra. |
| Dữ liệu | MySQL 8.0.16 trở lên, JDBC và MySQL Connector/J. |
| Kiểm thử | JUnit 5; H2 cho các ca tích hợp dữ liệu, MySQL thật cho các ca schema và transaction khi được cấu hình. |

```text
client                              admin
Swing và ConnectionController       Swing và AdminController
SocketClient                        AdminSocketClient
        |                                   |
        +----------- TCP và TLS ------------+
                            |
server       ClientHandler và SessionRegistry
                  |                       |
             RoomManager              JdbcUserBUS
                  |                       |
          DefaultGameEngine              UserDAO
                  |                       |
          ResultPersistence               |
             File journal                 |
                  |                       |
             JdbcGameBUS -----------------+
                            |
                          MySQL
```

`shared` cung cấp model, Packet, luật đánh giá bài và tiện ích dùng chung. `server`, `client` và `admin` đều phụ thuộc `shared`. Ba ứng dụng không phụ thuộc mã nguồn của nhau. Client và admin không kết nối trực tiếp MySQL; mọi yêu cầu dữ liệu đều đi qua server.

### Những chức năng đã có

Người chơi có thể đăng ký, đăng nhập, xem và sửa hồ sơ, đổi mật khẩu, tìm bàn, chơi ngay, vào hoặc rời bàn, sẵn sàng hoặc tạm nghỉ, đổi mức cược khi là chủ bàn, đặt cược, nặn bài, chat, xem lịch sử và bảng xếp hạng. Client có cơ chế thử khôi phục kết nối.

Quản trị viên có thể xem thống kê, tìm và khóa hoặc mở khóa người dùng, điều chỉnh điểm có lý do, theo dõi bàn, soi ván đang chạy, reset bàn, gửi thông báo toàn hệ thống và xem sổ cái. Tài khoản ADMIN không được tham gia chơi với tư cách người chơi.

## 3 Bản đồ thư mục dự án

```text
bacay-online/
  pom.xml                       Cấu hình Maven chung và danh sách 4 module
  shared/                       Model, protocol, luật bài và bảo mật dùng chung
  server/                       Máy chủ, bàn, engine, nghiệp vụ và JDBC
  client/                       Ứng dụng Swing của người chơi và asset
  admin/                        Ứng dụng Swing của quản trị viên
  database/setup.sql            Schema cài mới và nâng cấp MySQL
  scripts/                      Script PowerShell và CMD để build, khởi chạy
  docs/                         Tài liệu kiến trúc, protocol, dữ liệu và kiểm thử
  Game_Rules.md                 Luật chơi chi tiết
  README.md                     Hướng dẫn cài đặt và vận hành
  Build.cmd                     Điểm gọi build trên Windows
  Start-Server.cmd               Chạy server từ bản đóng gói dist
  Start-Client.cmd               Chạy ứng dụng người chơi từ dist
  Start-Admin.cmd                Chạy ứng dụng quản trị từ dist
```

Trong mỗi module, `src/main/java` chứa code chạy thật; `src/main/resources` chứa tài nguyên và cấu hình; `src/test/java` chứa kiểm thử. `target/` là đầu ra Maven; `dist/` là bộ ứng dụng được script build đóng gói. Không chỉnh code trong `target/` hoặc `dist/` để triển khai tính năng.

Root [pom.xml](../pom.xml) quản lý Java, encoding, plugin compiler, Surefire và phiên bản dependency dùng chung. POM từng module bổ sung phần riêng, chẳng hạn Connector/J của server hoặc chế độ headless khi test giao diện.

## 4 Module shared

Thư mục cơ sở: `shared/src/main/java/com/game3cay/shared/`.

Đây là nơi các ứng dụng thống nhất hình dạng dữ liệu và ý nghĩa thông điệp. Sửa model hoặc protocol tại đây có thể ảnh hưởng đồng thời server, client và admin.

### Model dùng chung

| Nhóm file trong model | Dùng để làm gì |
|---|---|
| [Card.java](../shared/src/main/java/com/game3cay/shared/model/Card.java), [Rank.java](../shared/src/main/java/com/game3cay/shared/model/Rank.java), [Suit.java](../shared/src/main/java/com/game3cay/shared/model/Suit.java) | Biểu diễn một lá bài, giá trị số và chất. |
| [Hand.java](../shared/src/main/java/com/game3cay/shared/model/Hand.java), [HandType.java](../shared/src/main/java/com/game3cay/shared/model/HandType.java) | Một tay bài đã được đánh giá, gồm các lá, loại bài và điểm. |
| `AuthRequest`, `RegisterRequest`, `AuthResult` | Dữ liệu đăng nhập, đăng ký và kết quả xác thực. |
| `ProfileResult`, `UpdateProfileRequest`, `ChangePasswordRequest` | Dữ liệu hồ sơ và yêu cầu thay đổi tài khoản. |
| [RoomSummary.java](../shared/src/main/java/com/game3cay/shared/model/RoomSummary.java) | Thông tin gọn của một bàn để hiển thị danh sách ở sảnh. |
| [RoomSnapshot.java](../shared/src/main/java/com/game3cay/shared/model/RoomSnapshot.java) | Chi tiết bàn: chủ bàn, ghế, người chơi, trạng thái sẵn sàng, mức cược, phiên bản và đếm ngược. |
| `CreateRoomRequest`, `JoinRoomRequest` | Cấu trúc cấu hình bàn và yêu cầu vào bàn. `CreateRoomRequest` còn được server dùng giữ cấu hình 20 bàn cố định. |
| `GameStartInfo`, `DealCardPayload` | Sự kiện bắt đầu ván và dữ liệu chia bài riêng. |
| [GameStateSnapshot.java](../shared/src/main/java/com/game3cay/shared/model/GameStateSnapshot.java) | Trạng thái công khai của ván: giai đoạn, lượt hiện tại, người còn trong ván và phiên bản. |
| [BetStateSnapshot.java](../shared/src/main/java/com/game3cay/shared/model/BetStateSnapshot.java) | Mức cược cao nhất, tổng pot, pot phụ và phần đóng góp công khai từng người. |
| [PlayerStateSnapshot.java](../shared/src/main/java/com/game3cay/shared/model/PlayerStateSnapshot.java) | Dữ liệu riêng của một người chơi: bài, số dư trong ván, tổng cược, đã úp hay tất tay. |
| `GameActionRequest`, `BetAction`, `BetRecord` | Yêu cầu hành động, danh mục hành động và lịch sử cược. |
| `RoundResult`, `PlayerResult`, `SidePot`, `PlayAgainPrompt` | Kết quả ván, lãi lỗ từng người, các pot và thông báo chuẩn bị ván tiếp theo. |
| `ChatMessage`, `RoomChatEvent` | Nội dung chat và bàn nhận tin; sự kiện có `roomId = 0` được dùng cho thông báo toàn hệ thống. |
| `PageRequest`, `PageResult`, `GameHistoryItem`, `LeaderboardItem` | Phân trang và từng dòng dữ liệu lịch sử hoặc xếp hạng. |
| Các file `Admin*` | Bộ lọc người dùng, thống kê, điều chỉnh điểm, reset bàn, sổ cái và dữ liệu soi bài dành cho quản trị. |

Các snapshot không thay thế cho nhau. Ví dụ, vẽ người đang đến lượt dùng `GameStateSnapshot`; vẽ quỹ cược dùng `BetStateSnapshot`; xem ba lá của mình dùng `PlayerStateSnapshot`. Không đưa bài riêng vào snapshot công khai để tiện hiển thị.

### Luật bài và kết nối dùng chung

| File | Trách nhiệm |
|---|---|
| [game/HandEvaluator.java](../shared/src/main/java/com/game3cay/shared/game/HandEvaluator.java) | `evaluate()` phân loại ba lá; `compare()` so hai tay bài. Server dùng để xác định thắng thua, UI dùng mô tả bài. |
| [network/Packet.java](../shared/src/main/java/com/game3cay/shared/network/Packet.java) | Đóng gói version, type, requestId và data; có hàm tạo request, push, ACK và ERROR. |
| [network/PacketType.java](../shared/src/main/java/com/game3cay/shared/network/PacketType.java) | Danh mục loại thông điệp, ví dụ `LOGIN`, `GAME_ACTION`, `ROUND_RESULT`. |
| [network/PacketIO.java](../shared/src/main/java/com/game3cay/shared/network/PacketIO.java) | Đọc và ghi frame, kiểm kích thước, lọc object được giải mã và kiểm tính hợp lệ payload. |
| [network/ErrorCode.java](../shared/src/main/java/com/game3cay/shared/network/ErrorCode.java) | Mã lỗi chung để UI hiểu lý do yêu cầu bị từ chối. |
| [network/TlsTransport.java](../shared/src/main/java/com/game3cay/shared/network/TlsTransport.java) | Tạo kết nối phía client/admin và xác minh chứng chỉ theo hostname hoặc IP. |
| [network/RuntimeSettings.java](../shared/src/main/java/com/game3cay/shared/network/RuntimeSettings.java) | Đọc resource properties và giá trị ghi đè bằng Java system property. |
| [security/PasswordUtil.java](../shared/src/main/java/com/game3cay/shared/security/PasswordUtil.java) | Tạo hash và kiểm mật khẩu bằng PBKDF2WithHmacSHA256, salt riêng và 310.000 vòng khi tạo hash mới. |

Luật hiện tại dùng 36 lá A–9, xếp Sáp cao hơn mọi bài điểm, điểm 0 sau phép chia dư được tính thành 10. Khi bằng cấp bài, so chất Rô > Cơ > Chuồn > Bích; trong cùng chất, Át rô lớn nhất, còn Át cơ, chuồn và bích nhỏ hơn 2. Sáp Át vẫn là sáp lớn nhất. 10/J/Q/K và enum `LIENG`, `BO_DOI` còn được giữ để đọc dữ liệu cũ; sự tồn tại của chúng không có nghĩa ván mới sử dụng các luật này.

## 5 Module server

Thư mục cơ sở: `server/src/main/java/com/game3cay/server/`.

### Khởi động và lớp mạng

| File | Trách nhiệm và thời điểm được dùng |
|---|---|
| [ServerApplication.java](../server/src/main/java/com/game3cay/server/ServerApplication.java) | Hàm `main` của server. Thiết lập log, đọc cấu hình, kiểm schema, bootstrap admin, tạo journal worker và các dịch vụ, mở cổng TLS, nhận kết nối và chạy đồng hồ bàn mỗi giây. Khi dừng, đóng các dịch vụ và worker. |
| [network/ClientHandler.java](../server/src/main/java/com/game3cay/server/network/ClientHandler.java) | Xử lý một kết nối của người chơi hoặc admin. Đọc Packet, kiểm phiên và quyền, phân nhánh theo `PacketType`, gọi room/BUS và gửi phản hồi. Có sender queue, heartbeat và cache phản hồi request gần nhất. |
| [network/SessionRegistry.java](../server/src/main/java/com/game3cay/server/network/SessionRegistry.java) | Ánh xạ userId tới kết nối đang đăng nhập, ngăn một tài khoản đăng nhập đồng thời hai kết nối; gửi tới một nhóm hoặc toàn bộ phiên. |
| [network/AuthenticationLimiter.java](../server/src/main/java/com/game3cay/server/network/AuthenticationLimiter.java) | Giới hạn tốc độ xác thực theo địa chỉ và tài khoản trước thao tác hash tốn tài nguyên. |
| [network/ServerTls.java](../server/src/main/java/com/game3cay/server/network/ServerTls.java) | Chuẩn bị keystore, chứng chỉ và server socket TLS. |

`ClientHandler` là đầu mối tìm chức năng mạng. Ví dụ, muốn hiểu nút Theo bài, tìm nhánh `GAME_ACTION`; muốn hiểu khóa người dùng, tìm `ADMIN_LOCK_USER_REQ`. Lớp này chuyển yêu cầu sang phần nghiệp vụ, còn luật cược nằm trong engine.

### Quản lý bàn

| File | Trách nhiệm |
|---|---|
| [room/RoomService.java](../server/src/main/java/com/game3cay/server/room/RoomService.java) | Giao diện cho các thao tác phòng cơ bản như liệt kê, vào/rời, sẵn sàng và chat. |
| [room/RoomManager.java](../server/src/main/java/com/game3cay/server/room/RoomManager.java) | Quản lý 20 bàn cố định, ghế, chủ bàn, membership, sẵn sàng, đếm ngược, ván đang chơi và phối hợp kết toán. Đây là lớp nối lớp mạng với engine và lưu kết quả. |
| [room/RoomEvents.java](../server/src/main/java/com/game3cay/server/room/RoomEvents.java) | Callback phát thay đổi bàn, sảnh, chat và Packet. `ServerApplication` nối callback này với `SessionRegistry` để chuyển tới đúng kết nối. |

Các hàm nên đọc trong `RoomManager`:

- `join()` và `leave()`: kiểm điều kiện vào, cấp ghế, chuyển chủ bàn khi cần; rời giữa ván được chuyển thành úp bài.
- `setReady()` và `changeBet()`: cập nhật sẵn sàng, kiểm số dư và hủy xác nhận khi mức cược thay đổi.
- `start()`: dành quyền sử dụng tài khoản, tải hồ sơ mới, kiểm lại phiên bản bàn, tạo roundId và gọi engine chia bài.
- `action()`: kiểm roundId rồi chuyển hành động vào engine.
- `publishGameUpdate()`: phát snapshot công khai và riêng; khi kết thúc, ghi journal trước khi phát `ROUND_RESULT` và dọn engine.
- `tick()`: xử lý đếm ngược tự bắt đầu, thời gian hiển thị kết quả và tự úp khi hết giờ hành động.
- `adjustBalanceForAdmin()`, `forceResetRoom()`, `getAdminRoomLiveDetail()`: phối hợp các thao tác quản trị có liên quan tới bàn hoặc số dư.

Bàn có chu kỳ `WAITING → PLAYING → RESULT → WAITING`. Khi chờ, bàn đủ người đủ điều kiện và sẵn sàng sẽ đếm ngược 8 giây; chủ bàn có thể bắt đầu ngay khi hợp lệ. Kết quả hiển thị 6 giây. Sau kết quả, server loại người AFK hoặc không đủ cược, đặt sẵn sàng cho người còn lại và xét đếm ngược; nút Tạm nghỉ cho phép người chơi hủy sẵn sàng trong giai đoạn chờ.

### Engine thực thi ván

| File | Trách nhiệm |
|---|---|
| [game/GameEngine.java](../server/src/main/java/com/game3cay/server/game/GameEngine.java) | Hợp đồng thao tác ván: khởi tạo, lấy snapshot, xử lý cược/rời/hủy, lấy kết quả và cleanup. |
| [game/DefaultGameEngine.java](../server/src/main/java/com/game3cay/server/game/DefaultGameEngine.java) | Tạo bộ bài 36 lá, xáo bằng nguồn ngẫu nhiên, chia không trùng, trừ ante trong RAM, quản lý lượt và cược, tính pot và lãi lỗ. |
| [game/RoundParticipant.java](../server/src/main/java/com/game3cay/server/game/RoundParticipant.java) | Danh tính và số dư ban đầu của người được đưa vào ván. |
| [game/RoundContext.java](../server/src/main/java/com/game3cay/server/game/RoundContext.java) | Record mô tả ngữ cảnh ván và kiểm hợp lệ danh sách người chơi, bàn, mức cược; luồng khởi tạo engine hiện nhận các tham số trực tiếp qua `initRound()`. |
| [game/ActionResult.java](../server/src/main/java/com/game3cay/server/game/ActionResult.java) | Kết quả xử lý một hành động, kèm snapshot và thông tin ván đã kết thúc hay chưa. |
| [game/CompletedRound.java](../server/src/main/java/com/game3cay/server/game/CompletedRound.java) | Hồ sơ để lưu: kết quả, bàn, ante và toàn bộ bài riêng phục vụ lịch sử, kiểm toán. Dữ liệu này không được broadcast nguyên khối cho người chơi. |
| [game/ResultPersistence.java](../server/src/main/java/com/game3cay/server/game/ResultPersistence.java) | Ghi và phục hồi journal; worker gọi BUS lưu DB và thử lại khi lỗi. |
| [game/GameEngineException.java](../server/src/main/java/com/game3cay/server/game/GameEngineException.java) | Lỗi luật hoặc trạng thái ván có ErrorCode để lớp điều phối trả về client. |
| [game/HandEvaluator.java](../server/src/main/java/com/game3cay/server/game/HandEvaluator.java) | Lớp kế thừa evaluator ở shared để giữ tương thích tên cũ. Luật chính nằm trong shared. |

Trong `DefaultGameEngine`, `initRound()` khởi tạo; `handleAction()` xử lý cược; `advanceTurn()` chuyển lượt; `computeSidePots()` chia quỹ theo các mức đóng góp; `calculateWinnings()` xác định phần được nhận; `buildPlayerResults()` tạo lãi lỗ và dữ liệu được công khai. Engine không thao tác socket hoặc ghi SQL.

### Nghiệp vụ và truy cập dữ liệu

| File | Trách nhiệm |
|---|---|
| [bus/UserBUS.java](../server/src/main/java/com/game3cay/server/bus/UserBUS.java), [bus/JdbcUserBUS.java](../server/src/main/java/com/game3cay/server/bus/JdbcUserBUS.java) | Hợp đồng và triển khai nghiệp vụ tài khoản: validation, đăng ký, xác thực, hồ sơ, mật khẩu, tìm người dùng, thống kê và điều chỉnh điểm. |
| [bus/UserDTO.java](../server/src/main/java/com/game3cay/server/bus/UserDTO.java) | Hồ sơ tài khoản dùng bên trong server, gồm role, trạng thái, số dư và thống kê. |
| [bus/GameBUS.java](../server/src/main/java/com/game3cay/server/bus/GameBUS.java), [bus/JdbcGameBUS.java](../server/src/main/java/com/game3cay/server/bus/JdbcGameBUS.java) | Lưu trọn kết quả ván bằng transaction, kiểm lưu trùng, truy vấn lịch sử và xếp hạng. `checkSchema()` kiểm dữ liệu cần thiết khi khởi động. |
| [bus/BUSException.java](../server/src/main/java/com/game3cay/server/bus/BUSException.java) | Biểu diễn lỗi nghiệp vụ và mã lỗi mà `ClientHandler` có thể trả về. |
| [dal/UserDAO.java](../server/src/main/java/com/game3cay/server/dal/UserDAO.java) | SQL tài khoản, hồ sơ, trạng thái, thống kê và sổ cái; điều chỉnh số dư dùng khóa DB và transaction. |
| [dal/ConnectionProvider.java](../server/src/main/java/com/game3cay/server/dal/ConnectionProvider.java) | Abstraction mở và đóng dịch vụ kết nối, giúp thay nguồn DB trong kiểm thử. |
| [dal/DatabaseConfig.java](../server/src/main/java/com/game3cay/server/dal/DatabaseConfig.java), [dal/JdbcConnectionProvider.java](../server/src/main/java/com/game3cay/server/dal/JdbcConnectionProvider.java) | Đọc cấu hình môi trường và mở kết nối JDBC có giới hạn đồng thời, mặc định 12. Mỗi lần mở tạo kết nối vật lý mới; đóng thì trả lượt, không giữ kết nối nhàn rỗi để tái sử dụng. |
| [dal/AdminBootstrap.java](../server/src/main/java/com/game3cay/server/dal/AdminBootstrap.java) | Khởi tạo thông tin admin riêng cho bản cài và xử lý seed mật khẩu công khai cũ trước khi server nhận kết nối. |

## 6 Module client

Thư mục cơ sở: `client/src/main/java/com/game3cay/`.

### Điều phối và kết nối

| File | Trách nhiệm |
|---|---|
| [RunClient.java](../client/src/main/java/com/game3cay/RunClient.java) | Điểm chạy người chơi. Nhận host/port nếu có, preload ảnh, khởi tạo theme, cửa sổ và controller. |
| [controller/ConnectionController.java](../client/src/main/java/com/game3cay/controller/ConnectionController.java) | Gắn sự kiện UI với request, theo dõi phiên và bàn, ghép phản hồi theo requestId, xử lý timeout, push, đổi số dư và reconnect. |
| [controller/ReconnectState.java](../client/src/main/java/com/game3cay/controller/ReconnectState.java) | Trạng thái và giới hạn lượt thử lại; phân biệt lỗi được retry với lỗi buộc quay lại đăng nhập. |
| [network/SocketClient.java](../client/src/main/java/com/game3cay/network/SocketClient.java) | Kết nối TLS, hàng đợi gửi, luồng đọc và watchdog kiểm tra máy chủ còn phản hồi. |
| [network/PacketListener.java](../client/src/main/java/com/game3cay/network/PacketListener.java) | Callback kết nối thành công, nhận Packet và mất kết nối từ lớp mạng sang controller. |

Controller hiện theo dõi một yêu cầu nghiệp vụ đang chờ và khóa các thao tác liên quan khi cần. Callback mạng được đưa về EDT, đồng thời kiểm đúng instance kết nối để bỏ sự kiện từ socket cũ.

### Các màn hình và thành phần giao diện

| File trong gui | Nội dung và chức năng |
|---|---|
| [ClientView.java](../client/src/main/java/com/game3cay/gui/ClientView.java), [MainFrame.java](../client/src/main/java/com/game3cay/gui/MainFrame.java) | Hợp đồng giao diện cho controller và cửa sổ chính. MainFrame dùng CardLayout chuyển đăng nhập, sảnh và bàn; cung cấp thao tác hồ sơ, mật khẩu và menu phiên. |
| [AuthPanel.java](../client/src/main/java/com/game3cay/gui/AuthPanel.java) | Form đăng nhập/đăng ký, nhập thông tin và cấu hình máy chủ. |
| [SessionHeader.java](../client/src/main/java/com/game3cay/gui/SessionHeader.java) | Thanh đầu có tài khoản, số dư, lịch sử, xếp hạng và menu. |
| [LobbyPanel.java](../client/src/main/java/com/game3cay/gui/LobbyPanel.java) | Danh sách bàn, tìm số bàn, bộ lọc và Chơi ngay. Bộ lọc là xử lý hiển thị cục bộ trên danh sách đã tải. |
| [RoomPanel.java](../client/src/main/java/com/game3cay/gui/RoomPanel.java) | Ghép vùng bàn chơi với chat và các thao tác vào/rời, sẵn sàng, bắt đầu, đổi cược. |
| [GamePanel.java](../client/src/main/java/com/game3cay/gui/GamePanel.java) | Nhận các snapshot, quản lý các nút cược, trạng thái kết quả, thanh chuẩn bị và thao tác xem bài riêng. |
| [GameTablePanel.java](../client/src/main/java/com/game3cay/gui/GameTablePanel.java) | Vẽ bàn, vị trí người chơi, lá bài, lượt, cược và thông tin kết quả bằng Java2D. |
| [HandRevealState.java](../client/src/main/java/com/game3cay/gui/HandRevealState.java) | Lưu lá nào đã mở và trạng thái ẩn bài trong một ván; reset khi sang ván khác. Đây là trạng thái xem cục bộ. |
| [CardSqueezeDialog.java](../client/src/main/java/com/game3cay/gui/CardSqueezeDialog.java), [CardSqueezePanel.java](../client/src/main/java/com/game3cay/gui/CardSqueezePanel.java) | Hộp và vùng tương tác nặn bài bằng kéo chuột. Đóng hộp vẫn giữ tiến độ; đồng hồ lượt tiếp tục chạy. |
| [DataDialog.java](../client/src/main/java/com/game3cay/gui/DataDialog.java), [PlainTable.java](../client/src/main/java/com/game3cay/gui/PlainTable.java) | Bảng dữ liệu phân trang lịch sử/xếp hạng và cách hiển thị văn bản thuần. |
| [Theme.java](../client/src/main/java/com/game3cay/gui/Theme.java) | Màu, font, kích thước và component giao diện dùng chung. |
| [Assets.java](../client/src/main/java/com/game3cay/gui/Assets.java) | Tải, thu nhỏ và cache ảnh, xử lý tỷ lệ màn hình và HiDPI. |
| [PlayingCardArt.java](../client/src/main/java/com/game3cay/gui/PlayingCardArt.java), [GameIcons.java](../client/src/main/java/com/game3cay/gui/GameIcons.java) | Cách vẽ lá bài, viền, bóng và icon bằng Java2D. |
| [Sound.java](../client/src/main/java/com/game3cay/gui/Sound.java) | Phát âm thanh cho các sự kiện UI và game, cho phép bật/tắt. |

Ảnh và âm thanh nằm trong `client/src/main/resources/assets/`: bài, avatar, nền, khung, nút, icon và sounds. Khi thay asset, kiểm cả đường dẫn mà `Assets` sử dụng và chất lượng khi phóng to; không chỉ thay tên file ảnh.

Ví dụ phân biệt trách nhiệm: đổi vị trí nút Tố đến thì sửa UI; đổi cách đóng gói giá trị tố thì xem `GameActionRequest` và controller; đổi tính hợp lệ của mức tố thì sửa engine cùng kiểm thử. Chỉ sửa điều kiện bật nút trên client sẽ không thay luật máy chủ.

## 7 Module admin

Thư mục cơ sở: `admin/src/main/java/com/game3cay/admin/`.

| File | Trách nhiệm |
|---|---|
| [RunAdmin.java](../admin/src/main/java/com/game3cay/admin/RunAdmin.java) | Điểm chạy ứng dụng quản trị, tạo cửa sổ và controller trên EDT. |
| [controller/AdminController.java](../admin/src/main/java/com/game3cay/admin/controller/AdminController.java) | Đăng nhập ADMIN, gửi yêu cầu, nạp từng màn hình, thăm dò dữ liệu và xử lý thao tác quản trị. |
| [controller/AdminRequests.java](../admin/src/main/java/com/game3cay/admin/controller/AdminRequests.java) | Gắn phản hồi với đúng phiên đăng nhập và lần đọc mới nhất; tránh kết quả tìm kiếm cũ ghi đè màn hình mới. |
| [controller/RoomInspectorRequests.java](../admin/src/main/java/com/game3cay/admin/controller/RoomInspectorRequests.java) | Bỏ phản hồi soi bàn cũ khi đã đổi bàn hoặc đóng/mở hộp soi khác. |
| [network/AdminSocketClient.java](../admin/src/main/java/com/game3cay/admin/network/AdminSocketClient.java) | Gửi request bất đồng bộ bằng CompletableFuture, ghép requestId, xử lý TLS và PING/PONG; request quá hạn 10 giây sẽ đóng phiên. |
| `gui/AdminView`, `gui/AdminMainFrame`, `gui/AdminAuthPanel` | Hợp đồng giao diện, cửa sổ quản trị và form đăng nhập. |
| `gui/DashboardPanel` | Thống kê và gửi thông báo hệ thống. |
| `gui/UsersPanel` | Tìm/lọc/phân trang người dùng, khóa/mở khóa và điều chỉnh điểm. |
| `gui/RoomsPanel` | Theo dõi các bàn và yêu cầu reset hoặc soi bàn. |
| `gui/LiveRoomInspectorDialog` | Chi tiết ván đang chạy, người chơi và bài dành cho ADMIN. |
| `gui/LedgerPanel` | Sổ cái giao dịch game và điều chỉnh quản trị, kèm người thực hiện và lý do. |
| `gui/AdminTheme` | Kiểu hiển thị dùng chung cho ứng dụng quản trị. |

Mọi thao tác quản trị vẫn phải được server kiểm quyền trong `ClientHandler`. Việc màn hình chỉ cho ADMIN đăng nhập không thay thế kiểm quyền phía máy chủ. Dữ liệu soi bài là dữ liệu đặc quyền, không được tái sử dụng làm snapshot công khai.

## 8 Cơ sở dữ liệu và nơi lưu trạng thái

Nguồn schema là [database/setup.sql](../database/setup.sql); bản [server/src/main/resources/schema.sql](../server/src/main/resources/schema.sql) đi kèm tài nguyên server phải giữ đồng bộ. Script build kiểm hai bản giống nhau trước khi đóng gói.

| Bảng | Dữ liệu và mục đích |
|---|---|
| `users` | Tài khoản, hash mật khẩu, hồ sơ, role, trạng thái, số dư và thống kê. Người đăng ký mới có mặc định 1.000 điểm. |
| `app_migrations` | Ghi nhận migration đã áp dụng để server kiểm phiên bản schema. |
| `play_rounds` | Một dòng cho mỗi ván: roundId, hash nội dung, bàn, ante, trạng thái và thời điểm kết thúc. |
| `play_results` | Kết quả từng người trong ván: thứ hạng, thắng/thua/úp/hủy, profit, loại bài và điểm. |
| `play_cards` | Ba lá của từng người để truy vấn lịch sử và kiểm toán; có ràng buộc không trùng lá trong cùng ván. |
| `play_bets` | Chuỗi hành động cược, tổng cược và số điểm thêm vào mỗi lần. |
| `play_pots` và `play_pot_players` | Các tầng pot và danh sách người có quyền tranh từng tầng. |
| `play_ledger` | Biến động điểm khi chốt ván FINISHED, có số dư trước và sau. |
| `admin_adjustments` | Điều chỉnh điểm bởi admin, tài khoản đích, delta, lý do, số dư trước/sau và thời gian. |

Quan hệ chính: một `play_rounds` có nhiều `play_results`; mỗi kết quả liên kết bài và các hành động của người đó. `roundId` nhận diện một ván trên toàn hệ thống; số bàn chỉ nhận diện bàn được tái sử dụng qua nhiều ván.

| Trạng thái | Nơi giữ và thời gian tồn tại |
|---|---|
| Phiên online | `SessionRegistry` trong RAM; không phục hồi phiên socket sau khi server dừng. |
| Người đang ở bàn, ready, lượt và cược dở | `RoomManager` và engine trong RAM. Không lưu từng thay đổi này xuống MySQL. |
| Cách người chơi đang mở/ẩn bài | `HandRevealState` trên client. |
| Ván đã có kết quả chờ DB | File `.round` trong thư mục journal và bộ pending của `ResultPersistence`. |
| Tài khoản và lịch sử đã chốt | MySQL. |

Journal bảo vệ **kết quả đã được ghi journal thành công**. Cơ chế này không phải lưu toàn bộ ván đang cược để tiếp tục nguyên trạng sau khi tiến trình server dừng.

## 9 Các luồng xử lý quan trọng

### Đăng ký và đăng nhập

1. `AuthPanel` nhận thông tin; `ConnectionController` kiểm tra đầu vào cơ bản và kết nối qua `SocketClient`.
2. Client gửi `REGISTER` hoặc `LOGIN` với DTO tương ứng; `PacketIO` phía server đọc và kiểm Packet.
3. `ClientHandler` giới hạn tốc độ xác thực rồi gọi `JdbcUserBUS`.
4. Đăng ký: BUS kiểm username/email/mật khẩu, hash mật khẩu rồi `UserDAO` tạo tài khoản. Đăng nhập: BUS đọc tài khoản, kiểm hash và trạng thái.
5. Với `LOGIN`, `SessionRegistry.bind()` giữ phiên cho userId; server kiểm lại trạng thái tài khoản sau khi gắn phiên rồi trả `AUTH_RESULT`.
6. Sau đăng ký thành công, controller gửi tiếp `LOGIN`; nhận hồ sơ xác thực xong mới đưa người chơi vào sảnh và tải danh sách bàn.

### Vào bàn và bắt đầu ván

1. Người chơi chọn bàn; controller gửi `JOIN_ROOM` và roomId.
2. `ClientHandler` lấy hồ sơ mới và gọi `RoomManager.join()`; manager kiểm bàn, ghế, membership và kết quả đang chờ chốt.
3. Người vào bàn trống trở thành chủ bàn. Ở bàn WAITING, người đủ số dư được đặt sẵn sàng khi vào; thiếu số dư thì không được đưa vào danh sách tham gia ván.
4. `ROOM_UPDATED` cập nhật ghế và trạng thái. Đủ điều kiện thì server bắt đầu đếm ngược; đổi cược làm mọi người phải xác nhận sẵn sàng lại.
5. `start()` tải lại hồ sơ/số dư ngoài khóa trạng thái, kiểm bàn chưa đổi rồi gọi `initRound()`.
6. Engine tạo bộ bài, xáo, chia ba lá mỗi người và góp ante trong RAM. Máy chủ gửi `START_GAME`, bài riêng và snapshot tới đúng đối tượng nhận.

### Thực hiện một lượt cược

Ví dụ A đã góp 10 điểm và chọn Tố đến 50. Giá trị gửi đi là **tổng cược 50**, engine trừ thêm 40 nếu hợp lệ.

1. `GamePanel` tạo `GameActionRequest(roundId, action, raiseTo)`; controller gửi `GAME_ACTION`.
2. `RoomManager.action()` kiểm người chơi thuộc bàn và roundId khớp ván hiện tại.
3. Engine kiểm đúng lượt, loại hành động, mức cược và số dư. Tố hợp lệ sẽ yêu cầu những người còn khả năng hành động phản hồi mức cược mới.
4. Máy chủ phát `GAME_UPDATED`, `BET_UPDATED` và `PLAYER_STATE` riêng. UI cập nhật theo snapshot; `ACK` chỉ xác nhận yêu cầu đã xử lý.
5. Khi chỉ còn một người chưa úp hoặc không còn lượt cần xử lý, engine tính kết quả. Quá thời gian lượt mặc định 30 giây, `tick()` thực hiện FOLD.

Ở protocol hiện tại không có `BetAction.CHECK` riêng. Nút Kiểm tra gửi `CALL` khi khoản cần theo bằng 0; thao tác này không trừ thêm điểm. `CALL` khi thiếu điểm chỉ góp phần còn lại và trạng thái người chơi trở thành all-in.

### Tính pot và kết toán

Giả sử A góp tổng 30 điểm, B và C mỗi người góp 100, cả ba chưa úp. Pot đầu là 90 điểm, cả ba được tranh; pot phụ là 140 điểm, chỉ B và C được tranh. Một người có thể thắng pot đầu nhưng không có quyền nhận pot phụ.

1. Engine chia các tầng pot, so bài người đủ quyền và phân phối điểm; `profit = điểm nhận từ pot − tổng điểm đã góp`.
2. `RoomManager` tạo `CompletedRound` gồm cả bài riêng để lưu, đánh dấu đang journal rồi nhả khóa trạng thái.
3. `ResultPersistence.enqueue()` ghi `.tmp`, đồng bộ dữ liệu xuống đĩa rồi đổi thành `.round`, ưu tiên đổi tên nguyên tử khi hệ thống hỗ trợ.
4. Sau khi journal thành công, manager phát `ROUND_RESULT`, cập nhật số dư trong bàn, dọn engine và chuyển sang RESULT. Lỗi journal giữ lại kết quả và reservation để thử lại, chưa phát kết quả chính thức.
5. Worker gọi `JdbcGameBUS.saveCompleted()`: khóa user theo thứ tự ID, kiểm roundId và fingerprint, ghi lịch sử/ledger, cập nhật số dư và thống kê trong cùng transaction.
6. Chốt thành công thì xóa file journal và pending. DB lỗi thì giữ journal, thử lại mỗi 3 giây; người có kết quả pending chưa được bắt đầu ván mới.

Nếu tiến trình dừng sau commit nhưng trước khi xóa journal, lần khởi động tiếp theo đọc lại file. Cùng roundId và cùng nội dung được nhận diện là đã lưu, không cộng trừ lần nữa. Cùng roundId nhưng nội dung khác bị từ chối.

### Mất kết nối và rời bàn

`SocketClient` phát hiện gián đoạn; controller thử khôi phục tối đa 5 lần và có thể đăng nhập, xin vào lại bàn đã nhớ. Server ghi nhớ bàn gần nhất trong khoảng 15 giây để hỗ trợ vào lại trong một số trạng thái.

Tuy nhiên, khi server xử lý rời/mất kết nối giữa ván, người đó đã bị FOLD và cược vẫn nằm trong pot. Vào lại không khôi phục quyền cược đã bỏ; snapshot trả về quyết định trạng thái được hiển thị. Client bỏ snapshot cũ theo phiên bản, bỏ callback socket cũ và xóa trạng thái ván khi bàn về WAITING, kể cả khi đã lỡ mất push kết quả.

### Quản trị điều chỉnh điểm hoặc reset bàn

Điều chỉnh điểm đi theo `UsersPanel → AdminController → ADMIN_ADJUST_BALANCE_REQ → ClientHandler → RoomManager → JdbcUserBUS → UserDAO`. Manager chặn tài khoản đang chơi, đang bắt đầu hoặc đang chờ chốt. DAO khóa tài khoản, kiểm không âm, cập nhật và ghi lý do trong cùng transaction. Máy chủ gửi hồ sơ mới riêng cho người bị điều chỉnh để UI cập nhật số dư.

Reset bàn đi theo `ADMIN_FORCE_RESET_ROOM → RoomManager.forceResetRoom()`. Ván đang chạy được hủy và hoàn cược qua luồng kết quả có journal trước khi dọn bàn. Không bỏ qua engine và journal bằng cách chỉ xóa danh sách người chơi.

## 10 Quy tắc cần giữ khi sửa code

### Giao thức và quyền truy cập

Packet v5 có header 8 byte gồm magic, version, type và độ dài; payload tối đa 1 MiB. `PacketIO` kiểm header trước khi giải mã object, có whitelist và giới hạn độ sâu/kích thước. Không đọc object tùy ý trực tiếp từ socket để bỏ qua lớp này.

Request có requestId; reply giữ cùng id; push nghiệp vụ dùng id null. PING/PONG dùng id riêng để ghép heartbeat. Cache 128 phản hồi gần nhất trên một kết nối ngăn xử lý lặp requestId, còn chống chốt kết quả trùng ở DB dùng roundId/fingerprint. Hai cơ chế có phạm vi khác nhau.

Mã loại frame lấy theo thứ tự `PacketType`, nên không đổi thứ tự enum tùy tiện. Thay định dạng không tương thích phải xử lý phiên bản và phân phối đồng bộ shared, server, client, admin. Một enum tồn tại không đồng nghĩa UI đã hỗ trợ: `CREATE_ROOM` bị từ chối vì sản phẩm dùng 20 bàn cố định; có giá trị khác chỉ để tương thích hoặc dự phòng.

Chỉ phiên ADMIN được gọi endpoint quản trị. Bài riêng chỉ gửi cho chủ bài, trừ endpoint soi bàn đã kiểm quyền. Kết quả showdown công khai bài người chưa úp; thắng do chỉ còn một người hoặc hủy ván không tự công khai bài riêng.

### Đồng thời và số điểm

`RoomManager` dùng một monitor chung cho trạng thái trong RAM. JDBC và fsync được đưa ra ngoài vùng khóa, sau đó kiểm lại version/reservation. Các tập `startingUsers`, `adjustingUsers`, `activeRounds` và pending phối hợp để tránh một số dư bị dùng đồng thời cho bắt đầu ván, chốt kết quả hoặc điều chỉnh admin.

Tiền điểm dùng `BigDecimal`, khi chia pot dùng đơn vị nguyên 0,01 điểm. Giữ các bất biến: tổng profit của ván bằng 0; số dư không âm; người đã FOLD không được tranh pot; tầng pot không có người đủ quyền được hoàn cho người góp. Không dùng số thực nhị phân làm nguồn tính kết toán.

Client/admin cập nhật Swing trên EDT, còn kết nối, gửi socket và chờ kết quả ở luồng nền. Giữ kiểm phiên đăng nhập, requestId, roundId và stateVersion để dữ liệu cũ không ghi đè trạng thái mới. Snapshot chỉ cập nhật hiển thị, không tự phát lại SET_READY từ client.

### Bảo mật và dữ liệu vận hành

TLS bật mặc định; chỉ chia sẻ chứng chỉ công khai `server.crt` cho máy client/admin. Mật khẩu, keystore và file bootstrap là dữ liệu riêng. Hash mật khẩu được tạo phía server; TLS bảo vệ mật khẩu khi truyền.

Không xóa journal để giải phóng tài khoản pending; cần xử lý nguyên nhân không chốt được DB. Khi sửa schema, cập nhật cả hai bản SQL; khi sửa luật bài, cập nhật evaluator, engine, test và tài liệu luật tương ứng.

## 11 Build chạy và kiểm thử

### Chạy tại máy phát triển

1. Cài JDK 21, MySQL phù hợp; chạy toàn bộ `database/setup.sql` trước khi mở server.
2. Từ thư mục gốc chạy `Build.cmd` để kiểm schema, chạy Maven test/build và đóng gói vào `dist/`.
3. Chạy `Start-Server.cmd`; cấu hình DB bằng biến môi trường hoặc tham số script. Mặc định server nghe cổng 8888.
4. Chạy `Start-Client.cmd` cho người chơi hoặc `Start-Admin.cmd` cho quản trị. Các file Start tại root chuyển tiếp tới bộ đã build trong dist.
5. Lần đầu, lấy thông tin admin từ file bootstrap mà server thông báo đường dẫn, đổi mật khẩu qua ứng dụng quản trị và xóa file bootstrap sau khi lưu thông tin an toàn.

Các nhóm cấu hình chính:

| Nơi cấu hình | Mục đích |
|---|---|
| `server/src/main/resources/server.properties` | Cổng, giới hạn kết nối, chu kỳ heartbeat, timeout và thời gian lượt. |
| `client/src/main/resources/client.properties` | Host/port mặc định, timeout kết nối và heartbeat phía người chơi. |
| `BACAY_DB_*` | Địa chỉ DB, tài khoản, mật khẩu và giới hạn kết nối JDBC. |
| `BACAY_TLS_*` | Bật/tắt TLS, keystore, chứng chỉ và SAN khi tạo chứng chỉ. |
| `BACAY_DATA_DIR` | Thư mục journal, mặc định `data/pending-results` tính từ thư mục chạy. |
| `BACAY_ADMIN_*` | Tùy chọn mật khẩu bootstrap và đường dẫn file thông tin admin. |

Java không tự đọc file `.env`. Script PowerShell xử lý tham số và biến môi trường; `RuntimeSettings` nhận giá trị từ resource/system property. Khi chạy từ dist, các đường dẫn runtime mặc định nằm dưới dist, ví dụ `dist/data/` và `dist/logs/`. Xem README để cấu hình TLS cho LAN.

### Các bộ test nên biết

| Khu vực test | Kiểm chứng điều gì |
|---|---|
| `shared/.../game/HandEvaluatorTest` | Validation tay bài, tính bất biến và tính nhất quán comparator. |
| `server/.../game/DefaultGameEngineTest` | Đánh giá bài, chia bài, luật lượt/cược, side pot và bảo toàn điểm. |
| `server/.../game/RoomSafetyTest`, `RoomConcurrencyTest`, `AdminRoomInteractionTest` | Điều kiện bàn, xung đột bắt đầu/điều chỉnh, I/O chậm và tương tác quản trị. |
| `server/.../game/IntegrationTest`, `server/.../bus/DataRegressionTest` | Lưu kết quả, retry, dữ liệu và các trường hợp hồi quy. |
| `server/.../network`, `server/.../game/PacketV2Test` | Socket, giới hạn Packet, heartbeat và TLS. Tên lớp test có thể giữ từ phiên bản trước; version chạy thật xem `Packet.VERSION`. |
| `server/.../admin` | Phân quyền và tương tác admin với người chơi. |
| `server/.../bus/JdbcUserBUSTest`, `MySqlSchemaTest` | Nghiệp vụ tài khoản, cài/nâng cấp schema thật và giao dịch; test MySQL cần cấu hình riêng. |
| `client/.../controller`, `client/.../gui` | Reconnect, cập nhật số dư, bố cục, sẵn sàng, nặn bài và chất lượng asset. |
| `admin/.../controller`, `admin/.../network`, `admin/.../gui` | Bỏ phản hồi cũ, soi bàn, request bất đồng bộ, heartbeat và UI quản trị. |

Lệnh thường dùng từ root trong PowerShell:

```powershell
# Chạy toàn bộ kiểm thử và đóng gói JAR trong target của từng module
.\mvnw.cmd -f pom.xml clean verify

# Chạy test server cùng module shared mà server phụ thuộc
.\mvnw.cmd -pl server -am test

# Chạy test giao diện và điều phối người chơi
.\mvnw.cmd -pl client -am test

# Chạy test quản trị
.\mvnw.cmd -pl admin -am test
```

Client/admin được cấu hình test headless; test socket tự mở cổng tạm. MySQL thật chỉ chạy khi có các biến `BACAY_TEST_MYSQL_*`; nếu chưa cấu hình, các ca đó có thể bị skip. `client/src/test/java/com/game3cay/gui/UiPreview.java` hỗ trợ xuất ảnh giao diện offline. Cách dùng và cách đọc báo cáo nằm trong tài liệu Testing.

## 12 Tra nhanh khi cần sửa một chức năng

| Yêu cầu | Đọc hoặc sửa từ đâu | Kiểm chứng liên quan |
|---|---|---|
| Đổi cách tính điểm hoặc so bài | `shared/game/HandEvaluator`, `Rank`, `Suit`; đối chiếu `DefaultGameEngine` | Evaluator và engine test; cập nhật luật chơi. |
| Đổi luật theo/tố/all-in, pot | `DefaultGameEngine.handleAction()` và các hàm tính pot/kết quả | Turn, side pot, profit bằng 0, không âm và hoàn cược. |
| Sửa sẵn sàng, tự bắt đầu, đổi cược | `RoomManager`, `ConnectionController`, `RoomPanel`, `GamePanel` | RoomSafety, RoomConcurrency và ReadinessUi. |
| Đổi sảnh, tìm bàn hoặc Chơi ngay | `LobbyPanel`; phần tải danh sách ở controller | UiLayout và snapshot danh sách. |
| Đổi vị trí ghế, bài hoặc đồng hồ | `GameTablePanel`, `GamePanel`, `Theme` | UiPreview, UiLayout và HandExperience. |
| Sửa nặn bài hoặc ẩn/hiện | `HandRevealState`, `CardSqueezePanel`, `CardSqueezeDialog` | HandExperience; không tự mở khi snapshot lặp. |
| Đổi ảnh, icon hoặc âm thanh | `Assets`, `PlayingCardArt`, `GameIcons`, `Sound`, thư mục assets | AssetRendering và preview ở nhiều tỷ lệ. |
| Thêm thông điệp qua mạng | `shared/model`, `PacketType`, `PacketIO`, `ClientHandler`, controller tương ứng | Protocol/socket/TLS, validation và phân quyền. |
| Sửa tài khoản hoặc mật khẩu | `JdbcUserBUS`, `UserDAO`, `PasswordUtil`; UI liên quan | JdbcUserBUS, AdminSecurity và test luồng client/admin. |
| Sửa lưu kết quả hoặc lệch điểm | `ResultPersistence`, `JdbcGameBUS`, schema; reservation trong `RoomManager` | Retry, idempotency, transaction, bảo toàn điểm. |
| Sửa lịch sử hoặc xếp hạng | `JdbcGameBUS`, DTO phân trang, `DataDialog` | DataRegression và phân trang; chú ý cache leaderboard. |
| Sửa quản trị hoặc soi bàn | `AdminController`, các panel, `ClientHandler`, `RoomManager`, `UserDAO` | AdminSecurity, AdminControllerIntegration, RoomInspectorRequests. |
| Sửa reconnect hoặc dữ liệu cũ | `SocketClient`, `ConnectionController`, `ReconnectState`, snapshot | ReconnectState và UiRegression. |
| Đổi cách build hoặc phân phối | Root/module POM, `scripts/Build.ps1`, các script Start | Build sạch, schema đồng bộ và chạy từ dist. |

Một tính năng thường đi qua nhiều lớp. Cách lần theo thực tế là **nút hoặc màn hình → controller → PacketType và DTO → ClientHandler → RoomManager hoặc BUS → engine hoặc SQL → snapshot/phản hồi → giao diện**. Khi giao việc, nên chỉ rõ cả phần xử lý và kiểm thử tương ứng thay vì chỉ giao tên một file.

## 13 Tài liệu và thứ tự đọc mã nguồn

Để có một lần đọc code xuyên suốt, có thể đi theo thứ tự: `RunClient → ConnectionController → SocketClient → Packet/PacketIO → ServerApplication → ClientHandler → RoomManager → DefaultGameEngine → ResultPersistence → JdbcGameBUS`. Sau đó đọc `RunAdmin → AdminController → AdminSocketClient` để hiểu các chức năng quản trị dùng chung máy chủ.

Các tài liệu chuyên sâu:

- [README](../README.md): cài đặt, môi trường, chạy LAN, TLS và vận hành.
- [Luật chơi](../Game_Rules.md): tính điểm, so chất, cược, pot và mất kết nối.
- [Kiến trúc](Architecture.md): đồng thời, reservation và journal.
- [Protocol](Protocol.md): cấu trúc frame, request/push, validation và quyền.
- [Cơ sở dữ liệu](Database.md): schema, transaction và chống chốt trùng.
- [Giao diện và dữ liệu](Screen_Data_Mapping.md): màn hình tương ứng Packet và trạng thái UI.
- [Kiểm thử](Testing.md): lệnh chạy, MySQL test và preview giao diện.
