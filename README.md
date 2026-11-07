# Ba Cây Online

Ứng dụng nhiều người chơi dùng Java 21, Swing, TCP/TLS và MySQL 8.0.16 trở lên. Maven có bốn module: `shared` (DTO, luật bài thuần, protocol), `server` (ván bài/JDBC), `client` (người chơi) và `admin` (quản trị).

JDBC dùng `DriverManager` và MySQL Connector/J; TLS/JSSE, logging JUL và giới hạn đồng thời dùng sẵn trong JDK. Giao thức Packet v5 có giới hạn kích thước; cần cập nhật đồng bộ server, client và admin.

| Thành phần | Trách nhiệm |
|---|---|
| `shared/` | Model dùng chung, đánh giá bài, Packet và TLS phía client |
| `server/` | Phiên đăng nhập, bàn chơi, engine, journal và JDBC |
| `client/` | Giao diện Swing của người chơi và khôi phục kết nối |
| `admin/` | Giao diện Swing quản trị, soi bàn và sổ cái |
| `database/`, `scripts/` | Schema MySQL và script build/khởi chạy Windows |

## Cài đặt và chạy trên Windows

1. Cài JDK 21 và MySQL. Chạy **toàn bộ** `database/setup.sql` trong MySQL Workbench hoặc mysql CLI. Script giữ tài khoản, số dư và lịch sử ván, thêm migration cần thiết và không cài mật khẩu admin chung. Khi nâng cấp, script xóa các bảng và lịch sử xin/cho điểm đã ngừng hỗ trợ.
2. Chạy `Build.cmd` để biên dịch, kiểm thử và đóng gói vào `dist/`. Lần đầu cần mạng để Maven tải dependency.
3. Chạy `Start-Server.cmd`; nhập mật khẩu MySQL nếu môi trường chưa cấu hình. Có thể chỉ định `-DbUser bacay_app -DbPassword ...`; nên dùng biến môi trường để tránh lưu mật khẩu trong lịch sử lệnh.
4. Lần chạy đầu, server tạo tài khoản `admin` với mật khẩu ngẫu nhiên riêng cho bản cài. Đường dẫn tệp bootstrap được thông báo trong log; mặc định là `dist/data/admin-bootstrap.properties`. Tệp chỉ cấp quyền cho chủ sở hữu. Mật khẩu không được ghi vào log. Có thể cung cấp `BACAY_ADMIN_PASSWORD` (16–128 ký tự) khi bootstrap.
5. Chạy `Start-Admin.cmd`, đăng nhập bằng thông tin trong tệp bootstrap, đổi mật khẩu qua nút **Đổi mật khẩu**, rồi xóa tệp bootstrap khi đã lưu mật khẩu an toàn. Server giữ nguyên tài khoản admin đã có mật khẩu riêng. Tài khoản seed cũ còn dùng mật khẩu công khai được đổi sang mật khẩu bootstrap riêng khi server khởi động; mật khẩu công khai cũ bị từ chối.
6. Chạy `Start-Client.cmd`. Đăng ký người chơi mới được 1.000 điểm; chọn bàn trong 20 bàn có sẵn. Admin dùng ứng dụng quản trị riêng.

Các file Start ở root chuyển tiếp vào `dist/`; cần build trước khi chạy từ một clone mới. Không chạy Start trực tiếp từ `scripts/` vì các JAR nằm trong `dist/`.

## TLS và máy chơi trong LAN

TLS bật mặc định. Server tạo khóa riêng PKCS12 và chứng chỉ tự ký ở `data/tls/` tương đối với thư mục chạy (mặc định `dist/data/tls/`). Chứng chỉ có localhost và địa chỉ IPv4 của máy khi tạo lần đầu. `BACAY_TLS_SAN` thêm SAN, ví dụ `dns:game.example.com,ip:192.168.1.10`; giá trị này dùng khi tạo chứng chỉ mới.

Chỉ sao chép **server.crt** từ server đến máy người chơi/admin. Đặt `BACAY_TLS_CERT` là đường dẫn đến tệp chứng chỉ trên máy đó; nếu không đặt, client tìm `data/tls/server.crt`, sau đó dùng trust store mặc định của JVM. Client kiểm tra hostname/IP trong chứng chỉ. Không phân phối `server.p12` hoặc `keystore-password.txt`.

Mở TCP 8888 trong firewall server. Từ máy khác chạy `Start-Client.cmd 192.168.1.10 8888` hoặc đổi địa chỉ tại màn hình đăng nhập. IP/hostname phải nằm trong SAN chứng chỉ; khi IP đổi cần tạo chứng chỉ phù hợp và phân phối lại chứng chỉ công khai. Có thể cấp chứng chỉ do CA tin cậy ký và dùng keystore được cấu hình bên dưới.

