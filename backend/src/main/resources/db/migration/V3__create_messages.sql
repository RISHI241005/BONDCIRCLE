-- V3: Create messages table
CREATE TABLE messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    client_message_id VARCHAR(100) NULL,
    message_type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    status VARCHAR(20) NOT NULL DEFAULT 'SENT',
    content TEXT NOT NULL,
    reply_to_message_id BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    CONSTRAINT uk_messages_public_id UNIQUE (public_id),
    CONSTRAINT uk_messages_sender_client_msg UNIQUE (sender_id, client_message_id),
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_reply_to FOREIGN KEY (reply_to_message_id) REFERENCES messages(id) ON DELETE SET NULL,
    CONSTRAINT chk_messages_type CHECK (message_type IN ('TEXT', 'SYSTEM', 'IMAGE', 'VIDEO', 'AUDIO', 'FILE')),
    CONSTRAINT chk_messages_status CHECK (status IN ('SENT', 'DELIVERED', 'READ', 'EDITED', 'DELETED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- High performance composite index for cursor-based backward pagination
CREATE INDEX idx_messages_conv_created_id ON messages(conversation_id, created_at DESC, id DESC);
CREATE INDEX idx_messages_sender_id ON messages(sender_id);
CREATE INDEX idx_messages_conv_unread ON messages(conversation_id, id);
