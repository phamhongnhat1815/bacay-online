# Kết quả kiểm tra bản giao

- Maven clean verify với JDK 21: 27 kiểm tra, 0 lỗi, 0 bỏ qua.
- Chạy thêm 6 integration test với MySQL 8 thật: đều đạt.
- Chạy setup.sql khi SQL_SAFE_UPDATES=1: hoàn tất, giữ chế độ Safe Updates. Cấp điểm cho tài khoản cũ có 0 điểm đúng một lần; chạy lại không cộng thêm và không thay đổi tài khoản đã có điểm.
- Hai ứng dụng Swing kết nối server thật: đăng ký và tự đăng nhập, vào Bàn 1, sẵn sàng, bắt đầu, nhận ba lá riêng không trùng, theo lượt, kết thúc, bảo toàn tổng điểm, lưu lịch sử, xem xếp hạng, rời bàn, chuyển chủ bàn, đăng xuất và đăng nhập lại đều đạt.
- Đọc và giải mã đủ 52 ảnh lá bài từ JAR đã đóng gói; không thiếu asset.
- Kiểm tra ảnh giao diện đăng nhập, đăng ký, sảnh 20 bàn, bàn 8 ghế, đang chơi và kết quả ở nhiều kích thước.

Các kiểm tra chạy trên Linux với JDK 21, MySQL 8 và màn hình Xvfb. Launcher CMD/PowerShell được rà soát và đã sửa cách truyền `-Dfile.encoding=UTF-8`; chưa chạy trực tiếp trong môi trường Windows ở phiên kiểm tra này. Bản giao có sẵn JAR nên không cần build hay chạy công cụ thử kết nối trước khi chơi.
