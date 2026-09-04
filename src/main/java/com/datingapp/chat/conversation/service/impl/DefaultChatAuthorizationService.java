package com.datingapp.chat.conversation.service.impl;

import com.datingapp.chat.conversation.service.ChatAuthorizationService;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Default implementation of ChatAuthorizationService.
 * Validates non-self communication and can be extended to verify matches via RPC/Feign/DB.
 */
@Service
public class DefaultChatAuthorizationService implements ChatAuthorizationService {

    @Override
    public boolean canInitiateConversation(Long initiatorUserId, Long targetUserId) {
        if (initiatorUserId == null || targetUserId == null) {
            return false;
        }
        // Basic dating rule: users cannot start a conversation with themselves
        return !Objects.equals(initiatorUserId, targetUserId);
    }
}