`BACAY_TLS_ENABLED=false` chỉ dành cho môi trường thử nghiệm cục bộ được kiểm soát và phải đặt nhất quán ở cả server/client/admin. Khi tắt TLS, mật khẩu và dữ liệu game đi qua TCP không mã hóa.

## Cấu hình

Script PowerShell ưu tiên tham số được truyền rõ ràng, sau đó biến môi trường hiện có, sau đó mặc định. `-DbUrl` ưu tiên URL; nếu truyền `-DbHost/-DbPort/-DbName`, script tạo URL từ các thành phần này. Java không tự đọc `.env`; xem `server/src/main/resources/database.env.example`.

| Biến môi trường | Tham số Start-Server | Mặc định / mục đích |
|---|---|---|
| BACAY_DB_URL | -DbUrl | JDBC URL MySQL; mặc định localhost:3306/bacay_online |
| BACAY_DB_HOST | -DbHost | localhost; script dùng khi tạo URL |
| BACAY_DB_PORT | -DbPort | 3306 |
| BACAY_DB_NAME | -DbName | bacay_online |
| BACAY_DB_USER | -DbUser | root; nên tạo tài khoản ứng dụng riêng |
| BACAY_DB_PASSWORD | -DbPassword | Hỏi nếu biến chưa được đặt; chấp nhận mật khẩu trống được truyền rõ |
| BACAY_SERVER_PORT | -Port | 8888 trong script; Java cũng nhận cổng ở đối số đầu |
| BACAY_DB_MAX_CONNECTIONS | — | Tối đa 12 kết nối JDBC đồng thời, khoảng 1–64 |
| BACAY_DB_ACQUIRE_TIMEOUT_MS | — | 5000 ms chờ lượt mở kết nối, khoảng 250–60000 |
| BACAY_DATA_DIR | — | data/pending-results; journal kết quả chưa chốt |
| BACAY_ADMIN_PASSWORD | — | Tùy chọn cho bootstrap, không ghi đè mật khẩu admin đã đổi |
| BACAY_ADMIN_CREDENTIAL_FILE | — | data/admin-bootstrap.properties; tệp bootstrap riêng của server |
| BACAY_TLS_ENABLED | — | true |
| BACAY_TLS_DIR | — | data/tls |
| BACAY_TLS_KEYSTORE | — | {TLS_DIR}/server.p12, alias bacay-server |
| BACAY_TLS_STORE_PASSWORD | — | Tùy chọn; nếu bỏ trống, lưu bí mật riêng trong thư mục TLS |
| BACAY_TLS_CERT | — | Chứng chỉ CA/server được client và admin tin cậy |
| BACAY_TLS_SAN | — | SAN bổ sung khi tạo chứng chỉ |

Các giới hạn kết nối, heartbeat và lượt chơi có trong `server.properties`; Java system property cùng tên ghi đè giá trị resource. Không lưu thông tin xác thực hay khóa TLS vào Git.

## Luật và độ bền dữ liệu

Sảnh chơi hỗ trợ tìm số bàn, lọc bàn trống/người đang chờ và **Chơi ngay**. Bố cục tự đổi số cột theo cửa sổ. Thanh đầu có Xếp hạng và Lịch sử ván; **•••** mở Luật chơi, Âm thanh và Đăng xuất. Trong bàn có khung trò chuyện riêng. Giao diện vẫn dùng Java Swing/Java2D, không bổ sung thư viện.

Ảnh lá bài, bàn chơi và nhân vật được thu nhỏ có lọc và vẽ theo độ phân giải màn hình, gồm HiDPI. Icon chip và vương miện vẽ bằng Java2D để giữ nét khi co giãn. Cache ảnh có giới hạn bộ nhớ.

Bài mới chia được úp cả ba lá. Bấm **Nặn bài**, giữ phần dưới lá bài và kéo lên từ từ: mặt bài hé dần từ mép dưới theo độ kéo. Kéo đủ xa rồi thả để mở; kéo ngắn rồi thả thì lá bài trở lại trạng thái úp. Đóng hộp vẫn giữ các lá chưa mở ở trạng thái úp, mở lại tiếp tục tiến độ. **Mở hết** mở ngay cả ba lá. Khi đã mở hết, có thể **Ẩn bài / Xem lại**. Thời gian cược vẫn chạy khi nặn; **Trở về bàn** hoặc Esc để hành động.

