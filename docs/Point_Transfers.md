# Xin điểm và cho điểm

Tính năng dùng điểm giải trí hiện có trong tài khoản, giữ nguyên Java 21, Swing, Socket/TLS, JDBC thuần và MySQL. Gửi yêu cầu không tạo điểm, không tạo khoản nợ và không tự trừ điểm của người được xin.

## Sử dụng

Trong bàn chờ, bấm **Xin điểm** hoặc **Cho điểm** ở khối **Điểm bạn bè** bên phải, chọn một người cùng bàn, nhập số điểm và lời nhắn. Có các mức chọn nhanh 50, 100 và 500. Cho điểm có bước xác nhận tên/ID người nhận cùng số dư dự kiến của người cho.

Nút **Yêu cầu điểm** trong bàn hoặc **Xin / Cho điểm** ở thanh phiên mở hộp thư không chặn việc chơi. Người được xin có thể chọn **Cho điểm** hoặc **Từ chối**; người xin có thể **Hủy yêu cầu**. Mục **Đã cho / Đã nhận** có lịch sử phân trang. **Bỏ qua người này** ẩn thông báo yêu cầu từ người đã chọn trên máy hiện tại và có thể bật lại.

Chỉ thực hiện chuyển khi hai người ở cùng bàn WAITING, không có ván đang khởi tạo/đang chạy/kết quả chờ lưu hoặc thao tác số dư khác. Chuyển thành công hủy sẵn sàng của hai người; bấm **Chơi tiếp** ở thanh dưới bàn để xác nhận tham gia lại. Yêu cầu xin điểm có thể giữ trạng thái chờ trong khi ván diễn ra, nhưng chỉ được đồng ý khi hai người trở lại đúng bàn chờ trước lúc hết hạn.

## Quy tắc mặc định

| Khóa Java system property | Giá trị | Ý nghĩa |
|---|---|---|
| gift.minCompletedGames | 3 | Người cho cần hoàn tất ít nhất 3 ván; ván hủy không được tính |
| gift.dailyLimit | 2000 | Tổng điểm cho trong 24 giờ liên tiếp |
| gift.maxTransfer | 2000 | Mức cho tối đa mỗi lần; hạn mức 24 giờ vẫn được kiểm tra |
| gift.requestExpirySeconds | 120 | Thời hạn yêu cầu xin điểm |
| gift.requestsPerMinute | 3 | Số yêu cầu mới tối đa mỗi người/phút |
| gift.transfersPerMinute | 30 | Số giao dịch cho thành công tối đa mỗi người/phút |

Các khóa có trong server.properties; có thể ghi đè bằng -D khi chạy Java, ví dụ -Dgift.dailyLimit=3000. Điểm phải lớn hơn 0, tối đa hai số thập phân. Không tự cho chính mình; tài khoản phải ACTIVE và role USER. Không cho số dư người nhận vượt DECIMAL(15,2). Mỗi cặp người chơi chỉ có một yêu cầu chưa hết hạn đang chờ, kể cả xin theo chiều ngược lại.

Các giới hạn giảm lạm dụng và spam. Ngưỡng ván chơi không chống hoàn toàn việc tạo tài khoản phụ để gom điểm thưởng đăng ký; bản này chưa tách nguồn điểm thưởng và điểm kiếm được.

## Transaction và gửi lại

RoomManager giữ chỗ đồng thời cả hai user trước I/O, dùng chung cơ chế bảo vệ với thao tác admin và khởi tạo ván. JDBC thực hiện ngoài monitor phòng. Transaction khóa yêu cầu khi đồng ý và khóa hai tài khoản theo ID tăng dần; kiểm số dư, điều kiện, hạn mức; ghi biên nhận, trừ/cộng, hai vế sổ cái và FULFILLED trong cùng commit. Bất kỳ lỗi nào trước commit đều rollback toàn bộ.

Mỗi thao tác có operationId riêng với requestId mạng. UNIQUE(sender_id,operation_id), fingerprint nội dung và UNIQUE(request_id) ngăn chuyển hai lần sau mất ACK, reconnect hoặc đồng ý đồng thời. Reply cache của socket không được dùng làm bảo đảm tài chính. Replay biên nhận thành công không trừ điểm hoặc phát lại số dư cũ.

Client lưu một thao tác chưa xác định kết quả bằng Java Preferences trước khi gửi. Khi kết nối lại, client xác minh bằng đúng UUID và nội dung đó; lỗi chưa rõ kết quả giữ mã để người dùng bấm Xác minh lại. Hộp thư và lịch sử được đọc lại từ DB, không phụ thuộc vào việc nhận đủ mọi push.

## Dữ liệu và quyền riêng tư

- point_requests: người xin/người được xin, số điểm, bàn, lời nhắn, operationId/fingerprint, thời hạn và trạng thái.
- point_transfers: biên nhận, người cho/người nhận, số điểm, thời điểm và request liên quan nếu có.
- point_transfer_entries: hai dòng trừ/cộng, mỗi dòng có số dư trước/sau; tổng delta của giao dịch bằng 0.

Trạng thái: PENDING → FULFILLED / DECLINED / CANCELLED / EXPIRED. Hết hạn được tính ở server và cập nhật khi đọc/xử lý hộp thư; client có đồng hồ hiển thị. Chỉ hai người liên quan xem được yêu cầu/biên nhận của họ. Số dư chỉ push riêng cho chủ tài khoản; không phát vào chat công khai. Admin tra sổ cái với loại GIFT_SENT / GIFT_RECEIVED, người thực hiện và lời nhắn.

## Cài đặt và kiểm chứng

Chạy lại toàn bộ database/setup.sql trước khi mở server; migration schema_v3_point_transfers tạo các bảng mà không xóa người chơi/lịch sử cũ. Build và cập nhật đồng bộ các JAR. Protocol v4 có thêm packet xin/cho điểm, không tương thích phiên bản v3.

Kiểm thử gồm transaction/rollback, bảo toàn tổng điểm, duplicate UUID/nội dung khác, quyền phản hồi, nhận/hủy đồng thời, hết hạn, hạn mức/tốc độ, người bị khóa, xung đột bắt đầu ván/admin/pending, private pushes qua socket và replay sau reconnect. Kiểm thử UI gồm tiền nhập, xác nhận người nhận, trạng thái/expiry và journal thao tác qua khởi động lại.

Kết quả kiểm chứng ngày 29/10/2026: **96 test qua, 0 failure/error/skipped** (server 77, client 12, admin 7), gồm MySQL 8.0 thực chạy trên instance tạm riêng. Kiểm tra bằng JAR đóng gói qua TLS đã chơi đủ 3 ván thật, cho điểm, xin/đồng ý/hủy/từ chối, gửi lại sau reconnect, kiểm tổng điểm và sổ cái admin. Bố cục RoomPanel được render kiểm tra; chưa thực hiện kiểm thử GUI tương tác nhiều máy. Bộ thư viện runtime giữ nguyên.
