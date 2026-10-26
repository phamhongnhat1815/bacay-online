USE bacay_online;

-- =====================================================
-- 1. Thêm cột role nếu chưa có
-- =====================================================

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

-- =====================================================
-- 2. Tạo bảng lưu lịch sử thao tác admin
-- =====================================================

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

-- =====================================================
-- 3. Chọn tài khoản game cần cấp quyền admin
-- =====================================================

SET @admin_username = '111';

SET @admin_id = (
    SELECT id
    FROM users
    WHERE username = @admin_username
      AND status = 'ACTIVE'
    LIMIT 1
);

-- =====================================================
-- 4. Cấp quyền bằng khóa chính, tương thích Safe Update
-- =====================================================

UPDATE users
SET role = 'ADMIN'
WHERE id = CAST(@admin_id AS UNSIGNED)
  AND status = 'ACTIVE'
LIMIT 1;

-- =====================================================
-- 5. Hiển thị kết quả
-- =====================================================

SELECT
    id,
    username,
    role,
    status,
    CASE
        WHEN role = 'ADMIN' AND status = 'ACTIVE'
            THEN 'Thanh cong: dang nhap lai game de dung quyen admin'
        WHEN status <> 'ACTIVE'
            THEN 'Tai khoan dang bi khoa'
        ELSE 'Chua duoc cap quyen admin'
    END AS ket_qua
FROM users
WHERE username = @admin_username;

SELECT
    CASE
        WHEN EXISTS (
            SELECT 1
            FROM users
            WHERE username = @admin_username
        )
            THEN 'Da tim thay tai khoan'
        ELSE 'Khong tim thay tai khoan game 111: can dang ky truoc'
    END AS kiem_tra_tai_khoan;