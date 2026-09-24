-- =========================================================
-- BaCay Online — Schema PostgreSQL (UTF-8)
-- Phiên bản: 1.0
-- Người phụ trách: Nhật (module D01)
-- =========================================================
-- Cách tạo DB mới:
--   psql -U postgres -f /đường/dẫn/bacaudb.sql
-- Hoặc trong psql:
--   \i /đường/dẫn/bacaudb.sql
-- =========================================================

CREATE DATABASE bacaudb
    ENCODING 'UTF8'
    LC_COLLATE = 'en_US.UTF-8'
    LC_CTYPE   = 'en_US.UTF-8'
    TEMPLATE   = template0;

\c bacaudb

-- -------------------------------------------------------
-- 1. Người dùng
-- -------------------------------------------------------
CREATE TABLE users (
    id            BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(50)  UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,          -- PBKDF2, KHÔNG lưu plain text
    display_name  VARCHAR(100),
    email         VARCHAR(100) UNIQUE,
    avatar_url    VARCHAR(255),
    balance       DECIMAL(15,2) DEFAULT 0,
    status        VARCHAR(20)  DEFAULT 'ACTIVE',  -- ACTIVE / BLOCKED
    created_at    TIMESTAMPTZ  DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  DEFAULT NOW()
);

-- -------------------------------------------------------
-- 2. Phòng chơi
-- -------------------------------------------------------
CREATE TABLE rooms (
    id          BIGSERIAL    PRIMARY KEY,
    room_code   VARCHAR(20)  UNIQUE NOT NULL,
    room_name   VARCHAR(100) NOT NULL,
    owner_id    BIGINT       NOT NULL REFERENCES users(id),
    min_players INT          DEFAULT 2,   -- ràng buộc: 2 <= min <= max <= 8
    max_players INT          DEFAULT 8,
    bet_amount  DECIMAL(15,2) NOT NULL,   -- mức cược cơ bản (ante)
    status      VARCHAR(20)  DEFAULT 'WAITING', -- WAITING / PLAYING / FINISHED
    created_at  TIMESTAMPTZ  DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  DEFAULT NOW()
);

-- -------------------------------------------------------
-- 3. Người chơi trong phòng
-- -------------------------------------------------------
CREATE TABLE room_players (
    id          BIGSERIAL   PRIMARY KEY,
    room_id     BIGINT      NOT NULL REFERENCES rooms(id),
    user_id     BIGINT      NOT NULL REFERENCES users(id),
    seat_number INT         NOT NULL,   -- 1..8
    joined_at   TIMESTAMPTZ DEFAULT NOW(),
    left_at     TIMESTAMPTZ,
    status      VARCHAR(20) DEFAULT 'PLAYING', -- PLAYING / SPECTATOR / LEFT
    UNIQUE (room_id, user_id)
);

-- -------------------------------------------------------
-- 4. Ván chơi
-- -------------------------------------------------------
CREATE TABLE games (
    id          BIGSERIAL    PRIMARY KEY,
    round_id    VARCHAR(36)  UNIQUE NOT NULL,    -- UUID, chống ghi lặp theo roundId
    room_id     BIGINT       NOT NULL REFERENCES rooms(id),
    game_number INT          NOT NULL,
    started_at  TIMESTAMPTZ,
    ended_at    TIMESTAMPTZ,
    status      VARCHAR(20)  DEFAULT 'WAITING', -- WAITING/PLAYING/FINISHED/CANCELLED
    base_point  DECIMAL(15,2) NOT NULL
);

-- -------------------------------------------------------
-- 5. Người chơi trong ván
-- -------------------------------------------------------
CREATE TABLE game_players (
    id               BIGSERIAL    PRIMARY KEY,
    game_id          BIGINT       NOT NULL REFERENCES games(id),
    user_id          BIGINT       NOT NULL REFERENCES users(id),
    seat_number      INT          NOT NULL,
    score            INT,                        -- điểm bài 3 Cây (chỉ có nghĩa với DIEM)
    hand_type        VARCHAR(20),               -- SAP / LIENG / BO_DOI / DIEM
    result           VARCHAR(20),               -- WIN / LOSE / DRAW / FOLD
    profit           DECIMAL(15,2),
    final_multiplier DECIMAL(8,2),
    UNIQUE (game_id, user_id)                   -- chống ghi lặp cùng một người trong ván
);

-- -------------------------------------------------------
-- 6. Lịch sử đặt cược chi tiết
-- -------------------------------------------------------
CREATE TABLE point_bets (
    id         BIGSERIAL    PRIMARY KEY,
    game_id    BIGINT       NOT NULL REFERENCES games(id),
    user_id    BIGINT       NOT NULL REFERENCES users(id),
    action     VARCHAR(20)  NOT NULL,            -- RAISE / CALL / FOLD / ALL_IN / ANTE
    amount     DECIMAL(15,2) NOT NULL,
    created_at TIMESTAMPTZ  DEFAULT NOW()
);

-- -------------------------------------------------------
-- 7. Side pot (cho ALL_IN nhiều mức)
-- -------------------------------------------------------
CREATE TABLE side_pots (
    id      BIGSERIAL    PRIMARY KEY,
    game_id BIGINT       NOT NULL REFERENCES games(id),
    amount  DECIMAL(15,2) NOT NULL
);

CREATE TABLE side_pot_players (
    id          BIGSERIAL PRIMARY KEY,
    side_pot_id BIGINT    NOT NULL REFERENCES side_pots(id),
    user_id     BIGINT    NOT NULL REFERENCES users(id)
);

-- -------------------------------------------------------
-- 8. Giao dịch điểm
-- -------------------------------------------------------
CREATE TABLE transactions (
    id            BIGSERIAL    PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users(id),
    game_id       BIGINT       REFERENCES games(id),
    type          VARCHAR(20)  NOT NULL,         -- BET / WIN / LOSE / REFUND
    amount        DECIMAL(15,2) NOT NULL,        -- dương = cộng, âm = trừ
    balance_after DECIMAL(15,2),
    created_at    TIMESTAMPTZ  DEFAULT NOW()
);

-- -------------------------------------------------------
-- 9. Chat (chỉ ghi khi ENABLE_CHAT_LOG=true)
-- -------------------------------------------------------
CREATE TABLE chat_messages (
    id         BIGSERIAL   PRIMARY KEY,
    room_id    BIGINT      NOT NULL REFERENCES rooms(id),
    sender_id  BIGINT      REFERENCES users(id), -- NULL = tin hệ thống
    content    TEXT        NOT NULL,
    type       VARCHAR(20) NOT NULL,              -- TEXT / EMOJI / SYSTEM
    is_hidden  BOOLEAN     DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- -------------------------------------------------------
-- Dữ liệu mẫu — xóa trước demo thật
-- -------------------------------------------------------
INSERT INTO users (username, password_hash, display_name, balance) VALUES
    ('admin',   'PLACEHOLDER_HASH', 'Admin',   1000000),
    ('player1', 'PLACEHOLDER_HASH', 'Người 1', 500),
    ('player2', 'PLACEHOLDER_HASH', 'Người 2', 500),
    ('player3', 'PLACEHOLDER_HASH', 'Người 3', 500),
    ('player4', 'PLACEHOLDER_HASH', 'Người 4', 500);
-- Chạy PasswordUtil.hash("password") để lấy hash thật thay PLACEHOLDER_HASH
