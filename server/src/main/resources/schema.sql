-- ============================================================
-- BA CAY ONLINE
-- DATABASE SCHEMA
-- DBMS: MySQL 8.0+
-- Character Set: utf8mb4
-- ============================================================

CREATE DATABASE IF NOT EXISTS bacay_online
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE bacay_online;


-- ============================================================
-- 1. USERS
-- Thông tin tài khoản người chơi
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
                                     user_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

                                     username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,

    email VARCHAR(100) NULL,
    avatar_url VARCHAR(255) NULL,

    balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id),

    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email),

    CONSTRAINT chk_users_balance
    CHECK (balance >= 0),

    CONSTRAINT chk_users_status
    CHECK (status IN ('ACTIVE', 'BLOCKED'))

    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 2. ROOMS
-- Thông tin phòng chơi
-- ============================================================

CREATE TABLE IF NOT EXISTS rooms (
                                     room_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

                                     room_code VARCHAR(20) NOT NULL,
    room_name VARCHAR(100) NULL,

    owner_user_id BIGINT UNSIGNED NOT NULL,

    min_players TINYINT UNSIGNED NOT NULL DEFAULT 2,
    max_players TINYINT UNSIGNED NOT NULL DEFAULT 8,

    bet_amount DECIMAL(15,2) NOT NULL DEFAULT 0.00,

    status VARCHAR(20) NOT NULL DEFAULT 'WAITING',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (room_id),

    UNIQUE KEY uk_rooms_room_code (room_code),

    KEY idx_rooms_owner_user (owner_user_id),
    KEY idx_rooms_status (status),

    CONSTRAINT fk_rooms_owner_user
    FOREIGN KEY (owner_user_id)
    REFERENCES users(user_id),

    CONSTRAINT chk_rooms_min_players
    CHECK (min_players >= 2),

    CONSTRAINT chk_rooms_max_players
    CHECK (max_players <= 8),

    CONSTRAINT chk_rooms_player_range
    CHECK (min_players <= max_players),

    CONSTRAINT chk_rooms_bet_amount
    CHECK (bet_amount >= 0),

    CONSTRAINT chk_rooms_status
    CHECK (
              status IN (
              'WAITING',
              'PLAYING',
              'FINISHED'
                        )
    )

    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 3. ROOM_PLAYERS
-- Danh sách người chơi trong phòng
-- Cho phép một user rời rồi vào lại phòng
-- ============================================================

CREATE TABLE IF NOT EXISTS room_players (
                                            room_player_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

                                            room_id BIGINT UNSIGNED NOT NULL,
                                            user_id BIGINT UNSIGNED NOT NULL,

                                            seat_number TINYINT UNSIGNED NOT NULL,

                                            joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                            left_at TIMESTAMP NULL,

                                            status VARCHAR(20) NOT NULL DEFAULT 'JOINED',

    -- Chỉ có giá trị khi player đang JOINED
    active_user_id BIGINT UNSIGNED
    GENERATED ALWAYS AS (
                            CASE
                            WHEN status = 'JOINED'
                            THEN user_id
                            ELSE NULL
                            END
                        ) STORED,

    active_seat_number TINYINT UNSIGNED
    GENERATED ALWAYS AS (
                            CASE
                            WHEN status = 'JOINED'
                            THEN seat_number
                            ELSE NULL
                            END
                        ) STORED,

    PRIMARY KEY (room_player_id),

    UNIQUE KEY uk_room_players_active_user (
                                               room_id,
                                               active_user_id
                                           ),

    UNIQUE KEY uk_room_players_active_seat (
                                               room_id,
                                               active_seat_number
                                           ),

    KEY idx_room_players_room (
                                  room_id
                              ),

    KEY idx_room_players_user (
                                  user_id
                              ),

    KEY idx_room_players_status (
                                    room_id,
                                    status
                                ),

    CONSTRAINT fk_room_players_room
    FOREIGN KEY (room_id)
    REFERENCES rooms(room_id)
    ON DELETE CASCADE,

    CONSTRAINT fk_room_players_user
    FOREIGN KEY (user_id)
    REFERENCES users(user_id),

    CONSTRAINT chk_room_players_seat
    CHECK (seat_number BETWEEN 1 AND 8),

    CONSTRAINT chk_room_players_status
    CHECK (
              status IN (
              'JOINED',
              'LEFT',
              'BLOCKED'
                        )
    )

    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 4. GAMES
-- Một phòng có thể chơi nhiều ván
-- ============================================================

CREATE TABLE IF NOT EXISTS games (
                                     game_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

                                     room_id BIGINT UNSIGNED NOT NULL,

                                     game_number INT UNSIGNED NOT NULL,

                                     started_at TIMESTAMP NULL,
                                     ended_at TIMESTAMP NULL,

                                     status VARCHAR(20) NOT NULL DEFAULT 'WAITING',

    winner_user_id BIGINT UNSIGNED NULL,

    base_point INT UNSIGNED NOT NULL DEFAULT 1,

    PRIMARY KEY (game_id),

    UNIQUE KEY uk_games_room_number (
                                        room_id,
                                        game_number
                                    ),

    KEY idx_games_room (
                           room_id
                       ),

    KEY idx_games_status (
                             status
                         ),

    KEY idx_games_winner_user (
                                  winner_user_id
                              ),

    CONSTRAINT fk_games_room
    FOREIGN KEY (room_id)
    REFERENCES rooms(room_id)
    ON DELETE CASCADE,

    CONSTRAINT fk_games_winner_user
    FOREIGN KEY (winner_user_id)
    REFERENCES users(user_id),

    CONSTRAINT chk_games_base_point
    CHECK (base_point > 0),

    CONSTRAINT chk_games_status
    CHECK (
              status IN (
              'WAITING',
              'PLAYING',
              'FINISHED',
              'CANCELLED'
                        )
    )

    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 5. GAME_PLAYERS
-- Người chơi thực tế tham gia từng ván
-- ============================================================

CREATE TABLE IF NOT EXISTS game_players (
                                            game_player_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

                                            game_id BIGINT UNSIGNED NOT NULL,
                                            user_id BIGINT UNSIGNED NOT NULL,

                                            seat_number TINYINT UNSIGNED NOT NULL,

                                            score TINYINT UNSIGNED NOT NULL DEFAULT 0,

                                            hand_type VARCHAR(20) NOT NULL DEFAULT 'NORMAL',

    result VARCHAR(20) NOT NULL DEFAULT 'DRAW',

    profit DECIMAL(15,2) NOT NULL DEFAULT 0.00,

    final_multiplier DECIMAL(8,2) NOT NULL DEFAULT 1.00,

    PRIMARY KEY (game_player_id),

    UNIQUE KEY uk_game_players_user (
                                        game_id,
                                        user_id
                                    ),

    UNIQUE KEY uk_game_players_seat (
                                        game_id,
                                        seat_number
                                    ),

    KEY idx_game_players_game (
                                  game_id
                              ),

    KEY idx_game_players_user (
                                  user_id
                              ),

    CONSTRAINT fk_game_players_game
    FOREIGN KEY (game_id)
    REFERENCES games(game_id)
    ON DELETE CASCADE,

    CONSTRAINT fk_game_players_user
    FOREIGN KEY (user_id)
    REFERENCES users(user_id),

    CONSTRAINT chk_game_players_seat
    CHECK (seat_number BETWEEN 1 AND 8),

    CONSTRAINT chk_game_players_score
    CHECK (score BETWEEN 0 AND 9),

    CONSTRAINT chk_game_players_result
    CHECK (
              result IN (
              'WIN',
              'LOSE',
              'DRAW'
                        )
    ),

    CONSTRAINT chk_game_players_multiplier
    CHECK (final_multiplier > 0)

    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 6. CARDS
-- Danh mục 52 lá bài
-- ============================================================

CREATE TABLE IF NOT EXISTS cards (
                                     card_id INT UNSIGNED NOT NULL,

                                     suit VARCHAR(10) NOT NULL,

    card_rank VARCHAR(5) NOT NULL,

    point TINYINT UNSIGNED NOT NULL,

    PRIMARY KEY (card_id),

    UNIQUE KEY uk_cards_suit_rank (
                                      suit,
                                      card_rank
                                  ),

    CONSTRAINT chk_cards_suit
    CHECK (
              suit IN (
              'RO',
              'CO',
              'BICH',
              'CHUON'
                      )
    ),

    CONSTRAINT chk_cards_point
    CHECK (
              point BETWEEN 1 AND 10
          )

    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 7. GAME_CARDS
-- Lưu lá bài được chia cho người chơi trong từng ván
-- ============================================================

CREATE TABLE IF NOT EXISTS game_cards (
                                          game_card_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

                                          game_id BIGINT UNSIGNED NOT NULL,
                                          user_id BIGINT UNSIGNED NOT NULL,
                                          card_id INT UNSIGNED NOT NULL,

                                          card_position TINYINT UNSIGNED NOT NULL,

                                          PRIMARY KEY (game_card_id),

    UNIQUE KEY uk_game_cards_card (
                                      game_id,
                                      card_id
                                  ),

    UNIQUE KEY uk_game_cards_position (
                                          game_id,
                                          user_id,
                                          card_position
                                      ),

    KEY idx_game_cards_game (
                                game_id
                            ),

    KEY idx_game_cards_user (
                                game_id,
                                user_id
                            ),

    KEY idx_game_cards_card (
                                card_id
                            ),

    CONSTRAINT fk_game_cards_game
    FOREIGN KEY (game_id)
    REFERENCES games(game_id)
    ON DELETE CASCADE,

    CONSTRAINT fk_game_cards_user
    FOREIGN KEY (user_id)
    REFERENCES users(user_id),

    CONSTRAINT fk_game_cards_card
    FOREIGN KEY (card_id)
    REFERENCES cards(card_id),

    CONSTRAINT chk_game_cards_position
    CHECK (
              card_position BETWEEN 1 AND 3
          )

    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 8. POINT_BETS
-- Lịch sử tố điểm của từng ván
-- ============================================================

CREATE TABLE IF NOT EXISTS point_bets (
                                          point_bet_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

                                          game_id BIGINT UNSIGNED NOT NULL,

                                          user_id BIGINT UNSIGNED NOT NULL,

                                          target_user_id BIGINT UNSIGNED NULL,

                                          previous_point INT UNSIGNED NOT NULL,

                                          raised_point INT UNSIGNED NOT NULL,

                                          raise_amount INT UNSIGNED NOT NULL DEFAULT 0,

                                          multiplier_before DECIMAL(8,2) NOT NULL DEFAULT 1.00,

    multiplier_after DECIMAL(8,2) NOT NULL DEFAULT 1.00,

    action VARCHAR(20) NOT NULL,

    sequence_no INT UNSIGNED NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (point_bet_id),

    UNIQUE KEY uk_point_bets_sequence (
                                          game_id,
                                          sequence_no
                                      ),

    KEY idx_point_bets_game (
                                game_id,
                                sequence_no
                            ),

    KEY idx_point_bets_user (
                                user_id
                            ),

    KEY idx_point_bets_target_user (
                                       target_user_id
                                   ),

    CONSTRAINT fk_point_bets_game
    FOREIGN KEY (game_id)
    REFERENCES games(game_id)
    ON DELETE CASCADE,

    CONSTRAINT fk_point_bets_user
    FOREIGN KEY (user_id)
    REFERENCES users(user_id),

    CONSTRAINT fk_point_bets_target_user
    FOREIGN KEY (target_user_id)
    REFERENCES users(user_id),

    CONSTRAINT chk_point_bets_points
    CHECK (
              raised_point >= previous_point
              AND raise_amount = raised_point - previous_point
          ),

    CONSTRAINT chk_point_bets_multiplier
    CHECK (
              multiplier_before > 0
              AND multiplier_after > 0
          ),

    CONSTRAINT chk_point_bets_action
    CHECK (
              action IN (
              'RAISE',
              'CALL',
              'FOLD',
              'ACCEPT'
                        )
    )

    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 9. ROOM_MESSAGES
-- Chat trong phòng
--
-- TEXT:
--   sender_user_id bắt buộc
--
-- SYSTEM:
--   server có thể tạo message, sender_user_id NULL
--
-- game_id:
--   NULL nếu tin nhắn không thuộc một ván cụ thể
-- ============================================================

CREATE TABLE IF NOT EXISTS room_messages (
                                             room_message_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

                                             room_id BIGINT UNSIGNED NOT NULL,

                                             game_id BIGINT UNSIGNED NULL,

                                             sender_user_id BIGINT UNSIGNED NULL,

                                             message TEXT NOT NULL,

                                             message_type VARCHAR(20) NOT NULL DEFAULT 'TEXT',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NULL,

    deleted_at TIMESTAMP NULL,

    PRIMARY KEY (room_message_id),

    KEY idx_room_messages_room_time (
                                        room_id,
                                        created_at,
                                        room_message_id
                                    ),

    KEY idx_room_messages_game (
                                   game_id
                               ),

    KEY idx_room_messages_sender (
                                     sender_user_id
                                 ),

    CONSTRAINT fk_room_messages_room
    FOREIGN KEY (room_id)
    REFERENCES rooms(room_id)
    ON DELETE CASCADE,

    CONSTRAINT fk_room_messages_game
    FOREIGN KEY (game_id)
    REFERENCES games(game_id)
    ON DELETE SET NULL,

    CONSTRAINT fk_room_messages_sender_user
    FOREIGN KEY (sender_user_id)
    REFERENCES users(user_id),

    CONSTRAINT chk_room_messages_type
    CHECK (
              message_type IN (
              'TEXT',
              'SYSTEM'
                              )
    ),

    CONSTRAINT chk_room_messages_sender
    CHECK (
(
              message_type = 'TEXT'
              AND sender_user_id IS NOT NULL
)
    OR
(
    message_type = 'SYSTEM'
)
    )

    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 10. TRANSACTIONS
-- Lịch sử biến động số dư người chơi
-- ============================================================

CREATE TABLE IF NOT EXISTS transactions (
                                            transaction_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

                                            user_id BIGINT UNSIGNED NOT NULL,

                                            game_id BIGINT UNSIGNED NULL,

                                            type VARCHAR(20) NOT NULL,

    amount DECIMAL(15,2) NOT NULL,

    balance_before DECIMAL(15,2) NOT NULL,

    balance_after DECIMAL(15,2) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (transaction_id),

    KEY idx_transactions_user_time (
                                       user_id,
                                       created_at
                                   ),

    KEY idx_transactions_game (
                                  game_id
                              ),

    KEY idx_transactions_type (
                                  type
                              ),

    CONSTRAINT fk_transactions_user
    FOREIGN KEY (user_id)
    REFERENCES users(user_id),

    CONSTRAINT fk_transactions_game
    FOREIGN KEY (game_id)
    REFERENCES games(game_id)
    ON DELETE SET NULL,

    CONSTRAINT chk_transactions_type
    CHECK (
              type IN (
              'BET',
              'WIN',
              'LOSE',
              'REFUND'
                      )
    ),

    CONSTRAINT chk_transactions_amount
    CHECK (
              amount >= 0
          ),

    CONSTRAINT chk_transactions_balance_before
    CHECK (
              balance_before >= 0
          ),

    CONSTRAINT chk_transactions_balance_after
    CHECK (
              balance_after >= 0
          )

    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- SEED 52 LÁ BÀI
-- ============================================================

INSERT IGNORE INTO cards (
    card_id,
    suit,
    card_rank,
    point
)
VALUES

-- =========================
-- RÔ
-- =========================

(1,  'RO', 'A',  1),
(2,  'RO', '2',  2),
(3,  'RO', '3',  3),
(4,  'RO', '4',  4),
(5,  'RO', '5',  5),
(6,  'RO', '6',  6),
(7,  'RO', '7',  7),
(8,  'RO', '8',  8),
(9,  'RO', '9',  9),
(10, 'RO', '10', 10),
(11, 'RO', 'J', 10),
(12, 'RO', 'Q', 10),
(13, 'RO', 'K', 10),

-- =========================
-- CƠ
-- =========================

(14, 'CO', 'A',  1),
(15, 'CO', '2',  2),
(16, 'CO', '3',  3),
(17, 'CO', '4',  4),
(18, 'CO', '5',  5),
(19, 'CO', '6',  6),
(20, 'CO', '7',  7),
(21, 'CO', '8',  8),
(22, 'CO', '9',  9),
(23, 'CO', '10', 10),
(24, 'CO', 'J', 10),
(25, 'CO', 'Q', 10),
(26, 'CO', 'K', 10),

-- =========================
-- BÍCH
-- =========================

(27, 'BICH', 'A',  1),
(28, 'BICH', '2',  2),
(29, 'BICH', '3',  3),
(30, 'BICH', '4',  4),
(31, 'BICH', '5',  5),
(32, 'BICH', '6',  6),
(33, 'BICH', '7',  7),
(34, 'BICH', '8',  8),
(35, 'BICH', '9',  9),
(36, 'BICH', '10', 10),
(37, 'BICH', 'J', 10),
(38, 'BICH', 'Q', 10),
(39, 'BICH', 'K', 10),

-- =========================
-- CHUỒN
-- =========================

(40, 'CHUON', 'A',  1),
(41, 'CHUON', '2',  2),
(42, 'CHUON', '3',  3),
(43, 'CHUON', '4',  4),
(44, 'CHUON', '5',  5),
(45, 'CHUON', '6',  6),
(46, 'CHUON', '7',  7),
(47, 'CHUON', '8',  8),
(48, 'CHUON', '9',  9),
(49, 'CHUON', '10', 10),
(50, 'CHUON', 'J', 10),
(51, 'CHUON', 'Q', 10),
(52, 'CHUON', 'K', 10);


-- ============================================================
-- KIỂM TRA DATABASE
-- ============================================================

-- Kiểm tra đủ 52 lá bài
-- SELECT COUNT(*) AS total_cards
-- FROM cards;

-- Xem cấu trúc bảng
-- DESCRIBE users;
-- DESCRIBE rooms;
-- DESCRIBE room_players;
-- DESCRIBE games;
-- DESCRIBE game_players;
-- DESCRIBE cards;
-- DESCRIBE game_cards;
-- DESCRIBE point_bets;
-- DESCRIBE room_messages;
-- DESCRIBE transactions;


-- ============================================================
-- HẾT SCHEMA
-- ============================================================