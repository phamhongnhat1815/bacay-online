# Ba Cây Online

Bản game Java Swing dùng bộ asset của nhóm, có đăng nhập/đăng ký, 20 bàn cố định, bài và lượt chơi trên bàn, chat, lịch sử, hồ sơ và bảng xếp hạng. Điểm dùng trong game là điểm giải trí.

## Chạy ngay trên Windows

1. Cài **JDK 21** và **MySQL 8.0.16 trở lên**. Kiểm tra `java -version` trong PowerShell. Máy chỉ chơi client không cần MySQL.
2. **Tắt server cũ.** Mở MySQL Workbench, đăng nhập MySQL bằng tài khoản của máy bạn, mở `database/setup.sql` rồi chạy **toàn bộ file** (nút tia sét / Ctrl+Shift+Enter). Chỉ cần file này; bỏ qua các hướng dẫn M1–M6 cũ. Database vẫn là `bacay_online`, giữ tài khoản và lịch sử đã có. Tài khoản cũ có 0 điểm được cấp 1.000 điểm một lần khi nâng cấp; tài khoản mới luôn có 1.000 điểm. Chạy lại SQL không cộng thêm điểm.
3. Mở **Start-Server.cmd** ở thư mục gốc, nhập mật khẩu **MySQL** khi được hỏi. User mặc định `root`. Giữ cửa sổ này mở.
4. Mở **Start-Client.cmd**. Đăng ký hoặc đăng nhập bằng **tài khoản game**. Đăng ký xong tự vào sảnh. Mật khẩu MySQL chỉ nhập ở cửa sổ server.

Gói có sẵn `dist/client.jar`, `dist/server.jar`, `dist/shared.jar` và MySQL JDBC driver. **Không cần build hay chạy công cụ thử kết nối để chơi.** Nên giải nén sang một thư mục mới, ví dụ `C:\Games\bacay-online`, để tránh dùng lẫn JAR của bản trước.

## Vào chơi

- Sảnh luôn có **Bàn 1 → Bàn 20**, theo đúng thứ tự. Bàn có người hiện số người, bàn đang chơi hoặc đầy sẽ tạm khóa nút vào. Trạng thái tự cập nhật.
- Bấm vào một bàn đang chờ. Người vào đầu tiên làm chủ bàn; khi chủ bàn rời đi, người còn lại được chuyển quyền.
- Mọi người bấm **Sẵn sàng**. Cần ít nhất 2 người đủ 10 điểm; chủ bàn bấm **Bắt đầu**. Mỗi bàn tối đa 8 người.
- Bạn nhận 3 lá, hiển thị phía dưới. Bài của người khác úp trên mặt bàn. Khi kết thúc, các bài được phép công khai mới lật lên.
- Đến lượt chọn **Kiểm tra / Theo**, **Tố đến**, **Úp bài** hoặc **Tất tay**. “Tố đến” là **tổng điểm bạn góp trong ván**, gồm cả điểm cơ bản. Hết 30 giây sẽ tự úp bài.
- Header luôn hiện điểm của bạn. Kết quả ghi lịch sử và bảng xếp hạng. Bấm **Sẵn sàng** để chơi ván tiếp theo.
- Có thể bật/tắt âm thanh, ẩn/hiện bài trên máy mình, xem luật, hồ sơ và đổi mật khẩu. Rời bàn mới sửa được tên/email.

Mở hai client và dùng hai tài khoản khác nhau để chơi trên cùng máy. Một tài khoản chỉ đăng nhập một nơi. Game có luật Sáp > Liêng > Bồ đội > Điểm như yêu cầu của nhóm; chi tiết ở `Game_Rules.md` và nút **Luật chơi**.

## Chơi trong cùng Wi-Fi / LAN

