# Kế hoạch chuyển đổi chủ đề: Royal Navy & Champagne Gold

## Mục tiêu
Chuyển toàn bộ giao diện Java Swing từ tông màu xanh lá rêu sang tông **Royal Navy & Champagne Gold (Xanh Navy Hoàng Gia & Vàng Ánh Kim)** để đồng bộ hoàn hảo với hình nền cung điện cẩm thạch trắng, áo vest nữ Dealer và mặt bàn nỉ xanh viền vàng.

## Danh sách công việc

1. **`Theme.java`**:
   - Chuyển `BG`, `PANEL` sang Navy đen sâu (`#08101C`, `#0C1626`).
   - Cập nhật `MUTED` sang xám bạc ánh lam (`#96A8C0`).
   - Cập nhật màu ô nhập liệu `field`: nền Navy sẫm, viền xanh tím than trầm mạ vàng nhẹ.
   - Cập nhật `Glass`: nền Navy Sapphire mờ (`new Color(8, 18, 34, 235)`), viền Champagne Gold (`new Color(212, 175, 55, 160)`).
   - Cập nhật viền nút `Button` (plain).

2. **`MainFrame.java`**:
   - Cập nhật lớp phủ `overlay` trên hình nền `casino.png` từ xanh rêu sang Sapphire Navy tối (`new Color(6, 14, 25, 175)`).
   - Cập nhật thanh `header` sang Navy sẫm và đường viền ngăn cách màu vàng gold ánh kim.

3. **`LobbyPanel.java`**:
   - Chuyển gradient nền của 20 thẻ bàn chơi sang Royal Navy Gradient (`#102038` -> `#081220`).
   - Đổi các chấm tròn hiển thị người ngồi từ màu xanh lá sang màu Vàng Gold (`Theme.GOLD`) cho người đã ngồi, và Navy trầm cho ghế trống.

4. **`GameTablePanel.java`**:
   - Cập nhật hộp thoại trung tâm ("Sẵn sàng cho ván mới", bình chọn rời bàn) sang Deep Sapphire viền vàng gold.
   - Cập nhật các ô ghế trống (`Ghế · Trống`) sang tông Navy mờ viền vàng gold nhạt.
   - Cập nhật khung tên / avatar người chơi đang ngồi sang Navy Gradient viền vàng.

5. **`RoomPanel.java` & `GamePanel.java`**:
   - Đổi nút "Sẵn sàng" (`ready`) từ màu `"green"` sang màu `"gold"`.
   - Đổi nút "Theo" (`call`) từ màu `"green"` sang màu `"gold"`.

6. **`DataDialog.java` & `AuthPanel.java`**:
   - Cập nhật lưới bảng (grid), tiêu đề cột (table header), màu dòng được chọn (selection) sang Navy & Gold.
   - Cập nhật lớp phủ màu tối trong `AuthPanel.java` sang Navy.

7. **Xác minh & Đóng gói**:
   - Chạy `Build.cmd` để biên dịch mã nguồn và đóng gói lại `dist/client.jar`.
   - Kiểm tra không còn lỗi compile hay cảnh báo tài nguyên.
