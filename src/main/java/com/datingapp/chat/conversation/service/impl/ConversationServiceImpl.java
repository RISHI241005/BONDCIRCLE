package com.datingapp.chat.conversation.service.impl;

import com.datingapp.chat.common.exception.ErrorCode;
import com.datingapp.chat.common.exception.ForbiddenException;
import com.datingapp.chat.common.exception.ResourceNotFoundException;
import com.datingapp.chat.conversation.dto.ConversationDetailResponse;
import com.datingapp.chat.conversation.dto.ConversationSummaryResponse;
import com.datingapp.chat.conversation.dto.ParticipantResponse;
import com.datingapp.chat.conversation.entity.Conversation;
import com.datingapp.chat.conversation.service.ChatAuthorizationService;
import com.datingapp.chat.conversation.service.ConversationService;
import com.datingapp.chat.conversation.entity.ConversationParticipant;
import com.datingapp.chat.conversation.entity.ConversationStatus;
import com.datingapp.chat.conversation.repository.ConversationParticipantRepository;
import com.datingapp.chat.conversation.repository.ConversationRepository;
import com.datingapp.chat.presence.model.PresenceStatus;
import com.datingapp.chat.presence.service.PresenceService;
import com.datingapp.chat.message.repository.MessageRepository;
import com.datingapp.chat.security.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ConversationServiceImpl implements ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationServiceImpl.class);

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ChatAuthorizationService chatAuthorizationService;
    private final UserRepository userRepository;
    private final PresenceService presenceService;
    private final com.datingapp.chat.message.repository.MessageRepository messageRepository;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    public ConversationServiceImpl(
            ConversationRepository conversationRepository,
            ConversationParticipantRepository participantRepository,
            ChatAuthorizationService chatAuthorizationService,
            UserRepository userRepository,
            PresenceService presenceService,
            com.datingapp.chat.message.repository.MessageRepository messageRepository,
            org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.chatAuthorizationService = chatAuthorizationService;
        this.userRepository = userRepository;
        this.presenceService = presenceService;
        this.messageRepository = messageRepository;
        this.transactionTemplate = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationSummaryResponse> getUserConversations(Long userId) {
        List<Conversation> conversations = conversationRepository.findActiveConversationsByUserId(userId);
        List<ConversationSummaryResponse> summaries = new ArrayList<>();

        for (Conversation conv : conversations) {
            ConversationParticipant myParticipant = conv.getParticipants().stream()
                    .filter(p -> Objects.equals(p.getUserId(), userId))
                    .findFirst()
                    .orElse(null);

            Long otherUserId = conv.getParticipants().stream()
                    .map(ConversationParticipant::getUserId)
                    .filter(pUserId -> !Objects.equals(pUserId, userId))
                    .findFirst()
                    .orElse(null);

            // Get other user details
            String otherUserName = null;
            String otherUserPhone = null;
            if (otherUserId != null) {
                var otherUser = userRepository.findById(otherUserId);
                if (otherUser.isPresent()) {
                    otherUserName = otherUser.get().getFullName();
                    otherUserPhone = otherUser.get().getPhone();
                }
            }

            // Get other user online status
            String otherUserOnlineStatus = "OFFLINE";
            if (otherUserId != null) {
                var presence = presenceService.getUserPresence(otherUserId, userId);
                otherUserOnlineStatus = presence.getStatus().toString();
            }

            long unreadCount = 0;
            if (myParticipant != null) {
                unreadCount = messageRepository.countUnreadMessages(
                        conv.getId(), myParticipant.getLastReadMessageId(), userId);
            }

            // Get last message content
            String lastMessageContent = null;
            var lastMessages = messageRepository.findInitialMessagesByConversationId(conv.getId(), null);
            if (lastMessages != null && !lastMessages.isEmpty()) {
                var lastMsg = lastMessages.get(0);
                lastMessageContent = lastMsg.getContent();
            }

            summaries.add(new ConversationSummaryResponse(
                    conv.getPublicId(),
                    otherUserId,
                    otherUserName,
                    otherUserPhone,
                    otherUserOnlineStatus,
                    lastMessageContent,
                    conv.getStatus(),
                    unreadCount,
                    myParticipant != null && myParticipant.isMuted(),
                    conv.getLastMessageAt(),
                    conv.getUpdatedAt()
            ));
        }

        return summaries;
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationDetailResponse getConversationDetails(String publicId, Long userId) {
        Conversation conversation = getConversationEntity(publicId);

        boolean isParticipant = conversation.getParticipants().stream()
                .anyMatch(p -> Objects.equals(p.getUserId(), userId));

        if (!isParticipant) {
            throw new ForbiddenException("You are not a participant in this conversation", ErrorCode.CHAT_ACCESS_DENIED);
        }

        return mapToDetailResponse(conversation);
    }

    @Override
    public synchronized ConversationDetailResponse createOrGetConversation(Long currentUserId, Long targetUserId) {
        if (!chatAuthorizationService.canInitiateConversation(currentUserId, targetUserId)) {
            throw new ForbiddenException("Cannot initiate conversation with this user", ErrorCode.MATCH_REQUIRED);
        }

        return transactionTemplate.execute(status -> {
            // Check if an existing direct conversation already exists between the two users
            List<Conversation> existing = conversationRepository.findDirectConversationsBetween(currentUserId, targetUserId);
            if (!existing.isEmpty()) {
                Conversation existingConv = existing.getFirst();
                log.info("Found existing direct conversation [{}] between users {} and {}",
                        existingConv.getPublicId(), currentUserId, targetUserId);
                return mapToDetailResponse(existingConv);
            }

            log.info("Creating new conversation between user {} and user {}", currentUserId, targetUserId);
            Conversation conversation = new Conversation();
            conversation.setStatus(ConversationStatus.ACTIVE);

            ConversationParticipant participant1 = new ConversationParticipant(conversation, currentUserId);
            ConversationParticipant participant2 = new ConversationParticipant(conversation, targetUserId);

            conversation.addParticipant(participant1);
            conversation.addParticipant(participant2);

            Conversation saved = conversationRepository.save(conversation);
            return mapToDetailResponse(saved);
        });
    }

    @Override
    @Transactional
    public void leaveOrArchiveConversation(String publicId, Long userId) {
        ConversationParticipant participant = participantRepository.findByConversation_PublicIdAndUserId(publicId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Participant not found in conversation", ErrorCode.PARTICIPANT_NOT_FOUND));

        participant.setArchived(true);
        participantRepository.save(participant);
        log.info("User {} archived conversation [{}]", userId, publicId);
    }

    @Override
    @Transactional(readOnly = true)
    public Conversation getConversationEntity(String publicId) {
        return conversationRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Conversation not found: " + publicId, ErrorCode.CONVERSATION_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public void validateUserIsParticipant(String publicId, Long userId) {
        boolean exists = participantRepository.existsByConversation_PublicIdAndUserId(publicId, userId);
        if (!exists) {
            throw new ForbiddenException(
                    "You are not an authorized participant in this conversation", ErrorCode.CHAT_ACCESS_DENIED);
        }
    }

    private ConversationDetailResponse mapToDetailResponse(Conversation conv) {
        List<ParticipantResponse> participantResponses = conv.getParticipants().stream()
                .map(p -> new ParticipantResponse(
                        p.getUserId(),
                        p.getJoinedAt(),
                        p.isMuted(),
                        p.isArchived()))
                .collect(Collectors.toList());

        return new ConversationDetailResponse(
                conv.getPublicId(),
                conv.getStatus(),
                conv.getCreatedAt(),
                conv.getUpdatedAt(),
                participantResponses
        );
    }
}
