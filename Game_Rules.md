# **1. Tổng quan hệ thống**

-   Hệ thống có một server và nhiều client. Server lưu toàn bộ thông tin
    và dữ liệu của hệ thống.

-   Để chơi, người chơi phải login vào tài khoản của mình từ một máy
    client. Sau khi login thành công, giao diện hiện lên danh sách các
    bàn chơi đang mở. Mỗi bàn chơi có các thông tin: tên bàn, số người
    đang chơi, số người tối đa và trạng thái của bàn.

-   Người chơi có thể chọn một bàn đang có sẵn để tham gia hoặc tạo một
    bàn chơi mới. Khi tạo bàn, người chơi có thể thiết lập tên bàn và số
    lượng người chơi tối đa.

-   Khi một người chơi tham gia vào bàn, hệ thống sẽ thông báo cho những
    người chơi khác trong bàn về người chơi mới. Danh sách người chơi
    trong bàn sẽ hiển thị tên và trạng thái của từng người.

-   Khi bàn đã có đủ số người tối thiểu để bắt đầu, người tạo bàn hoặc
    người có quyền bắt đầu có thể nhấn nút "Bắt đầu". Server sẽ tiến
    hành tạo một ván chơi mới và chia bài cho tất cả người chơi trong
    bàn.

-   Mỗi người chơi được server chia 3 lá bài. Bài của mỗi người chỉ được
    hiển thị cho chính người đó. Server chịu trách nhiệm quản lý bộ bài,
    chia bài và đảm bảo mỗi lá bài chỉ được chia cho một người chơi
    trong cùng một ván.

# **2. Chia bài và xác định loại bài**

-   Sau khi nhận được 3 lá bài, hệ thống sẽ tự động tính toán và xác
    định loại bài của từng người chơi. Các loại bài gồm: Sáp, Liêng, Bồ
    đội và Điểm.

-   **Sáp** là trường hợp người chơi có 3 lá bài cùng giá trị, ví dụ
    3-3-3, 7-7-7, K-K-K. Trong các bộ Sáp, giá trị càng cao thì bài càng
    mạnh và A-A-A là Sáp cao nhất.

-   **Liêng** là trường hợp người chơi có 3 lá bài liên tiếp nhau theo
    thứ tự của bài Tiến lên, ví dụ A-2-3, 2-3-4, 5-6-7, J-Q-K, Q-K-A.
    Trong các bộ Liêng, Q-K-A là Liêng cao nhất.

-   **Bồ đội** là trường hợp cả 3 lá bài đều là bài hình người, tức là
    các lá J, Q hoặc K, ví dụ J-Q-K, J-J-Q, Q-K-K. Bất kỳ bộ 3 lá nào
    chỉ gồm J, Q, K đều được tính là Bồ đội.

-   Nếu không thuộc Sáp, Liêng hoặc Bồ đội thì bài được tính theo
    **điểm**. Giá trị các lá bài số được tính theo giá trị trên lá bài;
    các lá J, Q, K được tính là 0 điểm. Tổng điểm của 3 lá được lấy hàng
    đơn vị. Ví dụ 3-5-K có tổng là 8 điểm, 7-8-4 có tổng là 19 nên được
    tính là 9 điểm. 9 điểm là điểm cao nhất trong các trường hợp bài
    điểm thông thường.

# **3. So sánh và xếp hạng bài**

-   Thứ tự mạnh yếu của các loại bài được quy định như sau: Sáp \> Liêng
    \> Bồ đội \> Điểm. Vì vậy, Sáp luôn thắng Liêng, Bồ đội và bài điểm;
    Liêng thắng Bồ đội và bài điểm; Bồ đội thắng bài điểm.

-   Sau khi tất cả người chơi đã nhận bài, server sẽ tiến hành so sánh
    bài của tất cả người chơi trong bàn để xác định thứ hạng. Người có
    loại bài mạnh hơn sẽ được xếp trên người có loại bài yếu hơn.

