package com.datingapp.chat.conversation.service;

/**
 * Authorization service interface for validating whether two users
 * are allowed to initiate a direct conversation (e.g., matched in the dating system).
 */
public interface ChatAuthorizationService {

    /**
     * Verifies whether initiatorUserId is allowed to start a chat with targetUserId.
     *
     * @param initiatorUserId The user attempting to create the conversation
     * @param targetUserId    The user being invited to chat
     * @return true if communication is authorized, false otherwise
     */
    boolean canInitiateConversation(Long initiatorUserId, Long targetUserId);
}
