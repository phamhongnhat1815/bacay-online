**BẢN GIAO VIỆC VÀ QUY TẮC TRIỂN KHAI BTL LẬP TRÌNH MẠNG**

Phiên bản 1.0 — Thành, Trọng, Khánh, Nhật.

Cả nhóm dùng tài liệu này làm căn cứ triển khai, ghép module và nghiệm thu. Mỗi người chịu trách nhiệm đưa phần việc của mình đến trạng thái người khác có thể chạy và sử dụng được. Một module chỉ được xem là hoàn thành khi có đầu ra đúng, có cách kiểm tra và đã ghép được với phần liên quan; việc viết xong các lớp chưa đủ để kết luận hoàn thành.

**Mục tiêu của dự án là một ứng dụng desktop nhiều người dùng kết nối tới một server Java.** Bản triển khai phục vụ học tập, gồm game thẻ bài giải trí không đặt cược, tài khoản, phòng chơi, chat, lịch sử và bảng xếp hạng. Điểm chỉ ghi nhận thành tích, không dùng làm tài sản đặt cược, không mua bán hoặc quy đổi.

Trọng tâm nghiệm thu là kết nối TCP, giao thức ứng dụng, nhiều client hoạt động đồng thời, đồng bộ trạng thái và xử lý ngắt kết nối. Giao diện và chức năng dữ liệu phải đủ để trình diễn trọn luồng sử dụng. Các yêu cầu trong tài liệu này là quy ước triển khai của nhóm, không phải tuyên bố về thang điểm của giảng viên.

**Cả nhóm thống nhất một bộ công nghệ và cách tổ chức dự án.** Các lựa chọn bổ sung dưới đây là mặc định để bắt đầu làm việc; nếu cần đổi thì thực hiện theo quy tắc thay đổi ở cuối tài liệu.

| Thành phần | Quy ước triển khai |
|---|---|
| Ngôn ngữ, build | Java 21; Maven quản lý build và thư viện. IDE do từng người chọn nhưng project phải build được ngoài IDE. |
| Client | Java Swing; tách View, Controller và phần giao tiếp mạng. Chạy dưới dạng ứng dụng riêng trên máy tính. |
| Kết nối | TCP qua `Socket` và `ServerSocket`; một kết nối được duy trì trong phiên sử dụng. |
| Thông điệp | `Packet` và payload dùng `Serializable`, truyền bằng `ObjectOutputStream`/`ObjectInputStream`. |
| Server | Một chương trình Java gồm mạng, quản lý phòng, nghiệp vụ game và nghiệp vụ dữ liệu. Phần của Nhật không phải một web server riêng. |
| CSDL | PostgreSQL và JDBC trực tiếp; tách BUS xử lý nghiệp vụ, DAL thực thi truy vấn. |
| Dữ liệu chung | Module `shared` chứa giao thức và các cấu trúc dữ liệu cần trao đổi. Client và server dùng cùng phiên bản. |
| Phạm vi bản đầu | Đăng ký/đăng nhập, phòng chờ, ván chơi cơ bản, chat văn bản, xử lý rời phòng/mất kết nối, lưu kết quả, lịch sử, bảng xếp hạng, hồ sơ cơ bản. |
| Phần làm sau | TLS, reconnect kèm khôi phục phiên, emoji/sticker, công cụ quản trị. Chỉ bổ sung sau khi các mốc lõi đã đạt. |

Không tự thêm JWT, JPA, web frontend, framework mới hoặc dịch vụ ngoài vào nhánh tích hợp. Đề xuất thay đổi cần nêu rõ vấn đề đang giải quyết, phần code bị ảnh hưởng và cách kiểm tra; nhóm thống nhất trước khi áp dụng.

**Trách nhiệm chính được giữ theo phân công cũ.** Người phụ trách module chịu trách nhiệm giải thích, sửa lỗi và bàn giao module đó; người phối hợp hỗ trợ thống nhất đầu vào/đầu ra và ghép chức năng.

| Thành viên | Chịu trách nhiệm chính | Phối hợp trực tiếp |
|---|---|---|
| Thành | UI, luồng thao tác và mạng phía client | Khánh về giao thức; Trọng về dữ liệu hiển thị; Nhật về tài khoản và dữ liệu tra cứu |
| Trọng | Logic game và trạng thái ván | Khánh về điều phối; Nhật về dữ liệu kết quả |
| Khánh | Mạng phía server, kết nối, phòng và điều phối | Thành về gửi/nhận; Trọng về Game Engine; Nhật về BUS |
| Nhật | Tài khoản, nghiệp vụ lưu trữ, MySQL và JDBC | Khánh về handler; Trọng về kết quả; Thành về các màn hình dữ liệu |

**Thành chịu trách nhiệm để client hoạt động đầy đủ và phản ánh đúng trạng thái server.** Không thực hiện truy vấn MySQL hoặc quyết định kết quả game ở phía client.

