# Luật Chơi Ba Cây (Game Rules)

Tài liệu này quy định đầy đủ và chính xác toàn bộ luật chơi, phân cấp xếp bài, cơ chế so chất và quy tắc đặt cược trong trò chơi **Ba Cây Online**.

---

## 1. Quy Định Chung & Bộ Bài

- **Bộ bài sử dụng:** Bộ bài **40 lá**, bỏ J, Q, K (gồm A và các quân từ 2 đến 10, với 4 chất: Bích ♠, Chuồn/Tép ♣, Rô ♦, Cơ ♥).
- **Số lượng người chơi:** Mỗi bàn có từ **2 đến 8 người chơi**.
- **Tiền cược sàn (Ante):** Mỗi người chơi khi bắt đầu ván bài phải góp mức cược cơ bản của bàn (mặc định là **10 điểm**, chủ bàn có quyền tùy chỉnh).
- **Chia bài:** Mỗi người chơi được chia ngẫu nhiên **3 lá bài** kín từ bộ bài 40 lá đã được xáo trộn ngẫu nhiên. Trong suốt ván, không bao giờ xảy ra tình trạng trùng lá giữa các người chơi.

---

## 2. Thứ Tự Phân Loại Bộ Bài (Hand Hierarchy)

Thứ tự sức mạnh của các bộ bài được xếp từ cao xuống thấp như sau:

**Sáp > 10 điểm > 9 điểm > ... > 1 điểm.**

Không áp dụng liêng. Ba lá liên tiếp (kể cả A-2-3) được tính điểm bình thường. Không có bồ đội vì bộ bài đã bỏ J, Q, K.

### 2.1. Sáp (Three of a Kind)
- **Định nghĩa:** Bộ 3 lá bài có **cùng giá trị số**.
- **Thứ tự giá trị:** **A-A-A > 10-10-10 > 9-9-9 > ... > 2-2-2**.
- Sáp Át là bộ bài mạnh nhất. Mọi sáp đều thắng bài 10 điểm.

### 2.2. Điểm
- **Định nghĩa:** Các bộ bài không phải sáp được tính theo tổng điểm của 3 lá:
  - Quân **Át (A)**: tính **1 điểm**.
  - Các quân từ **2 đến 9**: tính theo số trên mặt lá.
  - Quân **10**: tính **0 điểm** (tương đương cộng 10 khi lấy hàng đơn vị).
- **Điểm số cuối cùng:** Lấy hàng đơn vị của tổng; nếu bằng **0** thì tính là **10 điểm**.
- **Ví dụ:** 2-3-5 và 2-8-10 đều là **10 điểm**; A-2-3 là **6 điểm**; 4-5-6 là **5 điểm**.
- **Xếp hạng:** **10 > 9 > 8 > ... > 1**. Không có bài 0 điểm.
- **So sánh khi bằng điểm:** Áp dụng quy tắc so chất và lá bài ở mục 3.

---

## 3. Quy Tắc So Chất & Lá Bài (Tie-Breaking Rules)

Khi hai người chơi có cùng loại bài và cùng cấp giá trị, phân định thắng thua theo thứ tự ưu tiên chất truyền thống của Việt Nam:

$$\mathbf{Rô\ (\diamond)} > \mathbf{Cơ\ (\heartsuit)} > \mathbf{Chuồn\ /\ Tép\ (\clubsuit)} > \mathbf{Bích\ (\spadesuit)}$$

| Thứ hạng | Tên chất | Ký hiệu | Màu sắc | Độ ưu tiên |
|:---:|:---:|:---:|:---:|:---:|
| **1** | **Rô (Diamonds)** | ♦ | Đỏ | **Cao nhất** |
| **2** | **Cơ (Hearts)** | ♥ | Đỏ | Thứ nhì |
| **3** | **Chuồn / Tép (Clubs)** | ♣ | Đen | Thứ ba |
| **4** | **Bích (Spades)** | ♠ | Đen | **Thấp nhất** |

**Khi bằng điểm:** So chất cao nhất có trong bài theo thứ tự **Rô ♦ > Cơ ♥ > Chuồn ♣ > Bích ♠**. Nếu cùng chất, so số của lá cao nhất thuộc chất đó; **Át lớn nhất**.

