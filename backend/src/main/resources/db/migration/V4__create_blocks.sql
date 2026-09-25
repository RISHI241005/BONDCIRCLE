-- V4: Create blocks table
CREATE TABLE blocks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    blocker_user_id BIGINT NOT NULL,
    blocked_user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_blocks_pair UNIQUE (blocker_user_id, blocked_user_id),
    CONSTRAINT chk_blocks_no_self_block CHECK (blocker_user_id <> blocked_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_blocks_blocked_user ON blocks(blocked_user_id);