| Module | Yêu cầu phải thực hiện | Đầu ra và tiêu chí nghiệm thu |
|---|---|---|
| T01 — Kết nối client | Cho nhập hoặc cấu hình IP/cổng; kết nối có timeout; mỗi socket quản lý một bộ Object Stream; có một luồng nhận liên tục và đường gửi thống nhất; đóng tài nguyên khi thoát. | Kết nối được server, gửi/nhận nhiều thông điệp trên cùng kết nối; báo rõ khi kết nối thất bại; không khởi tạo thêm socket cho từng nút bấm. |
| T02 — Tài khoản và hồ sơ | Làm đăng ký, đăng nhập, đăng xuất, xem/sửa tên hiển thị và đổi mật khẩu; kiểm tra dữ liệu đầu vào cơ bản; phản hồi đúng kết quả server; không lưu mật khẩu vào log hoặc file. | Có thể thực hiện từ UI đến server; hiển thị được lỗi tên tài khoản trùng, thông tin đăng nhập sai và dữ liệu không hợp lệ. |
| T03 — Lobby và phòng chờ | Hiển thị danh sách phòng và số người; tạo, tham gia, rời phòng; hiển thị chủ phòng và trạng thái sẵn sàng; cập nhật khi server thông báo. | Hai client thấy cùng trạng thái phòng; phòng đầy bị từ chối rõ ràng; nút thao tác phù hợp trạng thái hiện tại. |
| T04 — Màn hình ván chơi | Hiển thị dữ liệu riêng được server gửi, thông tin công khai, trạng thái/lượt nếu luật game có, kết quả và lựa chọn chơi tiếp/rời phòng. Chỉ gửi ý định thao tác lên server. | Chạy được toàn bộ luồng phòng chờ → ván chơi → kết quả → quay lại phòng chờ; không tự suy diễn dữ liệu riêng của người khác. |
| T05 — Chat và thông báo | Chat văn bản theo phòng; giới hạn độ dài ở UI theo giao thức; hiển thị tên người gửi, thời gian server và thông báo hệ thống. | Tin nhắn xuất hiện đúng phòng; nội dung được hiển thị như văn bản, không thực thi hoặc diễn giải thành nội dung điều khiển giao diện. |
| T06 — Lịch sử và bảng xếp hạng | Gửi yêu cầu tra cứu và hiển thị dữ liệu trả về; lịch sử có phân trang; có trạng thái đang tải, danh sách rỗng và lỗi. | Đối chiếu được dữ liệu hiển thị với dữ liệu Nhật cung cấp; không dùng dữ liệu mẫu trong luồng demo chính thức. |
| T07 — Luồng UI và lỗi mạng | Đọc socket ngoài EDT; cập nhật Swing trên EDT; thao tác đang chờ phản hồi có trạng thái rõ; khi mất kết nối phải vô hiệu hóa thao tác cần server. | Chờ server không làm cửa sổ bị đơ; mất kết nối không để người dùng tiếp tục thao tác trên trạng thái đã hết hiệu lực. |

Thành bàn giao `RunClient`, View/Controller, phần giao tiếp mạng phía client, tài nguyên giao diện và hướng dẫn cấu hình/chạy. Dữ liệu giả được phép dùng để làm UI trước nhưng phải đặt trong chế độ riêng, dễ nhận biết và được tắt khi nghiệm thu tích hợp.

**Trọng chịu trách nhiệm về tính đúng của game và khả năng kiểm tra độc lập.** Game Engine không mở socket, không tạo cửa sổ Swing và không truy cập CSDL.

| Module | Yêu cầu phải thực hiện | Đầu ra và tiêu chí nghiệm thu |
|---|---|---|
| G01 — Luật và mô hình game | Viết quy tắc cho game thẻ bài giải trí, cách xác định kết quả và điểm thành tích; mô tả điều kiện bắt đầu/kết thúc, xử lý hòa, người rời ván và ván bị hủy. Chốt quy tắc trước khi triển khai sâu. | `Game_Rules.md`, các mô hình nội bộ và bộ ví dụ đầu vào/đầu ra đủ để các thành viên hiểu cùng một cách. |
| G02 — Khởi tạo và dữ liệu ván | Tạo `roundId`, danh sách tham gia và trạng thái đầu; quản lý dữ liệu game tại server; xác định rõ dữ liệu nào công khai và dữ liệu nào chỉ dành cho từng người. | Có thể tạo ván bằng chương trình kiểm thử; dữ liệu hợp lệ, không bị lẫn với ván trước. |
| G03 — Xử lý hành động | Nhận người thực hiện đã xác thực, hành động và định danh ván; kiểm tra trạng thái, quyền thao tác, lượt nếu có và điều kiện luật; trả cập nhật hoặc lỗi có mã. | Hành động hợp lệ làm thay đổi đúng trạng thái; hành động sai người, sai ván hoặc sai giai đoạn bị từ chối và không thay đổi trạng thái. |
| G04 — Kết quả và vòng đời | Quản lý các trạng thái `WAITING`, `PLAYING`, `FINISHED`, `CANCELLED`; tạo kết quả ổn định khi kết thúc. Quy tắc cộng điểm thống nhất với Nhật. | Một ván chỉ chốt một kết quả; không cộng điểm cho ván hủy; một yêu cầu tới muộn không làm ván đã kết thúc thay đổi kết quả. |
| G05 — Rời ván và dữ liệu gửi ra | Cung cấp thao tác xử lý người rời ván; khi không đủ điều kiện tiếp tục thì hủy ván có lý do; tạo bản chụp trạng thái công khai và bản riêng của người nhận. | Không rò rỉ dữ liệu bí mật qua kết quả trung gian hoặc snapshot; thao tác rời ván cho kết quả nhất quán theo `Game_Rules.md`. |
| G06 — Kiểm thử và API ghép | Công bố các hàm mà Khánh gọi, tham số, kết quả, lỗi và cách gọi đúng thứ tự; kiểm thử luật, chuyển trạng thái và các tình huống biên quan trọng. | Game Engine chạy thử không cần mạng/UI/DB; bàn giao được danh sách trường hợp đã kiểm tra và kết quả mong đợi. |