-   Nếu hai hoặc nhiều người chơi có cùng loại bài, server sẽ tiếp tục
    so sánh giá trị của bài. Ví dụ, nếu cùng là Sáp thì K-K-K thắng
    Q-Q-Q, và A-A-A là Sáp cao nhất. Nếu cùng là Liêng thì Q-K-A là
    Liêng cao nhất.

-   Nếu hai hoặc nhiều người chơi vẫn có giá trị bài bằng nhau, server
    sẽ sử dụng chất của các lá bài để phân định thắng thua. Thứ tự chất
    được quy định là: Rô \> Cơ \> Bích \> Chuồn.

-   Khi xét chất, người chơi có lá bài thuộc chất cao hơn sẽ được xếp
    trên. Nếu cần tiếp tục phân định thì lần lượt xét theo thứ tự Rô,
    Cơ, Bích, Chuồn.

-   Ví dụ, nếu hai người cùng có một bộ Liêng giống nhau về giá trị, hệ
    thống sẽ tiếp tục xét chất để xác định người thắng. Người có lá Rô
    cao hơn sẽ thắng; nếu không thể phân định bằng Rô thì tiếp tục xét
    Cơ, sau đó Bích và Chuồn.

# **4. Kết thúc ván và cập nhật kết quả**

-   Sau khi server hoàn tất việc so bài, hệ thống sẽ gửi kết quả của ván
    chơi cho tất cả người chơi trong bàn. Giao diện sẽ hiển thị bài của
    từng người, loại bài, số điểm và thứ hạng của từng người chơi.

-   Sau mỗi ván, server sẽ cập nhật kết quả của từng người chơi, bao gồm
    số ván đã chơi, số ván thắng, số ván thua, số điểm hiện có và các
    thống kê liên quan.

-   Sau khi ván kết thúc, server sẽ hiển thị thông báo cho tất cả người
    chơi trong bàn về việc có muốn chơi tiếp hay không. Nếu đủ số người
    cần thiết đồng ý, server sẽ tạo một ván mới và tiếp tục chia bài.

-   Người chơi cũng có thể chọn nút Thoát để rời khỏi bàn hoặc kết thúc
    việc chơi. Khi một người chơi thoát, server sẽ thông báo cho những
    người chơi còn lại và cập nhật trạng thái của bàn.

# **5. Lịch sử và bảng xếp hạng**

-   Kết quả các ván đấu được lưu vào server. Mỗi người chơi có thể vào
    xem lịch sử các ván đã chơi, bao gồm thời gian chơi, bàn chơi, những
    người cùng tham gia, loại bài nhận được và kết quả của ván.

-   Mỗi người chơi đều có thể xem bảng xếp hạng toàn bộ người chơi trong
    hệ thống, được sắp xếp lần lượt theo các tiêu chí: tổng số điểm giảm
    dần, tổng số trận thắng giảm dần.

-   Server chịu trách nhiệm quản lý toàn bộ trạng thái của các bàn chơi,
    người chơi đang online, quá trình chia bài, tính toán kết quả, lưu
    lịch sử và cập nhật bảng xếp hạng.

# **6. Luật tố (đặt cược)**

-   Trước khi vào ván mới, hệ thống yêu cầu tất cả người chơi trong bàn
    đặt cược cơ bản (tiền cửa/ante) theo mức đã được thiết lập khi tạo
    bàn.

-   Sau khi chia bài xong, ván chơi chuyển sang giai đoạn tố. Người chơi
    lần lượt theo chiều kim đồng hồ, bắt đầu từ người ngồi kế người chia
    (hoặc người tạo bàn), thực hiện một trong các hành động: **Tố** (đặt
    thêm tiền cược), **Theo** (đặt bằng mức cược cao nhất hiện tại),
    hoặc **Úp** (bỏ bài, không tham gia tranh phần thắng ở ván đó nữa).

-   Mỗi lượt tố, người chơi có thể chọn mức tố tùy ý nhưng không được
    thấp hơn mức cược cao nhất hiện tại của bàn và không được vượt quá
    mức tố tối đa do bàn quy định (nếu có giới hạn) hoặc vượt quá số
    điểm/tiền còn lại của người chơi (all-in).

