-- =========================================================
-- BaCay Online — Schema PostgreSQL (UTF-8)
-- Phiên bản: 1.1  (merge thiết kế Nhật + Khánh)
-- Người phụ trách: Nhật (module D01)
-- =========================================================
-- Tạo DB mới:
--   psql -U postgres -f /đường/dẫn/bacaudb.sql
-- Trong psql:
--   \i /đường/dẫn/bacaudb.sql
-- Reset sạch:
--   DROP DATABASE IF EXISTS bacaudb;  -- rồi chạy lại file này
-- =========================================================

CREATE DATABASE bacaudb
    ENCODING    'UTF8'
    LC_COLLATE  = 'en_US.UTF-8'
    LC_CTYPE    = 'en_US.UTF-8'
    TEMPLATE    = template0;

\c bacaudb

-- =========================================================
-- 0. Bảng tham chiếu — 52 lá bài (dữ liệu tĩnh, không đổi)
-- =========================================================

CREATE TABLE cards (
    id          INT         PRIMARY KEY,          -- 1–52, suit*13 + rank_order
    suit        VARCHAR(10) NOT NULL,             -- CHUON / BICH / CO / RO
    rank        VARCHAR(5)  NOT NULL,             -- A / 2-10 / J / Q / K
    point       INT         NOT NULL,             -- điểm Ba Cây: A=1, 2-9=mặt, 10/J/Q/K=0
    rank_value  INT         NOT NULL,             -- thứ tự tự nhiên: A=1..K=13
    suit_value  INT         NOT NULL,             -- so sánh chất: CHUON=1,BICH=2,CO=3,RO=4
    UNIQUE (suit, rank)
);

-- Nạp 52 lá bài bằng DO block (sạch hơn 52 dòng INSERT)
DO $$
DECLARE
    suit_names  TEXT[]  := ARRAY['CHUON','BICH','CO','RO'];
    suit_vals   INT[]   := ARRAY[1,2,3,4];
    rank_names  TEXT[]  := ARRAY['A','2','3','4','5','6','7','8','9','10','J','Q','K'];
    rank_vals   INT[]   := ARRAY[1,2,3,4,5,6,7,8,9,10,11,12,13];
    rank_pts    INT[]   := ARRAY[1,2,3,4,5,6,7,8,9,0,0,0,0];
    s INT; r INT;
BEGIN
    FOR s IN 1..4 LOOP
        FOR r IN 1..13 LOOP
            INSERT INTO cards (id, suit, rank, point, rank_value, suit_value)
            VALUES ((s-1)*13 + r, suit_names[s], rank_names[r],
                    rank_pts[r], rank_vals[r], suit_vals[s]);
        END LOOP;
    END LOOP;
END $$;