Trọng bàn giao Game Engine, mô hình nội bộ, các cấu trúc kết quả phục vụ trao đổi, tài liệu luật và kiểm thử. Khánh chịu trách nhiệm để các lời gọi cập nhật một phòng được xử lý tuần tự; Trọng phải ghi rõ giả định này và không tự tạo thêm luồng thay đổi trạng thái ngầm.

**Khánh chịu trách nhiệm để server phục vụ nhiều client và điều phối đúng các module.** Khánh quản lý giao thức dùng chung nhưng không tự viết lại logic game của Trọng hoặc nghiệp vụ CSDL của Nhật.

| Module | Yêu cầu phải thực hiện | Đầu ra và tiêu chí nghiệm thu |
|---|---|---|
| N01 — Khởi động và đa kết nối | Mở cổng cấu hình được, lặp nhận kết nối, tạo `ClientHandler`; sử dụng mô hình luồng có giới hạn kết nối được công bố; dừng server và đóng tài nguyên có kiểm soát. | Nhiều client được phục vụ đồng thời; một client lỗi không làm vòng nhận kết nối dừng; từ chối kết nối vượt giới hạn rõ ràng. |
| N02 — Giao thức và Object Stream | Quản lý `Packet`, `PacketType`, payload dùng chung; thống nhất khởi tạo stream, gửi, đọc, flush và giới hạn dữ liệu; kiểm tra loại đối tượng nhận được. | Client/server hiểu cùng một định dạng; thông điệp lỗi bị xử lý mà không làm hỏng các kết nối khác. |
| N03 — Xác thực kết nối | Gọi `UserBUS` của Nhật khi đăng nhập; chỉ gắn `userId` vào kết nối sau khi thành công; chỉ cho một phiên hoạt động trên mỗi tài khoản ở bản đầu; từ chối phiên đăng nhập mới nếu phiên cũ còn hoạt động. | Người chưa đăng nhập không được dùng chức năng cần xác thực; không tin `userId` do client tự khai để xác định người thực hiện. |
| N04 — Phòng và quyền thao tác | Tạo, tham gia, rời phòng, sẵn sàng, bắt đầu và quay lại phòng chờ; giới hạn 2–8 người/phòng; một người thuộc tối đa một phòng; chỉ chủ phòng được yêu cầu bắt đầu. | Hai yêu cầu cùng tranh chỗ cuối không làm phòng vượt sức chứa; các điều kiện được server kiểm tra kể cả khi UI bị bỏ qua. |
| N05 — Điều phối game và gửi dữ liệu | Chuyển yêu cầu hợp lệ cho Game Engine; tuần tự hóa cập nhật theo từng phòng; tạo thông báo theo đúng thứ tự trạng thái; gửi riêng/broadcast đúng phạm vi. | Hai phòng hoạt động độc lập; client chỉ nhận dữ liệu mà mình có quyền xem; thứ tự cập nhật phòng không bị đảo do nhiều luồng gửi. |
| N06 — Heartbeat và ngắt kết nối | Thống nhất PING/PONG và timeout cấu hình được; heartbeat chạy nền kể cả khi người dùng không thao tác; xử lý EOF, lỗi socket, logout và leave qua đường dọn trạng thái thống nhất. | Rút mạng hoặc đóng client được phát hiện; không nhầm im lặng của người dùng với mất kết nối; không phát thông báo rời phòng nhiều lần cho cùng một lần rời. |
| N07 — Chat theo phòng | Kiểm tra đã đăng nhập và thuộc phòng, giới hạn độ dài và tần suất; lấy tên người gửi từ phiên, lấy thời gian từ server; phát thông báo hệ thống. | Chat không đi sang phòng khác; client không giả tên người khác; tin sai định dạng bị từ chối. Bản đầu không lưu lịch sử chat lâu dài. |
| N08 — Ghép dữ liệu và theo dõi lỗi | Gọi BUS của Nhật để lưu kết quả và truy vấn; giữ nguyên `roundId` khi thử lưu lại; log các mốc kết nối, xác thực, phòng, ván và lỗi. | Có thể lần theo một yêu cầu trong log; lỗi DB không làm toàn server dừng hoặc âm thầm tạo kết quả trùng. |