Chuẩn bị ván mới chỉ hiển thị ở thanh nhỏ dưới bàn, không có khung sẵn sàng che giữa bàn. Đủ người và đủ điều kiện, máy chủ tự đếm ngược; vào ván thì thanh này ẩn ngay. **Tạm nghỉ** hủy sẵn sàng và dừng đếm ngược của bàn; **Chơi tiếp** xác nhận tham gia với mức cược hiện tại. Không có checkbox/timer tự sẵn sàng ở client để tự bật lại sau khi bạn đã chọn nghỉ.

Bộ bài 40 lá từ A đến 10, không có J/Q/K. Xếp hạng Sáp > 10 điểm > 9 điểm > ... > 1 điểm; không áp dụng liêng hoặc bồ đội. Điểm là hàng đơn vị của tổng (A = 1, 10 = 0), riêng 0 được tính thành 10 điểm. Xem `Game_Rules.md`.

Rời bàn hoặc mất kết nối trong ván được xử lý như úp bài; không hủy miễn phí để né thua. Admin reset đi qua luồng hủy ván có journal và cleanup. Admin không được sửa số dư của tài khoản đang tham gia/chờ chốt kết quả. Các giao dịch admin và kết quả ván đều xuất hiện trong sổ cái.

Kết quả được ghi journal `.round` bằng Java serialization, fsync và đổi tên nguyên tử trước khi phát kết quả; worker thử chốt DB mỗi 3 giây. Chốt DB dùng transaction, khóa tài khoản theo thứ tự và chống ghi trùng. Giữ nguyên thư mục journal khi nâng cấp/di chuyển server. Không xóa journal để giải phóng tài khoản đang chờ chốt; phải xử lý nguyên nhân lỗi lưu trước.

## Build và kiểm thử

Windows: `Build.cmd` hoặc `.\mvnw.cmd -f pom.xml clean verify`. Lệnh Maven chạy kiểm thử và tạo JAR trong `target/` của từng module; `scripts/Build.ps1` được gọi bởi `Build.cmd` để kiểm schema, chạy `clean install` rồi copy JAR, dependency, script và toàn bộ tài liệu vào `dist/`.

Linux/macOS: `bash server/mvnw -f pom.xml clean verify`. Các script khởi chạy/đóng gói hiện được hỗ trợ trên Windows; chạy Java trực tiếp nếu triển khai trên hệ điều hành khác.

Root `pom.xml` là parent của cả bốn module, quản lý Java 21, UTF-8, compiler/Surefire và phiên bản dependency dùng chung. Client/admin chạy test Swing ở chế độ headless; JUnit và H2 chỉ phục vụ kiểm thử. Khi sửa cấu hình chung, cập nhật parent; chỉ để cấu hình đặc thù trong POM module.

Test thường dùng H2. CI còn chạy MySQL thực để kiểm tra script cài mới/nâng cấp, giữ dữ liệu cũ và tính idempotent của giao dịch. Muốn chạy test MySQL tại máy phát triển, đặt `BACAY_TEST_MYSQL_URL`, `BACAY_TEST_MYSQL_USER`, `BACAY_TEST_MYSQL_PASSWORD` rồi chạy Maven verify. Test chỉ tạo/xóa database riêng tên `bacay_ci_<uuid>` hoặc `bacay_test_<uuid>`; tài khoản test cần quyền CREATE/DROP DATABASE.

## Vận hành

Log JUL ghi console và xoay vòng `logs/server-*.log` (5 file, tối đa 5 MB/file). Lỗi xác thực, đọc protocol hoặc chốt kết quả có ngữ cảnh để chẩn đoán; không ghi mật khẩu hoặc bài riêng. Theo dõi journal pending và lỗi kết nối DB trước khi khởi động ván mới.

Kết nối DB dùng JDBC trực tiếp, mỗi lần mở tạo một kết nối độc lập và đóng kết nối vật lý sau khi dùng. Mã hash sử dụng PBKDF2WithHmacSHA256, salt 16 byte và 310.000 vòng hiện tại.

Tài liệu dự án:

- [Luật chơi](Game_Rules.md): bộ bài, xếp hạng, cược và xử lý rời bàn.
- [Kiến trúc](docs/Architecture.md): trách nhiệm module, đồng thời và journal.
- [Giao thức](docs/Protocol.md): frame Packet v5, request/push và phân quyền.
- [Cơ sở dữ liệu](docs/Database.md): schema, transaction, dữ liệu lịch sử.
- [Giao diện và dữ liệu](docs/Screen_Data_Mapping.md): màn hình, packet và trạng thái UI.
- [Kiểm thử và phát triển](docs/Testing.md): lệnh chạy, phạm vi test và kết quả xác minh.
