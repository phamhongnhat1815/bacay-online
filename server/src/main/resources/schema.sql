-- MySQL 8.0.16+, theo thiết kế DOCX. Chạy trên DB mới; không xóa dữ liệu.
-- IF NOT EXISTS không cập nhật cấu trúc bảng cũ; cần migration riêng nếu đã tạo M2.
CREATE DATABASE IF NOT EXISTS bacay_online CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE bacay_online;
CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NULL,
    avatar_url VARCHAR(255) NULL,
    balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    total_games INT NOT NULL DEFAULT 0,
    total_wins INT NOT NULL DEFAULT 0,
    total_losses INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_balance CHECK (balance >= 0),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE','BLOCKED')),
    CONSTRAINT ck_users_stats CHECK (total_games >= 0 AND total_wins >= 0 AND total_losses >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Không CASCADE DELETE để giữ lịch sử ván/giao dịch.
CREATE TABLE IF NOT EXISTS rooms (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_code VARCHAR(20) NOT NULL UNIQUE,
    room_name VARCHAR(100) NOT NULL,
    owner_id BIGINT NOT NULL,
    min_players INT NOT NULL DEFAULT 2,
    max_players INT NOT NULL DEFAULT 8,
    bet_amount DECIMAL(15,2) NOT NULL DEFAULT 1.00,
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_id) REFERENCES users(id),
    CHECK (2 <= min_players AND min_players <= max_players AND max_players <= 8),
    CHECK (bet_amount > 0),
    CHECK (status IN ('WAITING','PLAYING','FINISHED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS room_players (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    seat_number INT NOT NULL,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    left_at TIMESTAMP NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PLAYING',
    -- NULL ở bản ghi LEFT cho phép tái sử dụng ghế và quay lại phòng.
    active_room_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN status = 'PLAYING' THEN room_id ELSE NULL END) STORED,
    active_user_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN status = 'PLAYING' THEN user_id ELSE NULL END) STORED,
    UNIQUE (active_room_id, seat_number),
    UNIQUE (active_user_id),
    FOREIGN KEY (room_id) REFERENCES rooms(id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    CHECK (seat_number BETWEEN 1 AND 8),
    CHECK (status IN ('PLAYING','LEFT')),
    CHECK ((status='PLAYING' AND left_at IS NULL)
        OR (status='LEFT' AND left_at IS NOT NULL AND left_at >= joined_at)),
    INDEX ix_room_players_room (room_id, status),
    INDEX ix_room_players_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS games (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    game_number INT NOT NULL,
    started_at TIMESTAMP NULL,
    ended_at TIMESTAMP NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    winner_id BIGINT NULL,
    base_point INT NOT NULL DEFAULT 1,
    UNIQUE (room_id, game_number),
    FOREIGN KEY (room_id) REFERENCES rooms(id),
    FOREIGN KEY (winner_id) REFERENCES users(id),
    CHECK (game_number > 0 AND base_point > 0),
    CHECK (status IN ('WAITING','PLAYING','FINISHED','CANCELLED')),
    CHECK (ended_at IS NULL OR (started_at IS NOT NULL AND ended_at >= started_at))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS game_players (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    seat_number INT NOT NULL,
    score INT NULL,
    hand_type VARCHAR(20) NULL,
    result VARCHAR(20) NULL,
    profit DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    final_multiplier DECIMAL(8,2) NOT NULL DEFAULT 1.00,
    UNIQUE (game_id, user_id),
    UNIQUE (game_id, seat_number),
    FOREIGN KEY (game_id) REFERENCES games(id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    CHECK (seat_number BETWEEN 1 AND 8),
    CHECK (score IS NULL OR score BETWEEN 0 AND 9),
    CHECK (hand_type IS NULL OR hand_type IN ('NORMAL','LIENG','SAP','BO_DOI')),
    CHECK (result IS NULL OR result IN ('WIN','LOSE','DRAW')),
    CHECK (final_multiplier > 0),
    INDEX ix_game_players_history (user_id, game_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cards (
    id INT PRIMARY KEY,
    suit VARCHAR(10) NOT NULL,
    `rank` VARCHAR(5) NOT NULL,
    point INT NOT NULL,
    UNIQUE (suit, `rank`),
    CHECK (suit IN ('RO','CO','BICH','CHUON')),
    CHECK (`rank` IN ('A','2','3','4','5','6','7','8','9','10','J','Q','K')),
    CHECK (point BETWEEN 0 AND 9)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS game_cards (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    card_id INT NOT NULL,
    card_position INT NOT NULL,
    UNIQUE (game_id, card_id),
    UNIQUE (game_id, user_id, card_position),
    FOREIGN KEY (game_id, user_id) REFERENCES game_players(game_id, user_id),
    FOREIGN KEY (card_id) REFERENCES cards(id),
    CHECK (card_position BETWEEN 1 AND 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS point_bets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    target_user_id BIGINT NULL,
    previous_point INT NOT NULL,
    raised_point INT NOT NULL,
    raise_amount INT NOT NULL,
    multiplier_before DECIMAL(8,2) NOT NULL,
    multiplier_after DECIMAL(8,2) NOT NULL,
    action VARCHAR(20) NOT NULL,
    sequence_no INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (game_id, sequence_no),
    FOREIGN KEY (game_id, user_id) REFERENCES game_players(game_id, user_id),
    FOREIGN KEY (game_id, target_user_id) REFERENCES game_players(game_id, user_id),
    CHECK (previous_point >= 0 AND raised_point >= 0 AND raise_amount >= 0),
    CHECK (multiplier_before > 0 AND multiplier_after > 0),
    CHECK (action IN ('RAISE','CALL','FOLD','ACCEPT')),
    CHECK (sequence_no > 0),
    INDEX ix_point_bets_user (user_id, game_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    game_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    balance_before DECIMAL(15,2) NOT NULL,
    balance_after DECIMAL(15,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (game_id, user_id) REFERENCES game_players(game_id, user_id),
    CHECK (type IN ('BET','WIN','LOSE','REFUND')),
    CHECK (balance_before >= 0 AND balance_after >= 0),
    CHECK (balance_after = balance_before + amount),
    INDEX ix_transactions_history (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Server kiểm tra số người theo max_players, lượt tố và điều kiện bắt đầu ván.
-- 52 lá theo DOCX. Quy ước point chưa được DOCX chốt:
-- seed A=1, 2–9 giữ nguyên, 10/J/Q/K=0; cần đối chiếu luật Engine.
-- Chạy lại seed giữ nguyên các lá đã tồn tại.
INSERT INTO cards (id, suit, `rank`, point) VALUES
    (1, 'RO', 'A', 1),
    (2, 'RO', '2', 2),
    (3, 'RO', '3', 3),
    (4, 'RO', '4', 4),
    (5, 'RO', '5', 5),
    (6, 'RO', '6', 6),
    (7, 'RO', '7', 7),
    (8, 'RO', '8', 8),
    (9, 'RO', '9', 9),
    (10, 'RO', '10', 0),
    (11, 'RO', 'J', 0),
    (12, 'RO', 'Q', 0),
    (13, 'RO', 'K', 0),
    (14, 'CO', 'A', 1),
    (15, 'CO', '2', 2),
    (16, 'CO', '3', 3),
    (17, 'CO', '4', 4),
    (18, 'CO', '5', 5),
    (19, 'CO', '6', 6),
    (20, 'CO', '7', 7),
    (21, 'CO', '8', 8),
    (22, 'CO', '9', 9),
    (23, 'CO', '10', 0),
    (24, 'CO', 'J', 0),
    (25, 'CO', 'Q', 0),
    (26, 'CO', 'K', 0),
    (27, 'BICH', 'A', 1),
    (28, 'BICH', '2', 2),
    (29, 'BICH', '3', 3),
    (30, 'BICH', '4', 4),
    (31, 'BICH', '5', 5),
    (32, 'BICH', '6', 6),
    (33, 'BICH', '7', 7),
    (34, 'BICH', '8', 8),
    (35, 'BICH', '9', 9),
    (36, 'BICH', '10', 0),
    (37, 'BICH', 'J', 0),
    (38, 'BICH', 'Q', 0),
    (39, 'BICH', 'K', 0),
    (40, 'CHUON', 'A', 1),
    (41, 'CHUON', '2', 2),
    (42, 'CHUON', '3', 3),
    (43, 'CHUON', '4', 4),
    (44, 'CHUON', '5', 5),
    (45, 'CHUON', '6', 6),
    (46, 'CHUON', '7', 7),
    (47, 'CHUON', '8', 8),
    (48, 'CHUON', '9', 9),
    (49, 'CHUON', '10', 0),
    (50, 'CHUON', 'J', 0),
    (51, 'CHUON', 'Q', 0),
    (52, 'CHUON', 'K', 0)
ON DUPLICATE KEY UPDATE id = id;