Khánh bàn giao `RunServer`, quản lý kết nối/phòng, bộ `shared`, `Protocol.md`, cấu hình mẫu và hướng dẫn chạy nhiều client. Không broadcast hoặc ghi log nguyên đối tượng Game Engine vì có thể chứa dữ liệu riêng.

**Nhật chịu trách nhiệm về tài khoản, tính nhất quán dữ liệu và khả năng khởi tạo CSDL trên máy khác.** Mọi truy vấn MySQL chỉ diễn ra ở server.

| Module | Yêu cầu phải thực hiện | Đầu ra và tiêu chí nghiệm thu |
|---|---|---|
| D01 — Schema và khởi tạo | Thiết kế bảng tài khoản, ván và người tham gia/kết quả; khóa ngoại, ràng buộc duy nhất và chỉ mục theo truy vấn; cung cấp script và dữ liệu mẫu. | Một máy mới tạo được CSDL theo hướng dẫn; không cần thao tác sửa bảng thủ công ngoài tài liệu. |
| D02 — JDBC và DAL | Kết nối qua cấu hình, dùng `PreparedStatement`, đóng tài nguyên; quy định vòng đời connection; các nghiệp vụ đồng thời không dùng chung tùy tiện một JDBC connection. | Truy vấn chạy đúng; lỗi kết nối có thông báo; không ghép trực tiếp đầu vào người dùng vào câu SQL. |
| D03 — Tài khoản và xác thực | Đăng ký, xác thực, xem/sửa tên hiển thị, đổi mật khẩu; thống nhất quy tắc username và mật khẩu; hash mật khẩu bằng hàm phù hợp như PBKDF2 với salt riêng và tham số cấu hình rõ. | Username trùng bị từ chối kể cả khi đăng ký đồng thời; mật khẩu không lưu nguyên văn; payload trả ra không chứa mật khẩu hoặc hash. |
| D04 — Lưu kết quả và thống kê | Nhận kết quả đã chốt từ server; lưu ván, người tham gia và cập nhật thống kê trong transaction; chống ghi lặp bằng định danh ván và ràng buộc DB. | Lưu cùng một kết quả hai lần không tăng thống kê hai lần; lỗi giữa chừng rollback; lỗi bị báo lại cho phần điều phối. |
| D05 — Lịch sử và bảng xếp hạng | Lịch sử giới hạn theo tài khoản đã xác thực, có phân trang; leaderboard sắp theo điểm thành tích giảm dần, số lần thắng giảm dần, rồi ID tăng dần để ổn định. | Dữ liệu đúng người, thứ tự ổn định, xử lý trang rỗng; không cho client tự đổi ID để đọc lịch sử riêng của người khác. |
| D06 — Hợp đồng BUS và kiểm thử | Công bố hàm BUS cho Khánh, kiểu tham số/kết quả và mã lỗi; cung cấp dữ liệu kiểm thử và quy trình khôi phục môi trường. | Chạy được kiểm tra tài khoản, lưu kết quả và truy vấn chưa cần Swing; có hướng dẫn để người khác tự thực hiện lại. |

Nhật bàn giao script SQL, sơ đồ dữ liệu đơn giản, `Database.md`, cấu hình mẫu, BUS/DAL và kết quả kiểm thử. Đối tượng JDBC, exception nội bộ và thông tin kết nối DB không được gửi cho client.

**Module dùng chung là hợp đồng của cả nhóm.** Khánh quản lý việc tích hợp `shared`; Trọng cung cấp dữ liệu game cần trao đổi, Nhật cung cấp dữ liệu tài khoản/lịch sử và Thành kiểm tra chúng có đủ cho UI. Không ai tự đổi hợp đồng rồi yêu cầu các phần còn lại sửa theo mà chưa thông báo.

| Hạng mục chung | Nội dung cần chốt | Người chịu trách nhiệm cập nhật |
|---|---|---|
| Cấu trúc `Packet` | `protocolVersion`, `type`, `requestId` và `data`; request cần phản hồi có ID để đối chiếu; phản hồi giữ ID; thông báo chủ động từ server được phân biệt với phản hồi. | Khánh |
| Payload | Mỗi loại thông điệp có cấu trúc và ví dụ; quy định trường bắt buộc, trường có thể rỗng, kiểu dữ liệu và giới hạn kích thước. Không chỉ ghi `Object data` rồi để mỗi bên tự đoán. | Khánh tổng hợp từ từng người |
| Snapshot phòng/ván | Có `roomId`, `roundId` khi liên quan và `stateVersion` tăng khi trạng thái thay đổi; phân biệt bản công khai và bản theo người nhận. | Khánh + Trọng |
| Phản hồi lỗi | Có mã lỗi ổn định và thông báo phù hợp cho người dùng; không trả stack trace. Ghi rõ yêu cầu bị lỗi có làm thay đổi trạng thái hay không. | Khánh + Nhật + Trọng |
| Dữ liệu lưu kết quả | Một `roundId` duy nhất từ khi tạo đến khi lưu; danh sách người tham gia, kết quả, điểm thành tích và thời gian server; thêm trạng thái hủy nếu có. | Trọng + Nhật |
| Phiên bản | Client/server phải dùng cùng phiên bản `shared`; mọi thay đổi có ảnh hưởng phải cập nhật tài liệu và kiểm tra hai phía. | Khánh |

