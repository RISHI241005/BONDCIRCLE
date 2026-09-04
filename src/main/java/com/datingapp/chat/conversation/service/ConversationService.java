package com.datingapp.chat.conversation.service;

import com.datingapp.chat.conversation.dto.ConversationDetailResponse;
import com.datingapp.chat.conversation.dto.ConversationSummaryResponse;
import com.datingapp.chat.conversation.entity.Conversation;

import java.util.List;

/**
 * Service handling conversation lifecycle, participant memberships, and authorization.
 */
public interface ConversationService {

    /**
     * Retrieves all active conversation summaries for a user with unread counts.
     */
    List<ConversationSummaryResponse> getUserConversations(Long userId);

    /**
     * Retrieves detailed metadata and participant list for a specific conversation.
     */
    ConversationDetailResponse getConversationDetails(String publicId, Long userId);

    /**
     * Creates a new direct conversation or returns an existing active one.
     */
    ConversationDetailResponse createOrGetConversation(Long currentUserId, Long participantId);

    /**
     * Archives or leaves a conversation for the requesting participant.
     */
    void leaveOrArchiveConversation(String publicId, Long userId);

    /**
     * Fetches the internal JPA Conversation entity by public UUID.
     */
    Conversation getConversationEntity(String publicId);

    /**
     * Validates that the specified user is an active participant of the conversation.
     * Throws ForbiddenException if not a participant.
     */
    void validateUserIsParticipant(String publicId, Long userId);
}
