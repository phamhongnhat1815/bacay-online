SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS bacay_online CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE bacay_online;

CREATE TABLE IF NOT EXISTS users (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 username VARCHAR(50) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
 password_hash VARCHAR(255) NOT NULL,
 display_name VARCHAR(100) NOT NULL,
 email VARCHAR(100) NULL,
 avatar_url VARCHAR(255) NULL,
 balance DECIMAL(15,2) NOT NULL DEFAULT 1000.00,
 status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
 role VARCHAR(20) NOT NULL DEFAULT 'USER',
 total_games INT NOT NULL DEFAULT 0,
 total_wins INT NOT NULL DEFAULT 0,
 total_losses INT NOT NULL DEFAULT 0,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 CONSTRAINT uq_users_username UNIQUE(username),
 CONSTRAINT uq_users_email UNIQUE(email),
 CONSTRAINT ck_users_balance CHECK(balance >= 0),
 CONSTRAINT ck_users_status CHECK(status IN ('ACTIVE','BLOCKED')),
 CONSTRAINT ck_users_role CHECK(role IN ('USER','ADMIN','MODERATOR')),
 CONSTRAINT ck_users_stats CHECK(total_games>=0 AND total_wins>=0 AND total_losses>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS app_migrations (name VARCHAR(100) PRIMARY KEY) ENGINE=InnoDB;

DROP PROCEDURE IF EXISTS bacay_migration_add_role;
DELIMITER $$
CREATE PROCEDURE bacay_migration_add_role()
BEGIN
 IF NOT EXISTS (
  SELECT 1 FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'role'
 ) THEN
  ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER' AFTER status;
  ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK(role IN ('USER','ADMIN','MODERATOR'));
 END IF;
END$$
DELIMITER ;
CALL bacay_migration_add_role();
DROP PROCEDURE IF EXISTS bacay_migration_add_role;

-- Administrator credentials are generated securely by AdminBootstrap on server startup.
-- No shared default password is installed by SQL.

CREATE TABLE IF NOT EXISTS play_rounds (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 round_id VARCHAR(64) NOT NULL UNIQUE,
 content_hash VARCHAR(64) NOT NULL,
 runtime_room_id BIGINT NOT NULL,
 room_name VARCHAR(100) NOT NULL,
 ante DECIMAL(15,2) NOT NULL,
 status VARCHAR(20) NOT NULL,
 ended_at BIGINT NOT NULL,
 CHECK (status IN ('FINISHED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS play_results (
 round_id VARCHAR(64) NOT NULL,
 user_id BIGINT NOT NULL,
 display_name VARCHAR(100) NOT NULL,
 placement INT NOT NULL,
 outcome VARCHAR(20) NOT NULL,
 profit DECIMAL(15,2) NOT NULL,
 hand_type VARCHAR(20), score INT,
 PRIMARY KEY (round_id,user_id),
 FOREIGN KEY (round_id) REFERENCES play_rounds(round_id),
 FOREIGN KEY (user_id) REFERENCES users(id),
 INDEX ix_play_history(user_id,round_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS play_cards (
 round_id VARCHAR(64) NOT NULL, user_id BIGINT NOT NULL,
 position_no INT NOT NULL, rank_name VARCHAR(10) NOT NULL, suit_name VARCHAR(10) NOT NULL,
 PRIMARY KEY(round_id,user_id,position_no), UNIQUE(round_id,rank_name,suit_name),
 FOREIGN KEY(round_id,user_id) REFERENCES play_results(round_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS play_bets (
 round_id VARCHAR(64) NOT NULL, sequence_no INT NOT NULL, user_id BIGINT NOT NULL,
 action VARCHAR(20) NOT NULL, total_bet DECIMAL(15,2) NOT NULL,
 added_amount DECIMAL(15,2) NOT NULL, created_at BIGINT NOT NULL,
 PRIMARY KEY(round_id,sequence_no),
 FOREIGN KEY(round_id,user_id) REFERENCES play_results(round_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS play_pots (
 round_id VARCHAR(64) NOT NULL, pot_no INT NOT NULL, amount DECIMAL(15,2) NOT NULL,
 PRIMARY KEY(round_id,pot_no), FOREIGN KEY(round_id) REFERENCES play_rounds(round_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS play_pot_players (
 round_id VARCHAR(64) NOT NULL, pot_no INT NOT NULL, user_id BIGINT NOT NULL,
 PRIMARY KEY(round_id,pot_no,user_id),
 FOREIGN KEY(round_id,pot_no) REFERENCES play_pots(round_id,pot_no),
 FOREIGN KEY(round_id,user_id) REFERENCES play_results(round_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS play_ledger (
 round_id VARCHAR(64) NOT NULL,user_id BIGINT NOT NULL,
 delta DECIMAL(15,2) NOT NULL,balance_before DECIMAL(15,2) NOT NULL,balance_after DECIMAL(15,2) NOT NULL,
 PRIMARY KEY(round_id,user_id),
 FOREIGN KEY(round_id,user_id) REFERENCES play_results(round_id,user_id),
 CHECK(balance_after>=0), CHECK(balance_after=balance_before+delta)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS admin_adjustments (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 adjustment_id VARCHAR(64) NOT NULL UNIQUE,
 admin_user_id BIGINT NOT NULL,
 target_user_id BIGINT NOT NULL,
 delta DECIMAL(15,2) NOT NULL,
 balance_before DECIMAL(15,2) NOT NULL,
 balance_after DECIMAL(15,2) NOT NULL,
 reason VARCHAR(255) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(target_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP PROCEDURE IF EXISTS bacay_starting_points;
DELIMITER $$
CREATE PROCEDURE bacay_starting_points()
SQL SECURITY INVOKER
BEGIN
 DECLARE old_safe INT DEFAULT 1;
 DECLARE apply_grant INT DEFAULT 0;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION
 BEGIN
  ROLLBACK;
  SET SESSION SQL_SAFE_UPDATES = old_safe;
  RESIGNAL;
 END;
 SET old_safe = @@SESSION.sql_safe_updates;
 SET SESSION SQL_SAFE_UPDATES = 0;
 START TRANSACTION;
 INSERT IGNORE INTO app_migrations(name) VALUES ('product_starting_points_v1');
 SET apply_grant = ROW_COUNT();
 IF apply_grant = 1 THEN
  UPDATE users SET balance = 1000.00 WHERE balance = 0.00 AND role = 'USER';
 END IF;
 INSERT IGNORE INTO app_migrations(name) VALUES ('m5_initial_points');
 COMMIT;
 SET SESSION SQL_SAFE_UPDATES = old_safe;
END$$
DELIMITER ;
CALL bacay_starting_points();
DROP PROCEDURE IF EXISTS bacay_starting_points;

INSERT IGNORE INTO app_migrations(name) VALUES ('schema_v2_secure_admin');