`Protocol.md` phải mô tả ít nhất các nhóm thông điệp dưới đây. Danh sách là hợp đồng triển khai v1; nếu đổi tên thì đổi đồng bộ trong tài liệu và hai phía.

| Chức năng | Client gửi | Server trả hoặc thông báo | Quyền và yêu cầu bắt buộc |
|---|---|---|---|
| Tài khoản | `REGISTER`, `LOGIN`, `LOGOUT` | `AUTH_RESULT`, `ACK` hoặc `ERROR` | Thành công/thất bại rõ; không gửi hash mật khẩu về client. |
| Hồ sơ | `GET_PROFILE`, `UPDATE_PROFILE`, `CHANGE_PASSWORD` | `PROFILE_RESULT`, `ACK` hoặc `ERROR` | Chỉ thao tác tài khoản của kết nối đã xác thực; đổi mật khẩu phải xác minh thông tin cần thiết phía server. |
| Danh sách phòng | `ROOM_LIST` | `ROOM_LIST_RESULT` | Chỉ trả dữ liệu công khai; không chứa trạng thái bí mật của ván. |
| Tham gia phòng | `CREATE_ROOM`, `JOIN_ROOM`, `LEAVE_ROOM` | `ACK`, `ROOM_UPDATED` hoặc `ERROR` | Có phản hồi riêng cho người yêu cầu và cập nhật đúng phòng cho người liên quan. |
| Chuẩn bị ván | `SET_READY`, `START_GAME_REQUEST` | `ROOM_UPDATED`, `START_GAME` hoặc `ERROR` | Server kiểm tra chủ phòng, số người và điều kiện sẵn sàng. |
| Trong ván | `GAME_ACTION` | `GAME_UPDATED`, `PLAYER_STATE` hoặc `ERROR` | Kiểm tra người thực hiện và ván hiện hành; `PLAYER_STATE` chỉ gửi cho chủ dữ liệu. |
| Kết thúc | `PLAY_AGAIN_VOTE` khi ván đã kết thúc | `ROUND_RESULT`, `ROOM_UPDATED` | Server chốt kết quả; quay lại phòng chờ và kiểm tra điều kiện trước khi tạo ván mới. |
| Chat | `CHAT_MESSAGE` | `CHAT_MESSAGE`, `CHAT_SYSTEM` hoặc `ERROR` | Server xác định người gửi, phòng và thời gian; giới hạn nội dung. |
| Dữ liệu | `GET_HISTORY`, `GET_LEADERBOARD` | `HISTORY_RESULT`, `LEADERBOARD_RESULT` hoặc `ERROR` | Kiểm tra phân trang, giới hạn số bản ghi và quyền truy cập. |
| Kiểm tra kết nối | `PONG` phản hồi nhịp server | `PING` | Mốc phản hồi được theo dõi ở server; không phụ thuộc thao tác bàn phím/chuột. |

Mã lỗi tối thiểu gồm `INVALID_REQUEST`, `UNAUTHENTICATED`, `FORBIDDEN`, `USERNAME_EXISTS`, `INVALID_CREDENTIALS`, `ALREADY_LOGGED_IN`, `ROOM_NOT_FOUND`, `ROOM_FULL`, `INVALID_STATE`, `STALE_ROUND` và `INTERNAL_ERROR`. Các mã mới được thêm khi có tình huống sử dụng cụ thể; UI không dựa vào việc so sánh nguyên câu thông báo lỗi.

**Các quy tắc xử lý dữ liệu và kết nối sau đây áp dụng cho mọi module.** Đây là điều kiện ghép và nghiệm thu, không chỉ là khuyến nghị.