-- =========================================================
-- 1. Người dùng
-- =========================================================
CREATE TABLE users (
    id            BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(50)  UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,          -- PBKDF2WithHmacSHA256, KHÔNG lưu plain text
    display_name  VARCHAR(100),
    email         VARCHAR(100) UNIQUE,
    avatar_url    VARCHAR(255),
    balance       DECIMAL(15,2) NOT NULL DEFAULT 0
                  CHECK (balance >= 0),           -- lớp bảo vệ DB; BUS kiểm tra trước
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE / BLOCKED
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- =========================================================
-- 2. Phòng chơi
-- =========================================================
CREATE TABLE rooms (
    id          BIGSERIAL     PRIMARY KEY,
    room_code   VARCHAR(20)   UNIQUE NOT NULL,
    room_name   VARCHAR(100)  NOT NULL,
    owner_id    BIGINT        NOT NULL REFERENCES users(id),
    min_players INT           NOT NULL DEFAULT 2
                CHECK (min_players BETWEEN 2 AND 8),
    max_players INT           NOT NULL DEFAULT 8
                CHECK (max_players BETWEEN 2 AND 8),
    bet_amount  DECIMAL(15,2) NOT NULL,           -- mức ante cơ bản của phòng
    status      VARCHAR(20)   NOT NULL DEFAULT 'WAITING', -- WAITING / PLAYING / FINISHED
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT rooms_min_le_max CHECK (min_players <= max_players)
);

-- =========================================================
-- 3. Người chơi trong phòng (lịch sử vào/rời)
-- =========================================================
CREATE TABLE room_players (
    id          BIGSERIAL   PRIMARY KEY,
    room_id     BIGINT      NOT NULL REFERENCES rooms(id),
    user_id     BIGINT      NOT NULL REFERENCES users(id),
    seat_number INT         NOT NULL CHECK (seat_number BETWEEN 1 AND 8),
    joined_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    left_at     TIMESTAMPTZ,
    status      VARCHAR(20) NOT NULL DEFAULT 'PLAYING',  -- PLAYING / SPECTATOR / LEFT
    UNIQUE (room_id, user_id)         -- một người không ngồi 2 ghế cùng lúc
);

-- =========================================================
-- 4. Ván chơi
-- =========================================================
CREATE TABLE games (
    id          BIGSERIAL     PRIMARY KEY,
    round_id    VARCHAR(36)   UNIQUE NOT NULL,    -- UUID do server sinh — chống ghi lặp
    room_id     BIGINT        NOT NULL REFERENCES rooms(id),
    game_number INT           NOT NULL,           -- số thứ tự ván trong phòng
    started_at  TIMESTAMPTZ,
    ended_at    TIMESTAMPTZ,
    status      VARCHAR(20)   NOT NULL DEFAULT 'WAITING',
                                                  -- WAITING/PLAYING/FINISHED/CANCELLED
    base_point  DECIMAL(15,2) NOT NULL            -- mức ante thực tế của ván
    -- winner_id KHÔNG có ở đây: dùng game_players.result = 'WIN' để truy vấn
);

-- =========================================================
-- 5. Người chơi trong ván (kết quả)
-- =========================================================
CREATE TABLE game_players (
    id               BIGSERIAL     PRIMARY KEY,
    game_id          BIGINT        NOT NULL REFERENCES games(id),
    user_id          BIGINT        NOT NULL REFERENCES users(id),
    seat_number      INT           NOT NULL CHECK (seat_number BETWEEN 1 AND 8),
    score            INT,                         -- điểm Ba Cây (0-9, chỉ nghĩa với DIEM)
    hand_type        VARCHAR(20),                 -- SAP / LIENG / BO_DOI / DIEM
    result           VARCHAR(20),                 -- WIN / LOSE / DRAW / FOLD
    profit           DECIMAL(15,2),               -- dương = thắng, âm = thua (sau ante)
    final_multiplier DECIMAL(8,2),                -- hệ số cuối sau vòng tố
    UNIQUE (game_id, user_id)                     -- chống ghi lặp cùng người trong ván
);

-- =========================================================
-- 6. Lịch sử chia bài (tùy chọn — ghi khi ván FINISHED)
--    Cho phép audit, replay, kiểm tra gian lận
-- =========================================================
CREATE TABLE game_cards (
    id            BIGSERIAL PRIMARY KEY,
    game_id       BIGINT    NOT NULL REFERENCES games(id),
    user_id       BIGINT    NOT NULL REFERENCES users(id),
    card_id       INT       NOT NULL REFERENCES cards(id),
    card_position INT       NOT NULL CHECK (card_position BETWEEN 1 AND 3),
    revealed      BOOLEAN   NOT NULL DEFAULT FALSE, -- TRUE = lá đã lật ở showdown
    UNIQUE (game_id, user_id, card_position),       -- 3 lá/người/ván
    UNIQUE (game_id, card_id)                       -- mỗi lá chỉ chia 1 lần trong ván
);

-- =========================================================
-- 7. Lịch sử tố điểm chi tiết
-- =========================================================
CREATE TABLE point_bets (
    id               BIGSERIAL     PRIMARY KEY,
    game_id          BIGINT        NOT NULL REFERENCES games(id),
    user_id          BIGINT        NOT NULL REFERENCES users(id),
    target_user_id   BIGINT        REFERENCES users(id),  -- null = tố toàn bàn
    action           VARCHAR(20)   NOT NULL,    -- RAISE / CALL / FOLD / ALL_IN / ANTE
    raised_point     DECIMAL(15,2) NOT NULL,    -- mức cược tích lũy sau action
    raise_amount     DECIMAL(15,2) NOT NULL DEFAULT 0, -- số điểm tăng thêm
    multiplier_before DECIMAL(8,2) NOT NULL DEFAULT 1,
    multiplier_after  DECIMAL(8,2) NOT NULL DEFAULT 1,
    sequence_no      INT           NOT NULL,    -- thứ tự action trong ván (bắt đầu 1)
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- =========================================================
-- 8. Side pot (khi có nhiều mức ALL_IN)
-- =========================================================
CREATE TABLE side_pots (
    id      BIGSERIAL     PRIMARY KEY,
    game_id BIGINT        NOT NULL REFERENCES games(id),
    amount  DECIMAL(15,2) NOT NULL,
    pot_order INT         NOT NULL               -- thứ tự side pot trong ván (1, 2...)
);

CREATE TABLE side_pot_players (
    id          BIGSERIAL PRIMARY KEY,
    side_pot_id BIGINT    NOT NULL REFERENCES side_pots(id),
    user_id     BIGINT    NOT NULL REFERENCES users(id),
    UNIQUE (side_pot_id, user_id)
);

-- =========================================================
-- 9. Giao dịch điểm (audit trail đầy đủ)
-- =========================================================
CREATE TABLE transactions (
    id             BIGSERIAL     PRIMARY KEY,
    user_id        BIGINT        NOT NULL REFERENCES users(id),
    game_id        BIGINT        REFERENCES games(id),   -- null = nạp/rút ngoài ván
    type           VARCHAR(20)   NOT NULL,    -- BET / WIN / LOSE / REFUND
    amount         DECIMAL(15,2) NOT NULL,    -- dương = cộng, âm = trừ
    balance_before DECIMAL(15,2) NOT NULL,    -- số dư trước giao dịch
    balance_after  DECIMAL(15,2) NOT NULL,    -- số dư sau giao dịch (kiểm tra: before+amount=after)
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- =========================================================
-- 10. Chat (ghi khi server.enable_chat_log=true)
-- =========================================================
CREATE TABLE chat_messages (
    id         BIGSERIAL   PRIMARY KEY,
    room_id    BIGINT      NOT NULL REFERENCES rooms(id),
    sender_id  BIGINT      REFERENCES users(id),  -- null = tin hệ thống (SYSTEM)
    content    TEXT        NOT NULL,
    type       VARCHAR(20) NOT NULL,              -- TEXT / EMOJI / SYSTEM
    is_hidden  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- =========================================================
-- Index gợi ý (Nhật thêm/bỏ theo kết quả EXPLAIN ANALYZE)
-- =========================================================
CREATE INDEX idx_room_players_room   ON room_players (room_id);
CREATE INDEX idx_room_players_user   ON room_players (user_id);
CREATE INDEX idx_games_room          ON games (room_id);
CREATE INDEX idx_games_round_id      ON games (round_id);
CREATE INDEX idx_game_players_game   ON game_players (game_id);
CREATE INDEX idx_game_players_user   ON game_players (user_id);
CREATE INDEX idx_game_cards_game     ON game_cards (game_id);
CREATE INDEX idx_point_bets_game     ON point_bets (game_id);
CREATE INDEX idx_transactions_user   ON transactions (user_id);
CREATE INDEX idx_transactions_game   ON transactions (game_id);
CREATE INDEX idx_chat_room           ON chat_messages (room_id, created_at);

-- =========================================================
-- Dữ liệu mẫu — xóa trước demo thật
-- =========================================================
INSERT INTO users (username, password_hash, display_name, balance) VALUES
    ('admin',   'PLACEHOLDER_HASH', 'Admin',   1000000),
    ('player1', 'PLACEHOLDER_HASH', 'Người 1', 5000),
    ('player2', 'PLACEHOLDER_HASH', 'Người 2', 5000),
    ('player3', 'PLACEHOLDER_HASH', 'Người 3', 5000),
    ('player4', 'PLACEHOLDER_HASH', 'Người 4', 5000);
-- Chạy PasswordUtil.hash("password") để lấy hash thật thay PLACEHOLDER_HASH

-- =========================================================
-- Quan hệ tóm tắt:
--   users 1–N room_players N–1 rooms
--   rooms 1–N games
--   users 1–N game_players N–1 games
--   cards 1–N game_cards N–1 games, N–1 users
--   games 1–N point_bets N–1 users
--   games 1–N side_pots 1–N side_pot_players N–1 users
--   users 1–N transactions N–1 games (nullable)
--   rooms 1–N chat_messages N–1 users (nullable sender)
-- =========================================================
