package com.datingapp.chat.message.service;

import com.datingapp.chat.message.entity.Message;

import java.util.Optional;

/**
 * Service for preventing duplicate message ingestion on client retry.
 */
public interface IdempotencyService {

    /**
     * Checks if a message with clientMessageId already exists for this sender.
     */
    Optional<Message> findExistingMessage(Long senderId, String clientMessageId);
}