-   Khi một người chơi tố, những người chơi còn lại (chưa úp) phải Theo
    hoặc Tố tiếp để tiếp tục ở lại ván; nếu không đủ khả năng theo,
    người chơi có thể chọn All-in với toàn bộ số điểm còn lại.

-   Vòng tố kết thúc khi tất cả người chơi còn lại trong ván đã đặt cược
    bằng nhau, hoặc chỉ còn duy nhất một người chưa úp.

-   Nếu tất cả người chơi khác đều úp, người còn lại được xác định thắng
    ván ngay lập tức và nhận toàn bộ tiền cược (pot) mà không cần lật
    bài so sánh với người khác (bài vẫn được ghi nhận vào lịch sử nhưng
    không công khai nếu người chơi không chọn "lật bài").

-   Khi vòng tố kết thúc mà còn từ hai người chơi trở lên chưa úp,
    server tiến hành so bài giữa những người này theo đúng thứ tự Sáp \>
    Liêng \> Bồ đội \> Điểm như đã quy định, để xác định người thắng ván
    và nhận toàn bộ tiền cược trong pot.

-   Trường hợp nhiều người chơi all-in với số điểm khác nhau, server
    chia pot thành các pot phụ (side pot) tương ứng với mức tiền mỗi
    người đã góp, đảm bảo người chơi chỉ tranh phần pot tương ứng với số
    tiền mình đã đặt.

-   Sau khi ván kết thúc, server cập nhật số điểm của từng người chơi
    dựa trên kết quả tố và so bài, đồng thời lưu chi tiết các mức tố,
    hành động (tố/theo/úp) của từng người vào lịch sử ván đấu.

-   Nếu một người chơi hết điểm cược (không còn đủ điểm để tham gia mức
    cược cơ bản của bàn), hệ thống sẽ tự động chuyển trạng thái người
    chơi đó sang "xem" (spectator) hoặc yêu cầu rời bàn/nạp thêm điểm
    tùy theo cấu hình của bàn.

# **7. Tính năng chat trong bàn**

-   Mỗi bàn chơi có một khung chat riêng, chỉ hiển thị cho những người
    chơi và người xem đang có mặt trong bàn đó.

-   Người chơi có thể gửi tin nhắn văn bản tới tất cả thành viên trong
    bàn; tin nhắn hiển thị kèm tên người gửi và thời gian gửi.

-   Ngoài tin nhắn văn bản, hệ thống hỗ trợ gửi biểu tượng cảm xúc
    (emoji/sticker) nhanh để người chơi phản ứng trong lúc chơi mà không
    làm gián đoạn ván đấu.

-   Bên cạnh tin nhắn của người chơi, hệ thống tự động gửi các thông báo
    hệ thống vào khung chat, ví dụ: người chơi vào/rời bàn, bắt đầu ván
    mới, kết quả ván, hoặc khi có người tố/theo/úp.

-   Người chơi có thể ẩn hoặc tắt tiếng (mute) chat của một người chơi
    cụ thể trong bàn nếu không muốn nhận tin nhắn từ người đó.

-   Hệ thống có cơ chế lọc từ ngữ không phù hợp (spam, từ ngữ nhạy cảm)
    trước khi hiển thị tin nhắn cho các thành viên khác trong bàn.

-   Lịch sử chat của một bàn được lưu tạm thời trong phiên chơi hiện
    tại; khi bàn đóng hoặc người chơi rời bàn, lịch sử chat của bàn đó
    không được lưu lại vào hệ thống lâu dài (trừ khi có yêu cầu lưu trữ
    phục vụ kiểm duyệt).

-   Quản trị viên hệ thống có quyền xem lại nội dung chat của các bàn
    (nếu cần) để xử lý các trường hợp vi phạm quy định như chửi bậy, lừa
    đảo, hoặc hành vi gian lận trong khi chơi.
