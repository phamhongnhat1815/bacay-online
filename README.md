# 🃏 Game 3 Cây - Lập Trình Mạng Multi-Player

Hệ thống game 3 cây nhiều người chơi theo thời gian thực (Real-time Multiplayer Game) sử dụng kiến trúc **Client - Server** qua giao thức **Raw TCP Socket**.

---

## 🏗️ Cấu Trúc Dự Án (Project Structure)

Dự án được tổ chức theo dạng Monorepo gồm 2 module độc lập: `server` (Backend) và `client` (Frontend Swing).

```text
game-3-cay/
├── .gitignore               # Cấu hình bỏ qua các file build/rác khi commit Git
├── README.md                # Tài liệu hướng dẫn & mô tả dự án
│
├── server/                  # Backend Module (Spring Boot + JDBC + Raw Socket)
│   ├── pom.xml              # Quản lý phụ thuộc Maven của Server (Spring JDBC, MySQL, JJWT, Jackson)
│   └── src/
│       ├── main/
│       │   ├── java/com/game3cay/
│       │   │   ├── ServerApplication.java  # Class khởi chạy ứng dụng Spring Boot & Socket Server
│       │   │   ├── config/                 # Cấu hình Spring Security (BCrypt), AppConfig
│       │   │   ├── controller/             # Xử lý & điều hướng Message từ Socket Client
│       │   │   ├── model/                  # Đĩnh nghĩa các Entity (User, Card, Room, GameMatch)
│       │   │   ├── repository/             # Tương tác CSDL bằng Spring JdbcTemplate (DAO)
│       │   │   ├── service/                # Business Logic (Tính điểm 3 cây, Quản lý phòng, Auth)
│       │   │   ├── socket/                 # ServerSocket & ClientHandler (Quản lý đa tiến trình Client)
│       │   │   └── util/                   # Utility classes (JwtUtil, CardUtil...)
│       │   └── resources/
│       │       ├── application.yaml  # Cấu hình kết nối Database (MySQL, Port, JWT Secret)
│       │       └── schema.sql              # Script tạo bảng CSDL
│
└── client/                  # Frontend Module (Java Swing GUI)
    ├── pom.xml              # Quản lý phụ thuộc Maven của Client (Jackson)
    └── src/
        └── main/
            └── java/com/game3cay/
                ├── gui/                    # Các màn hình giao diện Swing (Login, Register, Lobby, Table)
                │   ├── MainFrame.java      # Cửa sổ chính khởi chạy giao diện Swing
                │   ├── LoginPanel.java     # Màn hình Đăng nhập
                │   ├── RegisterPanel.java  # Màn hình Đăng ký
                │   └── GameTablePanel.java # Bàn chơi game 3 cây (Hiển thị lá bài, nút cược)
                ├── network/                # Quản lý kết nối Socket đến Server
                │   ├── SocketClient.java   # Singleton gửi/nhận luồng byte TCP & Thread nghe liên tục
                │   └── MessageHandler.java # Xử lý phản hồi JSON từ Server và cập nhật GUI
                └── model/                  # Data models cho phía Client (Card, PlayerInfo)