1. Server là nguồn quyết định trạng thái, quyền thao tác, kết quả và thời gian nghiệp vụ. Kiểm tra ở UI chỉ hỗ trợ người dùng; server luôn kiểm tra lại.
2. Client gửi ý định thao tác. Danh tính người thực hiện được lấy từ kết nối đã xác thực, không lấy theo `userId` tùy ý trong payload.
3. Phòng và ván đang chạy được quản lý trong bộ nhớ server. CSDL lưu tài khoản, lịch sử và thống kê. Không truy vấn DB liên tục để thay thế việc đồng bộ phòng qua socket.
4. Một người chỉ ở một phòng và có một phiên đăng nhập hoạt động trong bản đầu. Người mới chỉ được vào phòng đang chờ; khán giả và tham gia giữa ván chưa thuộc phạm vi.
5. Khi chủ phòng rời ở trạng thái chờ, chuyển quyền cho người còn lại có thời điểm tham gia sớm nhất. Phòng không còn người được xóa khỏi bộ nhớ và danh sách phòng.
6. Mất kết nối hoặc rời khi đang chơi phải được chuyển thành sự kiện cho Game Engine. Nếu không đủ người tiếp tục thì hủy ván có lý do; việc xử lý trường hợp còn đủ người tuân theo `Game_Rules.md`. Lịch sử tham gia của ván không bị xóa tùy tiện.
7. Dữ liệu riêng chỉ có trong payload gửi đúng người nhận. Không gửi toàn bộ trạng thái server rồi chỉ ẩn bớt ở giao diện. Không đặt deck, JDBC connection, socket hoặc đối tượng quản lý luồng trong payload dùng chung.
8. Chỉ một luồng đọc trên mỗi kết nối. Có một đường gửi thống nhất, bảo đảm nhiều nguồn gửi không ghi xen kẽ lên cùng Object Stream. Không tạo Object Stream mới cho từng thông điệp.
9. Hai phía khởi tạo Output Stream, flush phần đầu rồi tạo Input Stream theo quy ước thống nhất. Payload phải có thể serialize; các lớp dùng chung có phiên bản tương thích. Việc gửi lại đối tượng có thay đổi phải dùng snapshot độc lập và chiến lược reset Object Stream nhất quán để không nhận nhầm dữ liệu cũ.
10. Giới hạn lớp được giải tuần tự hóa và kích thước/độ sâu đối tượng; kiểm tra kiểu payload theo loại thông điệp. Kết nối gửi dữ liệu không hợp lệ có thể bị đóng nhưng không làm dừng server.
11. Cập nhật cùng một phòng phải được tuần tự hóa. Không giữ khóa phòng trong lúc chờ đọc socket, truy vấn DB hoặc gửi tới client chậm. Tạo snapshot cùng phiên bản trong lúc cập nhật rồi chuyển qua đường gửi có thứ tự; client chậm không được chặn các phòng khác.
12. Gói từ ván cũ hoặc cập nhật cũ không được áp lên ván hiện tại. Các thao tác bắt đầu/kết thúc/lưu kết quả phải kiểm tra trạng thái và định danh để không bị thực hiện hai lần khi người dùng nhấn lặp.
13. Client nhận sự kiện mạng qua luồng nền, cập nhật Swing qua EDT. Không thực hiện đọc mạng chặn hoặc truy vấn DB trong sự kiện nút bấm.
14. Số điểm và thống kê lấy từ kết quả server, được Nhật cập nhật bằng transaction. Ràng buộc duy nhất phải ngăn ghi lặp cùng một người trong cùng một ván.
15. Nếu lưu kết quả lỗi, server giữ trạng thái chờ lưu, báo rõ và không mở ván mới của phòng đó cho đến khi giải quyết. Thử lại với cùng `roundId`; không tạo kết quả khác để né lỗi. Khởi động lại server giữ dữ liệu đã commit, nhưng bản đầu không cam kết phục hồi ván hoặc kết quả chưa lưu trong RAM.
16. JWT, tự reconnect và phục hồi phiên không thuộc bản đầu. Khi mất kết nối, client hiển thị rõ trạng thái và cho kết nối/đăng nhập lại; không tự coi phiên trước vẫn còn hiệu lực.

**Cấu hình và giới hạn phải thống nhất, không rải hằng số ở nhiều nơi.** Các giá trị dưới đây là mặc định cho bản demo và có thể điều chỉnh khi kiểm thử có lý do.

| Tham số | Mặc định và yêu cầu |
|---|---|
| Địa chỉ/cổng | Client cấu hình được host; server dùng cổng 8888 mặc định, cho phép thay đổi. Không gắn IP máy cá nhân vào mã nguồn dùng chung. |
| Số người/phòng | Từ 2 đến 8; kiểm tra ở server. Chỉ bắt đầu khi đủ số người tối thiểu và các thành viên đủ điều kiện sẵn sàng. |
| Timeout kết nối client | 5 giây; thất bại phải trả quyền điều khiển cho UI. |
| Heartbeat | Server gửi mỗi 5 giây, coi mất kết nối sau 15 giây không nhận phản hồi hợp lệ; cấu hình được. Khánh chịu trách nhiệm kiểm tra không ngắt nhầm khi hệ thống bận. |
| Chat | Tối đa 500 ký tự/tin, tối đa 5 tin mỗi 10 giây trên một kết nối; vượt giới hạn có phản hồi rõ. |
| Phân trang | 20 bản ghi/trang mặc định, tối đa 100; từ chối chỉ số trang âm hoặc kích thước không hợp lệ. |
| Dữ liệu chuỗi | Thống nhất UTF-8 cho mã nguồn/tài liệu và UTF-8 cho PostgreSQL (mặc định khi tạo DB với `ENCODING 'UTF8'`); kiểm thử tên và tin nhắn tiếng Việt. |
| Tài nguyên mạng | Công bố giới hạn kết nối và hàng đợi gửi; vượt giới hạn phải từ chối hoặc ngắt client chậm có thông báo/log, không tăng bộ nhớ vô hạn. |