### So sánh chi tiết từng lá:
Trong cùng một chất, thứ tự giá trị quân bài từ lớn đến bé là:
$$\mathbf{A} > \mathbf{10} > \mathbf{9} > \mathbf{8} > \mathbf{7} > \mathbf{6} > \mathbf{5} > \mathbf{4} > \mathbf{3} > \mathbf{2}$$

- Hệ thống sẽ sắp xếp 3 lá bài của mỗi người theo chất giảm dần (Rô $\rightarrow$ Cơ $\rightarrow$ Chuồn $\rightarrow$ Bích), trong cùng chất sắp xếp theo rank giảm dần (A $\rightarrow$ 2).
- Tiến hành so sánh đối đầu từng lá một (lá thứ nhất, lá thứ hai, lá thứ ba). Người nào có lá vượt trội trước sẽ giành chiến thắng.

---

## 4. Diễn Biến Vòng Cược (Betting Round)

Mỗi ván bài diễn ra qua các bước chuẩn hóa sau:

```mermaid
flowchart TD
    A[Bắt đầu ván] --> B[Trừ Ante theo mức bàn, mặc định 10 điểm / người]
    B --> C[Chia 3 lá bài ngẫu nhiên cho mỗi người]
    C --> D[Vòng cược: Bắt đầu từ người kế tiếp chủ bàn theo chiều kim đồng hồ]
    D --> E{Đến lượt hành động<br/>Thời gian suy nghĩ: 30s}
    E -- Quá 30s --> F[Hệ thống tự động Úp bài / FOLD]
    E -- Chọn hành động --> G[Kiểm tra / Theo / Tố đến / Tất tay / Úp bài]
    G --> H{Vòng cược hoàn tất?}
    F --> H
    H -- Chưa --> D
    H -- Chỉ còn 1 người chưa úp --> I[Người duy nhất thắng ván<br/>Không bắt buộc lật bài]
    H -- Có từ 2 người so bài --> J[So bài Showdown<br/>Lật bài & Phân định theo Hand Hierarchy]
    I --> K[Thanh toán Pot chính & Pot phụ]
    J --> K
    K --> L[Ghi journal bền vững]
    L --> M[Phát kết quả và hiển thị số dư ván]
    L --> N[Worker chốt MySQL, cập nhật thống kê và BXH]
```

### 4.1. Các Hành Động Đặt Cược
1. **Kiểm tra (Check):**
   - Chỉ khả dụng khi số điểm bạn đã cược trong ván bằng với mức cược cao nhất hiện tại trên bàn.
   - Giữ nguyên mức cược và chuyển lượt cho người kế tiếp.
2. **Theo (Call):**
   - Bỏ thêm số điểm cần thiết để đưa tổng tiền cược của mình bằng với mức cược cao nhất trên bàn.
   - Nếu số dư không đủ số điểm cần theo, hành động sẽ tự động chuyển thành **Tất tay**.
3. **Tố đến (Raise to):**
   - Nâng tổng số điểm đóng góp của mình trong ván lên một mức cụ thể (phải lớn hơn mức cược cao nhất hiện tại).
   - Số điểm nhập vào ô tố là **tổng điểm tích lũy của bạn trong ván** (bao gồm cả tiền Ante cơ bản).
4. **Tất tay (All-in):**
   - Đặt cược toàn bộ số điểm còn lại trong tài khoản vào ván đấu.
   - Người tất tay không bị buộc phải úp bài và được quyền đi tiếp đến vòng so bài để tranh các phần pot mà mình đã đóng góp.
5. **Úp bài (Fold):**
   - Bỏ cuộc trong ván hiện tại. Toàn bộ số tiền đã cược trước đó sẽ nằm lại trên bàn.

### 4.2. Giới Hạn Thời Gian (Turn Timeout)
- Mỗi lượt hành động có thời gian suy nghĩ tối đa là **30 giây**.
- Đây là mặc định; người vận hành có thể cấu hình `game.turnTimeoutSeconds` trong khoảng 5–300 giây. Quá thời gian cấu hình, máy chủ tự úp bài.
- Có thanh đo thời gian đếm ngược màu vàng ngay dưới avatar người chơi.
- Nếu người chơi không chọn hành động trong 30 giây, máy chủ sẽ tự động kích hoạt hành động **Úp bài (Fold)** để bảo đảm ván đấu không bị gián đoạn.