- Chỉ một máy chạy MySQL và server. Cho phép Java/server qua Windows Firewall trên mạng riêng, TCP cổng **8888**.
- Xem IPv4 của máy server bằng `ipconfig`.
- Trên máy khác, mở client, bấm **Máy chủ** ở màn đăng nhập, nhập IPv4 máy server và cổng `8888`, lưu rồi đăng nhập. Máy client dùng bộ `dist` và JDK 21.
- Cũng có thể chạy `Start-Client.cmd 192.168.1.10 8888` để đặt địa chỉ cho lần chạy đó.
- Đây là cấu hình dành cho máy cá nhân/LAN của nhóm. Server TCP hiện tại chưa dùng TLS; triển khai qua Internet cần bổ sung TLS và cấu hình vận hành phù hợp.

## Cấu hình máy chủ

`Start-Server.cmd` mặc định dùng MySQL `localhost:3306`, database `bacay_online`, user `root`. Mật khẩu được hỏi lúc chạy, không ghi trong source.

Ví dụ dùng MySQL user khác:

```powershell
.\Start-Server.cmd -DbUser bacay_app
```

Đổi cổng game / URL MySQL:

```powershell
.\Start-Server.cmd -Port 8889 -DbUser bacay_app -DbUrl 'jdbc:mysql://localhost:3306/bacay_online?connectTimeout=5000&socketTimeout=10000&connectionTimeZone=UTC'
```

Đổi cổng tương ứng ở client. Nếu MySQL báo `Public Key Retrieval is not allowed`, cấu hình MySQL có TLS hoặc, với MySQL trên localhost/LAN tin cậy, thêm `&allowPublicKeyRetrieval=true` vào URL. Mặc định dùng thông số phù hợp MySQL cục bộ để chạy ngay.

Nếu server không mở: kiểm tra MySQL đã chạy, đã chạy trọn `setup.sql`, mật khẩu MySQL đúng, JDK 21 nằm trong PATH và cổng 8888 chưa bị bản server cũ chiếm. Đóng bản cũ trước khi mở bản mới.

## Source và build lại

| Thư mục / file | Nội dung |
|---|---|
| `client/` | Toàn bộ giao diện Swing, asset, âm thanh và kết nối client |
| `server/` | Tài khoản, 20 bàn, luật game, xử lý lượt và lưu MySQL |
| `shared/` | Các kiểu dữ liệu và giao thức chung |
| `database/setup.sql` | Một file cài mới hoặc nâng cấp database |
| `dist/` | JAR đã build và các file chạy sẵn |
| `scripts/` | Script build / chạy Windows |
| `docs/Architecture.md` | Luồng dữ liệu và vị trí sửa các phần chính |

Muốn chỉnh code: mở **thư mục gốc có `pom.xml`** bằng IntelliJ IDEA, chọn JDK 21 và import Maven. Sửa xong chạy **Build.cmd** để build/test và cập nhật `dist`; sau đó mở lại server/client. Lần build đầu cần Internet để Maven Wrapper tải dependencies. Build chỉ dùng thông tin test riêng, không tự sửa database đang chơi.

```powershell
.\Build.cmd
```

Hoặc trên Linux/macOS:

```bash
bash server/mvnw -f pom.xml clean install
```

Chạy bằng IntelliJ: `com.game3cay.server.ServerApplication` (module server, thiết lập `BACAY_DB_USER`, `BACAY_DB_PASSWORD`, tùy chọn `BACAY_DB_URL`) và `com.game3cay.RunClient` (module client). Client không có thông tin đăng nhập MySQL.

## Lưu dữ liệu

Kết quả được ghi transaction vào các bảng `play_*`, chống ghi trùng theo ván. Server giữ kết quả đang chờ trong `dist/data/pending-results` và thử ghi lại nếu MySQL tạm ngắt. Giữ thư mục này khi chuyển bản cài có kết quả chưa lưu. Điểm chưa ghi xong sẽ bị khóa cho đến khi lưu thành công.

Nên dừng server khi không có ván đang chơi. Nếu dừng giữa ván, ván đang diễn ra không có kết quả và không trừ điểm trong MySQL; các ván đã chốt kết quả được khôi phục từ hàng chờ khi mở lại. Không chạy hai server cùng database/thư mục dữ liệu.

Asset hình ảnh và âm thanh được lấy từ `bacay-assets-room.zip` do nhóm cung cấp. Các màn hình tham khảo trong asset không được dùng thay cho giao diện tương tác.
