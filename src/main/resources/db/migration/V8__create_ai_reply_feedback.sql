-- V8: Create AI Reply Coach feedback table for learning user preferences
CREATE TABLE ai_reply_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    conversation_id VARCHAR(36) NOT NULL,
    suggestion_id VARCHAR(36) NOT NULL,
    suggestion_text VARCHAR(1000) NOT NULL,
    action VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT chk_feedback_action CHECK (action IN ('SHOWN','USED','REJECTED','COPIED','EDITED','SENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_feedback_user_action ON ai_reply_feedback(user_id, action, created_at DESC);
CREATE INDEX idx_feedback_conv ON ai_reply_feedback(conversation_id);
