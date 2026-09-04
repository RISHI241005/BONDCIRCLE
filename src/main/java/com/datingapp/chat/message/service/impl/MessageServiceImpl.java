package com.datingapp.chat.message.service.impl;

import com.datingapp.chat.common.exception.BadRequestException;
import com.datingapp.chat.common.exception.ErrorCode;
import com.datingapp.chat.common.exception.ResourceNotFoundException;
import com.datingapp.chat.common.util.CursorUtils;
import com.datingapp.chat.config.RateLimitProperties;
import com.datingapp.chat.conversation.entity.Conversation;
import com.datingapp.chat.conversation.repository.ConversationRepository;
import com.datingapp.chat.conversation.service.ConversationService;
import com.datingapp.chat.message.dto.MessageCursorPage;
import com.datingapp.chat.message.dto.MessageResponse;
import com.datingapp.chat.message.dto.ReplyMessageSnippet;
import com.datingapp.chat.message.dto.SendMessageRequest;
import com.datingapp.chat.message.entity.Message;
import com.datingapp.chat.message.entity.MessageStatus;
import com.datingapp.chat.message.entity.MessageType;
import com.datingapp.chat.message.repository.MessageRepository;
import com.datingapp.chat.message.service.IdempotencyService;
import com.datingapp.chat.message.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class MessageServiceImpl implements MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageServiceImpl.class);

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final IdempotencyService idempotencyService;
    private final RateLimitProperties rateLimitProperties;
    private final com.datingapp.chat.websocket.service.WebSocketBroadcastService webSocketBroadcastService;
    private final com.datingapp.chat.block.service.BlockService blockService;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    public MessageServiceImpl(
            MessageRepository messageRepository,
            ConversationRepository conversationRepository,
            ConversationService conversationService,
            IdempotencyService idempotencyService,
            RateLimitProperties rateLimitProperties,
            com.datingapp.chat.websocket.service.WebSocketBroadcastService webSocketBroadcastService,
            com.datingapp.chat.block.service.BlockService blockService,
            org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.conversationService = conversationService;
        this.idempotencyService = idempotencyService;
        this.rateLimitProperties = rateLimitProperties;
        this.webSocketBroadcastService = webSocketBroadcastService;
        this.blockService = blockService;
        this.transactionTemplate = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    }

    @Override
    public synchronized MessageResponse sendMessage(String conversationPublicId, Long senderId, SendMessageRequest request) {
        return transactionTemplate.execute(txStatus -> {
            // 1. Authorize sender participation
            conversationService.validateUserIsParticipant(conversationPublicId, senderId);

            // 2. Validate payload content constraints
            if (request.getContent() == null || request.getContent().trim().isEmpty()) {
                throw new BadRequestException("Message content cannot be empty", ErrorCode.EMPTY_MESSAGE_CONTENT);
            }
            if (request.getContent().length() > rateLimitProperties.getMaxLength()) {
                throw new BadRequestException(
                        "Message exceeds maximum allowed character length of " + rateLimitProperties.getMaxLength(),
                        ErrorCode.MESSAGE_TOO_LONG);
            }

            // 3. Idempotency check: verify if clientMessageId already processed for this sender
            if (request.getClientMessageId() != null && !request.getClientMessageId().isBlank()) {
                var existingMessage = idempotencyService.findExistingMessage(senderId, request.getClientMessageId());
                if (existingMessage.isPresent()) {
                    log.info("Idempotent retry detected for clientMessageId [{}]. Returning existing message [{}]",
                            request.getClientMessageId(), existingMessage.get().getPublicId());
                    return mapToResponse(existingMessage.get());
                }
            }

            // 4. Retrieve conversation container
            Conversation conversation = conversationService.getConversationEntity(conversationPublicId);

            // 5. Block Validation: verify no block exists between sender and other participant
            for (com.datingapp.chat.conversation.entity.ConversationParticipant p : conversation.getParticipants()) {
                if (!Objects.equals(p.getUserId(), senderId)) {
                    blockService.validateCommunicationNotBlocked(senderId, p.getUserId());
                }
            }

            // 6. Reply-to validation if present
            Message repliedMessage = null;
            if (request.getReplyToMessageId() != null && !request.getReplyToMessageId().isBlank()) {
                repliedMessage = getMessageEntity(request.getReplyToMessageId());
                if (!Objects.equals(repliedMessage.getConversation().getId(), conversation.getId())) {
                    throw new BadRequestException(
                            "Replied message does not belong to this conversation", ErrorCode.BAD_REQUEST);
                }
            }

            // 7. Build and persist message
            Message message = new Message();
            message.setConversation(conversation);
            message.setSenderId(senderId);
            message.setClientMessageId(request.getClientMessageId() != null ? request.getClientMessageId().trim() : null);
            message.setContent(request.getContent().trim());
            message.setMessageType(request.getType() != null ? request.getType() : MessageType.TEXT);
            message.setStatus(MessageStatus.SENT);
            message.setReplyTo(repliedMessage);

            Message savedMessage = messageRepository.save(message);

            // 8. Update conversation last message pointer
            conversation.setLastMessageId(savedMessage.getId());
            conversation.setLastMessageAt(savedMessage.getCreatedAt());
            conversationRepository.save(conversation);

            log.info("Persisted message [{}] in conversation [{}] from sender {}",
                    savedMessage.getPublicId(), conversationPublicId, senderId);

            MessageResponse response = mapToResponse(savedMessage);

            // 9. Broadcast real-time event to all conversation participants
            List<Long> participantUserIds = conversation.getParticipants().stream()
                    .map(com.datingapp.chat.conversation.entity.ConversationParticipant::getUserId)
                    .collect(Collectors.toList());
            webSocketBroadcastService.broadcastNewMessage(conversationPublicId, response, participantUserIds);

            return response;
        });
    }

    @Override
    @Transactional
    public MessageResponse editMessage(
            String conversationPublicId,
            String messagePublicId,
            Long userId,
            com.datingapp.chat.message.dto.EditMessageRequest request) {
        conversationService.validateUserIsParticipant(conversationPublicId, userId);

        Message message = getMessageEntity(messagePublicId);
        if (!Objects.equals(message.getConversation().getPublicId(), conversationPublicId)) {
            throw new BadRequestException("Message does not belong to this conversation", ErrorCode.BAD_REQUEST);
        }

        if (!Objects.equals(message.getSenderId(), userId)) {
            throw new com.datingapp.chat.common.exception.ForbiddenException(
                    "You can only edit your own messages", ErrorCode.NOT_MESSAGE_OWNER);
        }

        if (message.isDeleted()) {
            throw new BadRequestException("Cannot edit a deleted message", ErrorCode.BAD_REQUEST);
        }

        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new BadRequestException("Message content cannot be blank", ErrorCode.EMPTY_MESSAGE_CONTENT);
        }

        message.setContent(request.getContent().trim());
        message.setStatus(MessageStatus.EDITED);
        Message saved = messageRepository.save(message);
        log.info("Message [{}] edited by sender {}", messagePublicId, userId);

        MessageResponse response = mapToResponse(saved);

        List<Long> participantUserIds = message.getConversation().getParticipants().stream()
                .map(com.datingapp.chat.conversation.entity.ConversationParticipant::getUserId)
                .collect(Collectors.toList());
        webSocketBroadcastService.broadcastMessageStatusUpdate(
                conversationPublicId, messagePublicId, MessageStatus.EDITED, userId, participantUserIds);

        return response;
    }

    @Override
    @Transactional
    public MessageResponse deleteMessage(String conversationPublicId, String messagePublicId, Long userId) {
        conversationService.validateUserIsParticipant(conversationPublicId, userId);

        Message message = getMessageEntity(messagePublicId);
        if (!Objects.equals(message.getConversation().getPublicId(), conversationPublicId)) {
            throw new BadRequestException("Message does not belong to this conversation", ErrorCode.BAD_REQUEST);
        }

        if (!Objects.equals(message.getSenderId(), userId)) {
            throw new com.datingapp.chat.common.exception.ForbiddenException(
                    "You can only delete your own messages", ErrorCode.NOT_MESSAGE_OWNER);
        }

        if (!message.isDeleted()) {
            message.setDeletedAt(java.time.Instant.now());
            message.setStatus(MessageStatus.DELETED);
            messageRepository.save(message);
            log.info("Message [{}] soft-deleted by sender {}", messagePublicId, userId);

            List<Long> participantUserIds = message.getConversation().getParticipants().stream()
                    .map(com.datingapp.chat.conversation.entity.ConversationParticipant::getUserId)
                    .collect(Collectors.toList());
            webSocketBroadcastService.broadcastMessageStatusUpdate(
                    conversationPublicId, messagePublicId, MessageStatus.DELETED, userId, participantUserIds);
        }

        return mapToResponse(message);
    }

    @Override
    @Transactional(readOnly = true)
    public MessageCursorPage getMessageHistory(String conversationPublicId, Long userId, String cursor, int limit) {
        // 1. Authorize user membership
        conversationService.validateUserIsParticipant(conversationPublicId, userId);

        int effectiveLimit = limit > 0
                ? Math.min(limit, rateLimitProperties.getMaxPageSize())
                : rateLimitProperties.getDefaultPageSize();

        Conversation conversation = conversationService.getConversationEntity(conversationPublicId);

        // Fetch (effectiveLimit + 1) to determine hasMore
        PageRequest pageRequest = PageRequest.of(0, effectiveLimit + 1);
        List<Message> results;

        if (cursor == null || cursor.isBlank()) {
            results = messageRepository.findInitialMessagesByConversationId(conversation.getId(), pageRequest);
        } else {
            CursorUtils.CursorPayload cursorPayload = CursorUtils.decodeCursor(cursor);
            results = messageRepository.findMessagesBeforeCursor(
                    conversation.getId(),
                    cursorPayload.getCreatedAt(),
                    cursorPayload.getId(),
                    pageRequest
            );
        }

        boolean hasMore = results.size() > effectiveLimit;
        List<Message> pageItems = hasMore ? results.subList(0, effectiveLimit) : results;

        String nextCursor = null;
        if (hasMore && !pageItems.isEmpty()) {
            Message lastItem = pageItems.get(pageItems.size() - 1);
            nextCursor = CursorUtils.encodeCursor(lastItem.getCreatedAt(), lastItem.getId());
        }

        List<MessageResponse> dtos = pageItems.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return new MessageCursorPage(dtos, nextCursor, hasMore, effectiveLimit);
    }

    @Override
    @Transactional(readOnly = true)
    public Message getMessageEntity(String messagePublicId) {
        return messageRepository.findByPublicId(messagePublicId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Message not found: " + messagePublicId, ErrorCode.MESSAGE_NOT_FOUND));
    }

    @Override
    public MessageResponse mapToResponse(Message message) {
        ReplyMessageSnippet replySnippet = null;
        if (message.getReplyTo() != null) {
            Message parent = message.getReplyTo();
            String snippetContent = parent.isDeleted() ? "This message was deleted." : parent.getContent();
            replySnippet = new ReplyMessageSnippet(
                    parent.getPublicId(),
                    parent.getSenderId(),
                    snippetContent,
                    parent.getMessageType()
            );
        }

        String renderedContent = message.isDeleted() ? "This message was deleted." : message.getContent();

        return new MessageResponse(
                message.getPublicId(),
                message.getConversation().getPublicId(),
                message.getSenderId(),
                message.getClientMessageId(),
                renderedContent,
                message.getMessageType(),
                message.getStatus(),
                replySnippet,
                message.getCreatedAt(),
                message.getUpdatedAt(),
                message.isDeleted()
        );
    }
}
