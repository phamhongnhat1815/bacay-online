# Kiểm thử và phát triển

Các lệnh dưới đây chạy từ thư mục root của repository. Cần JDK 21; Maven Wrapper tải Maven/dependency ở lần chạy đầu. Root POM quản lý cấu hình chung cho `shared`, `server`, `client` và `admin`.

## Build sạch

Windows PowerShell:

```powershell
.\mvnw.cmd -f pom.xml clean verify
```

Linux/macOS:

```bash
bash server/mvnw -f pom.xml clean verify
```

`verify` tạo JAR ở `target/` của mỗi module. Dùng `Build.cmd` trên Windows để kiểm đồng bộ schema, chạy `clean install` và đóng gói vào `dist/`. Bản phân phối gồm server/client/admin/shared, thư viện runtime, script khởi chạy, SQL, README, luật chơi và toàn bộ `docs/*.md`. Build không khởi động server hoặc sửa database game.

## Phạm vi kiểm thử

| Module | Kiểm tra chính |
|---|---|
| shared | Tay bài không hợp lệ, bất biến dữ liệu, hoán vị lá, tính đối xứng/ngược dấu và thứ tự comparator |
| server | Toàn bộ 7.140 tổ hợp ba lá của bộ 36 lá, chia bài không trùng, lượt/cược, all-in/side pot, bảo toàn điểm và số dư |
| server | Room/concurrency, journal/retry/idempotency, JDBC với H2, phân quyền admin, Packet qua socket và TLS |
| server + MySQL | Cài mới/nâng cấp schema thật, chạy SQL lặp, giữ dữ liệu cũ, bootstrap, transaction và chống chốt trùng |
| client | Reconnect, push admin/profile, bố cục Swing, readiness, nặn bài và render asset/HiDPI |
| admin | Phân trang/phiên UI, soi bàn, request từ EDT, PING/PONG và hoàn tất request khi đóng kết nối |

JUnit/H2 chỉ thuộc test scope. Client/admin dùng `java.awt.headless=true` khi chạy Surefire; không cần mở ứng dụng hoặc có server game chạy sẵn. Các test socket tự mở cổng tạm. Test engine dùng seed cố định để tái hiện lỗi.

## MySQL thật

Nếu chưa đặt `BACAY_TEST_MYSQL_URL`, hai ca `MySqlSchemaTest` được đánh dấu skipped. Để chạy, cấu hình ba biến môi trường trong phiên shell hiện tại:

```powershell
$env:BACAY_TEST_MYSQL_URL = 'jdbc:mysql://127.0.0.1:3306/?allowPublicKeyRetrieval=true&useSSL=false'
$env:BACAY_TEST_MYSQL_USER = 'bacay_test'
# Đặt BACAY_TEST_MYSQL_PASSWORD bằng cơ chế quản lý bí mật tại máy/CI.
.\mvnw.cmd -f pom.xml verify
```

Tài khoản test cần quyền CREATE/DROP DATABASE và quyền thao tác schema/dữ liệu trong database vừa tạo. Test chỉ tạo/xóa database riêng tên `bacay_ci_<uuid>` hoặc `bacay_test_<uuid>`; fixture không ghi vào database game. Các mật khẩu test không được commit. GitHub Actions tại `.github/workflows/verify.yml` cung cấp MySQL 8.4 cho bộ kiểm thử này.

## Chạy một phần

Chạy một module và các module phụ thuộc:

```powershell
.\mvnw.cmd -pl server -am test
.\mvnw.cmd -pl client -am test
.\mvnw.cmd -pl admin -am test
```

Chạy một lớp test, cho phép module phụ thuộc không có lớp cùng tên:

```powershell
.\mvnw.cmd -pl shared -am '-Dtest=HandEvaluatorTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd -pl server -am '-Dtest=DefaultGameEngineTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd -pl admin -am '-Dtest=AdminSocketClientTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Surefire ghi báo cáo vào `<module>/target/surefire-reports/`, gồm XML và bản text. Khi thấy lỗi, đọc lỗi gốc và báo cáo của lớp tương ứng; log `PENDING` trong test cố tình gây lỗi DB/journal không tự đồng nghĩa với test thất bại. Kết quả cuối phải có `BUILD SUCCESS`, không có failures/errors và kiểm rõ số ca skipped.

## Preview giao diện offline

Sau khi Maven đã biên dịch test, chạy trên máy có desktop:

```powershell
java '-Djava.awt.headless=false' '-Dpreview.scale=2' '-Xmx256m' `
  -cp 'client/target/test-classes;client/target/classes;shared/target/classes' `
  com.game3cay.gui.UiPreview 'local-dev/ui-preview-200'
```

Preview xuất PNG đăng nhập, sảnh, bàn chờ/đếm ngược, bàn chơi và các bước nặn bài ở hai kích thước cửa sổ. Công cụ không kết nối DB/server. Trên Linux/macOS thay dấu `;` trong classpath bằng `:` và dùng cú pháp xuống dòng của shell. Preview chỉ phục vụ kiểm tra hình ảnh; cần tự xem ảnh khi thay đổi layout.

## Nguyên tắc khi sửa code

- Chỉnh cấu hình Java, plugin và dependency chung trong root POM; giữ cấu hình đặc thù ở module.
- Đặt luật bài thuần trong shared để server và UI dùng chung. Giữ enum/serialVersionUID cần cho protocol và lịch sử.
- Tách tính pot, phân phối tiền và tạo kết quả; dùng BigDecimal hoặc số nguyên đơn vị 0,01 điểm. Giữ kiểm thử bảo toàn điểm, hoàn cược và không lộ bài riêng.
- Giữ I/O mạng/JDBC/fsync ngoài EDT và monitor trạng thái bàn; không bỏ reservation hoặc journal để đơn giản hóa code.
- Khi đổi schema, sửa cả `database/setup.sql` và `server/src/main/resources/schema.sql`; khi đổi wire format, xử lý phiên bản và cập nhật đồng bộ ứng dụng.
- Cập nhật tài liệu liên quan trong [README](../README.md), [luật](../Game_Rules.md), [kiến trúc](Architecture.md), [protocol](Protocol.md), [database](Database.md) và [mapping UI](Screen_Data_Mapping.md).

## Kết quả xác minh ngày 07/11/2026

Sau refactor, Maven `clean verify` thành công. Lần chạy `verify` có cấu hình MySQL thật đã chạy **127 ca: 127 thành công, 0 thất bại, 0 lỗi, 0 bỏ qua**:

| Module | Số ca |
|---|---:|
| shared | 3 |
| server | 75 |
| client | 32 |
| admin | 17 |

Script `scripts/Build.ps1` cũng đã hoàn tất với cùng cấu hình MySQL: `clean install`, toàn bộ 127 ca và bước copy dependency/đóng gói đều thành công. Hai bản schema giống nhau; các liên kết tài liệu nội bộ hợp lệ.

Các ca mới kiểm comparator dùng chung, hoàn các tầng pot không còn người đủ điều kiện với số lẻ 0,01 điểm, và heartbeat/đóng kết nối admin. Kiểm thử tự động không thay thế kiểm tải vận hành hoặc phiên chơi LAN thủ công.