**Cấu trúc dự án phải đơn giản và có chủ sở hữu rõ.** Dùng một repository với ba module Maven `shared`, `server`, `client`; thư viện dùng chung khai báo tập trung. Tên package viết thường. BUS và DAL là trách nhiệm cụ thể trong server, không phải lý do để thêm nhiều lớp chỉ chuyển tiếp lời gọi.

| Khu vực | Nội dung | Người phụ trách tích hợp |
|---|---|---|
| `shared` | `Packet`, enum, request/response, snapshot trao đổi | Khánh; thay đổi có người liên quan xem lại |
| `client` | `RunClient`, View, Controller, kết nối client, tài nguyên UI | Thành |
| `server/network`, `server/room` | Kết nối, handler, phiên người dùng, phòng, chat | Khánh |
| `server/game` | Luật, trạng thái, kết quả và kiểm thử game | Trọng |
| `server/bus`, `server/dal` | Nghiệp vụ tài khoản/dữ liệu và JDBC | Nhật |
| `database` | Script schema, dữ liệu mẫu và script nâng cấp khi có | Nhật |
| `docs` | Giao thức, luật, CSDL, hướng dẫn chạy và nghiệm thu | Mỗi người cập nhật phần mình; Khánh phối hợp ghép |

Trong luồng đăng nhập, Thành tạo yêu cầu; Khánh nhận và gọi BUS; Nhật xác thực và trả kết quả; Khánh gắn phiên vào kết nối rồi trả phản hồi; Thành hiển thị. Trong luồng game, Khánh kiểm tra quyền và điều phối; Trọng cập nhật logic và trả snapshot/kết quả; Khánh gửi đúng người và gọi Nhật để lưu khi cần. Thành chỉ cập nhật màn hình theo dữ liệu nhận được.

**Cả nhóm phải ghép theo từng mốc, không chờ đến cuối mới tích hợp.** Chưa có hạn nộp trong thông tin hiện tại nên tài liệu dùng mốc phụ thuộc thay cho ngày tự đặt. Trước khi bắt đầu một mốc, người phụ trách ghi ngày dự kiến và tình trạng vào bảng tiến độ chung.

| Mốc | Kết quả phải có | Phân công và điều kiện qua mốc |
|---|---|---|
| M0 — Chuẩn chung | Project build được, phiên bản công cụ/config mẫu, giao thức khởi đầu, dữ liệu mẫu và luật game thống nhất | Cả nhóm; Khánh tổng hợp. Chưa cần UI hoàn chỉnh hoặc DB đầy đủ. |
| M1 — Kết nối | Client kết nối, gửi yêu cầu thử, nhận phản hồi và nhận một sự kiện server chủ động gửi | Thành + Khánh; chạy liên tiếp nhiều thông điệp, không chỉ gửi được một lần. |
| M2 — Tài khoản | Đăng ký/đăng nhập từ Swing tới MySQL | Thành + Khánh + Nhật; kiểm tra thành công, sai thông tin, trùng tên, tài khoản đã online. |
| M3 — Phòng và chat | Nhiều client tạo/tham gia/rời phòng, sẵn sàng và chat | Thành + Khánh; hai phòng không lẫn dữ liệu, tranh chỗ cuối và chủ phòng rời được xử lý. |
| M4 — Game | Bắt đầu, chạy, kết thúc và chơi tiếp đúng luật | Thành + Khánh + Trọng; có kiểm tra dữ liệu riêng, lệnh sai trạng thái và người rời giữa ván. |
| M5 — Dữ liệu đầy đủ | Lưu kết quả, lịch sử, bảng xếp hạng và hồ sơ | Cả nhóm; kết quả không ghi trùng, dữ liệu đã lưu còn sau khi restart server. |
| M6 — LAN và bàn giao | Đóng gói, hướng dẫn và kịch bản demo | Cả nhóm; ít nhất 4 client chia 2 phòng, chạy trên ít nhất 2 máy trong cùng mạng, đồng thời kiểm tra một phòng tới giới hạn 8 người. |

Thành làm UI bằng dữ liệu mẫu sau M0; Trọng kiểm thử engine độc lập; Nhật dựng DB và BUS/DAL độc lập; Khánh dựng mạng và phòng. Không dùng lý do chưa có module khác để dừng toàn bộ phần có thể làm độc lập. Phần còn thiếu phải có mô tả hợp đồng hoặc dữ liệu mẫu để người còn lại tiếp tục làm.

**Quy tắc làm việc với Git và thay đổi giao diện module được áp dụng từ đầu.** Nhánh tích hợp chính là `main`; mỗi phần việc dùng nhánh riêng theo module, ví dụ `feature/T03-lobby`. Chỉ ghép thay đổi đã build được, có mô tả và được ít nhất một người liên quan xem lại.

