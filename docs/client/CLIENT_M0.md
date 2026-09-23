# CLIENT M0 — YÊU CẦU GIAO DIỆN VÀ DỮ LIỆU TRAO ĐỔI

Người phụ trách: Thành  
Phạm vi: UI và mạng phía client  
Mốc: M0 — Chốt chuẩn và hợp đồng ghép

## 1. Mục tiêu

Tài liệu này xác định:

- Các màn hình cần có ở client.
- Những thao tác người dùng được thực hiện.
- Dữ liệu client cần gửi lên server.
- Dữ liệu client cần nhận để hiển thị.
- Dữ liệu game công khai và dữ liệu riêng của từng người chơi.
- Các nội dung Thành cần thống nhất với Khánh và Trọng.

M0 chưa yêu cầu hoàn thiện giao diện hoặc kết nối server thật.

---

## 2. Danh sách màn hình

| Mã | Màn hình | Chức năng chính |
|---|---|---|
| UI01 | Kết nối server | Nhập IP, cổng và kết nối tới server |
| UI02 | Đăng nhập | Đăng nhập tài khoản |
| UI03 | Đăng ký | Tạo tài khoản mới |
| UI04 | Lobby | Xem danh sách phòng, tạo phòng, tham gia phòng |
| UI05 | Phòng chờ | Xem thành viên, chủ phòng, trạng thái sẵn sàng |
| UI06 | Ván chơi | Hiển thị dữ liệu ván và gửi thao tác của người chơi |
| UI07 | Kết quả | Hiển thị kết quả, chơi tiếp hoặc trở lại phòng chờ |
| UI08 | Chat phòng | Gửi và nhận tin nhắn trong phòng |
| UI09 | Hồ sơ | Xem và cập nhật tên hiển thị, đổi mật khẩu |
| UI10 | Lịch sử | Xem các ván đã chơi theo từng trang |
| UI11 | Bảng xếp hạng | Xem thứ hạng và điểm thành tích |

Chat có thể nằm bên trong màn hình phòng chờ và màn hình ván chơi.  
Kết quả có thể là hộp thoại hoặc một vùng trong màn hình ván chơi.

---

## 3. Luồng chuyển màn hình

```mermaid
flowchart TD
    A[Kết nối server] --> B[Đăng nhập hoặc đăng ký]
    B --> C[Lobby]
    C --> D[Phòng chờ]
    D --> E[Ván chơi]
    E --> F[Kết quả]
    F --> D
    C --> G[Hồ sơ]
    C --> H[Lịch sử]
    C --> I[Bảng xếp hạng]