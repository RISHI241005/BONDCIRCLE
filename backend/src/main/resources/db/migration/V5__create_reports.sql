-- V5: Create reports table
CREATE TABLE reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL,
    conversation_id BIGINT NOT NULL,
    reporter_user_id BIGINT NOT NULL,
    reported_user_id BIGINT NOT NULL,
    message_id BIGINT NULL,
    reason VARCHAR(30) NOT NULL,
    description VARCHAR(1000) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_reports_public_id UNIQUE (public_id),
    CONSTRAINT fk_reports_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    CONSTRAINT fk_reports_message FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE SET NULL,
    CONSTRAINT chk_reports_reason CHECK (reason IN ('SPAM', 'HARASSMENT', 'SCAM', 'THREATS', 'INAPPROPRIATE_CONTENT', 'OTHER')),
    CONSTRAINT chk_reports_status CHECK (status IN ('PENDING', 'REVIEWED', 'DISMISSED', 'ACTIONED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_reports_conversation_id ON reports(conversation_id);
CREATE INDEX idx_reports_status ON reports(status);
