USE bacay_online;

DROP PROCEDURE IF EXISTS bacay_add_role;

DELIMITER $$

CREATE PROCEDURE bacay_add_role()
SQL SECURITY INVOKER
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'users'
          AND column_name = 'role'
    ) THEN
        ALTER TABLE users
        ADD COLUMN role VARCHAR(10) NOT NULL DEFAULT 'PLAYER';
    END IF;
END$$

DELIMITER ;

CALL bacay_add_role();
DROP PROCEDURE bacay_add_role;

CREATE TABLE IF NOT EXISTS admin_audit (
    request_id VARCHAR(64) PRIMARY KEY,
    actor_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL,
    amount DECIMAL(15,2) NULL,
    balance_before DECIMAL(15,2) NOT NULL,
    balance_after DECIMAL(15,2) NOT NULL,
    details VARCHAR(128) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (actor_id) REFERENCES users(id),
    FOREIGN KEY (target_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ĐỔI thành tên tài khoản GAME đã tồn tại của bạn.
SET @admin_username = 'thanh_admin';

UPDATE users
SET role = 'ADMIN'
WHERE username = @admin_username
  AND status = 'ACTIVE';

SELECT id, username, role, status
FROM users
WHERE username = @admin_username;