### 4.3. Cơ Chế Pot Phụ (Side Pots)
- Khi một hoặc nhiều người chơi chọn **Tất tay** với số điểm ít hơn mức cược của những người khác, hệ thống sẽ tự động chia quỹ cược thành **Main Pot (Pot chính)** và một hoặc nhiều **Side Pots (Pot phụ)**.
- Người tất tay chỉ được quyền tranh giải trong các Pot mà họ có tham gia đóng góp điểm. Những người còn nhiều điểm sẽ tiếp tục cược với nhau để tranh Side Pot riêng.
- Nếu một tầng pot không còn người đủ điều kiện nhận do mọi người đóng góp ở tầng đó đã úp/rời bàn, tầng này được hoàn lại cho các người đóng góp. Các tầng còn người đủ điều kiện vẫn được phân định bình thường. Số điểm kết toán chính xác đến 0,01 điểm.

### 4.4. Xem Và Nặn Bài Riêng
- Ba lá mới chia được úp. Chọn **Nặn bài**, giữ phần dưới từng lá rồi kéo lên từ từ để hé mặt bài từ mép dưới; kéo đủ xa rồi thả để mở lá đó.
- **Mở hết** cho phép xem ngay cả ba lá. Đóng hộp nặn không tự mở các lá còn lại; có thể mở lại để tiếp tục trong cùng ván.
- Thời gian cược vẫn chạy khi nặn bài. Bấm **Trở về bàn** hoặc Esc để thực hiện hành động cược. Cách xem bài không thay đổi luật cược hay kết quả ván.

### 4.5. Chuẩn Bị Ván Kế Tiếp
- Bàn tự đếm ngược khi đủ người sẵn sàng và đủ điều kiện. Thời gian chờ nằm ở thanh dưới bàn, không che bài hoặc vị trí người chơi.
- Trong lúc chờ, bấm **Tạm nghỉ** để hủy sẵn sàng và dừng đếm ngược. Bấm **Chơi tiếp** để xác nhận tham gia với mức cược hiện tại; chủ bàn có thể **Bắt đầu ngay** khi hợp lệ.
- Thanh chuẩn bị ẩn khi bắt đầu ván. Sau khi đổi mức cược, người bị hủy sẵn sàng cần bấm **Chơi tiếp** để xác nhận lại.

---

## 5. Kết Thúc Ván & Xử Lý Sự Cố Kết Nối

### 5.1. Thắng Trực Tiếp (Sole Survivor)
- Nếu tất cả người chơi khác đều đã úp bài và chỉ còn duy nhất 1 người trụ lại, người đó thắng các pot mình đủ điều kiện nhận mà **không cần lật bài**. Tầng pot không có người đủ điều kiện nhận được hoàn theo mục 4.3.

### 5.2. So Bài Công Khai (Showdown)
- Khi tất cả người chơi còn quyền đã hoàn thành đặt cược và cân bằng mức theo, bài của tất cả người chơi tham gia so bài sẽ tự động lật mở trên bàn.
- Màn hình kết quả hiển thị tên người chiến thắng và số điểm cộng/trừ trong **6 giây** trước khi chuyển về trạng thái chờ ván mới.

### 5.3. Mất Kết Nối & Hủy Ván
- Người chơi bị rớt mạng hoặc tắt ứng dụng giữa ván: Hệ thống xem như người đó đã **Úp bài**; số điểm đã cược vẫn ở trên bàn.
- Rời bàn hoặc mất mạng không tự hủy ván. Ván tiếp tục với phần cược đã đặt; nếu chỉ còn một người chưa úp bài thì người đó thắng theo mục 5.1. Người đã rời bàn vẫn được kết toán trong kết quả ván.
- Thao tác hủy ván có chủ đích của quản trị viên tạo kết quả **CANCELLED** và hoàn lại toàn bộ cược của ván. Engine cũng áp dụng hủy/hoàn cược khi không còn người nào chưa úp/chưa rời bàn. Máy chủ phải ghi journal kết quả hủy trước khi giải phóng khóa điểm và đặt lại bàn.
- Ghi MySQL chạy bất đồng bộ sau khi journal đã bền vững. Nếu DB tạm lỗi, kết quả vẫn chờ chốt và tài khoản liên quan chưa được vào ván mới; không xóa journal để bỏ qua bước này.

Luật được thực thi bởi `shared.game.HandEvaluator` và `server.game.DefaultGameEngine`; xem [kiến trúc](docs/Architecture.md), [dữ liệu](docs/Database.md) và [kiểm thử](docs/Testing.md) để đối chiếu triển khai.