- Mỗi commit có phạm vi rõ, ghi được đã sửa gì. Không đẩy file build, cache IDE, mật khẩu DB, khóa bí mật hoặc cấu hình chứa thông tin riêng lên repository.
- Trước khi sửa module của người khác, thông báo phần cần sửa và lý do; người sở hữu cùng rà soát. Không tự đổi tên API, payload hoặc cột DB mà bỏ qua các bên sử dụng.
- Thay đổi `shared` phải cập nhật `Protocol.md`, ví dụ thông điệp và cả hai phía. Thay đổi schema phải có script hoặc hướng dẫn nâng cấp, không chỉ sửa DB trên máy cá nhân.
- Không dùng force push lên nhánh tích hợp chung. Khi xung đột, trao đổi với người sở hữu phần bị ảnh hưởng, không xóa code để hết conflict mà chưa hiểu tác dụng.
- Code phải có tên rõ và trách nhiệm hợp lý. Không thêm framework hoặc pattern chỉ để có tên trong báo cáo. Có thể dùng AI hỗ trợ, nhưng người bàn giao phải hiểu và giải thích được luồng chạy, giả định và lỗi có thể xảy ra.
- Mỗi ngày có làm việc, cập nhật một lần: module đang làm, kết quả đã chạy được, phần tiếp theo, vướng mắc, người cần phối hợp và thời điểm dự kiến hoàn thành. Báo sớm khi không đạt mốc; không chờ tới lúc ghép mới thông báo.
- Mọi thay đổi phạm vi cần ghi vấn đề, phương án, ảnh hưởng và quyết định vào `docs/decisions.md`. Các phần mở rộng không được làm chậm việc hoàn thành mốc lõi.

**Một module chỉ được đánh dấu hoàn thành khi đủ các bằng chứng sau.** Người nhận bàn giao phải có thể tái hiện mà không cần tác giả ngồi sửa trực tiếp trên máy của mình.

1. Code nằm trong repository, build được theo hướng dẫn chung và không phụ thuộc đường dẫn riêng của máy tác giả.
2. Có mô tả chức năng, đầu vào/đầu ra, điều kiện gọi, mã lỗi và người sử dụng module.
3. Có cấu hình mẫu, dữ liệu thử, bước chạy và kết quả mong đợi; ghi rõ điều kiện môi trường.
4. Đã kiểm tra luồng thành công, dữ liệu không hợp lệ và lỗi liên quan thực tế đến module. Các lỗi logic/trạng thái, đồng thời và ghi kết quả cần kiểm thử có thể chạy lại; UI đơn giản có thể dùng kịch bản thao tác.
5. Đã ghép ít nhất một luồng với module liên quan; ghi commit hoặc phiên bản đã kiểm tra.
6. Có ảnh, log hoặc kết quả kiểm thử chứng minh hoạt động, không chứa mật khẩu hay dữ liệu riêng không cần thiết.
7. Có danh sách lỗi còn tồn tại và giới hạn đã biết. Chưa xử lý xong lỗi cản luồng chính thì ghi “chưa đạt”, không ghi “hoàn thành cơ bản”.

**Bộ bàn giao cuối của nhóm phải đủ để một máy mới chạy lại được dự án.** Thành bàn giao bản client và hướng dẫn thao tác; Khánh bàn giao bản server, cấu hình mạng và giao thức; Nhật bàn giao schema/dữ liệu mẫu/hướng dẫn DB; Trọng bàn giao luật, engine và kiểm thử. Cả nhóm hoàn thiện README với thứ tự tạo DB → chạy server → chạy client, cách cấu hình IP/cổng và kịch bản demo.

Kịch bản nghiệm thu cuối phải có: đăng ký/đăng nhập; hai phòng hoạt động đồng thời; bắt đầu và kết thúc game; chat đúng phòng; client thoát đột ngột; thao tác sai trạng thái; phòng đầy; mất kết nối DB khi lưu kết quả; gửi lặp một kết quả; lịch sử và xếp hạng sau khi khởi động lại server. Phần đạt, chưa đạt và cách tái hiện đều phải được ghi lại.

**Phân công báo cáo và bảo vệ gắn với module thực tế.** Thành giải thích client/Swing/luồng cập nhật giao diện; Trọng giải thích luật và trạng thái; Khánh giải thích socket/giao thức/đa luồng/đồng bộ; Nhật giải thích JDBC/transaction/schema/xác thực. Mỗi người phải trình bày được ít nhất một yêu cầu đi trọn từ thao tác người dùng tới phản hồi server, đồng thời chỉ rõ đoạn mình thực hiện.

Công việc đầu tiên của cả nhóm là hoàn thành M0: Khánh tạo khung project và bản giao thức khởi đầu; Thành liệt kê dữ liệu cần cho từng màn hình; Trọng chốt luật game giải trí và cấu trúc kết quả; Nhật chốt schema cùng hợp đồng BUS. Sau khi thống nhất các điểm này, mọi người triển khai song song và đưa kết quả lên nhánh tích hợp theo từng mốc.
