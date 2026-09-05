package com.datingapp.chat.message.service.impl;

import com.datingapp.chat.common.exception.BadRequestException;
import com.datingapp.chat.common.exception.ErrorCode;
import com.datingapp.chat.common.exception.ResourceNotFoundException;
import com.datingapp.chat.conversation.entity.Conversation;
import com.datingapp.chat.conversation.entity.ConversationParticipant;
import com.datingapp.chat.conversation.repository.ConversationParticipantRepository;
import com.datingapp.chat.conversation.service.ConversationService;
import com.datingapp.chat.message.entity.Message;
import com.datingapp.chat.message.entity.MessageStatus;
import com.datingapp.chat.message.repository.MessageRepository;
import com.datingapp.chat.message.service.ReceiptService;
import com.datingapp.chat.presence.service.PresenceService;
import com.datingapp.chat.websocket.service.WebSocketBroadcastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional
public class ReceiptServiceImpl implements ReceiptService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptServiceImpl.class);

    private final MessageRepository messageRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ConversationService conversationService;
    private final WebSocketBroadcastService broadcastService;
    private final PresenceService presenceService;

    public ReceiptServiceImpl(
            MessageRepository messageRepository,
            ConversationParticipantRepository participantRepository,
            ConversationService conversationService,
            WebSocketBroadcastService broadcastService,
            PresenceService presenceService) {
        this.messageRepository = messageRepository;
        this.participantRepository = participantRepository;
        this.conversationService = conversationService;
        this.broadcastService = broadcastService;
        this.presenceService = presenceService;
    }

    @Override
    public void processDeliveryReceipt(String conversationPublicId, String messagePublicId, Long recipientUserId) {
        // 1. Validate recipient is participant in conversation
        conversationService.validateUserIsParticipant(conversationPublicId, recipientUserId);

        // 2. Fetch message and verify conversation ownership
        Message message = messageRepository.findByPublicId(messagePublicId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Message not found: " + messagePublicId, ErrorCode.MESSAGE_NOT_FOUND));

        Conversation conversation = message.getConversation();
        if (!Objects.equals(conversation.getPublicId(), conversationPublicId)) {
            throw new BadRequestException("Message does not belong to this conversation", ErrorCode.BAD_REQUEST);
        }

        // Only transition if recipient is not sender, message is currently SENT,
        // AND recipient has an active WebSocket delivery connection
        boolean recipientIsOnline = presenceService.isUserOnline(recipientUserId);
        if (!Objects.equals(message.getSenderId(), recipientUserId)
                && message.getStatus() == MessageStatus.SENT
                && recipientIsOnline) {
            message.setStatus(MessageStatus.DELIVERED);
            messageRepository.save(message);
            log.info("Message [{}] transitioned to DELIVERED by recipient {} (online)",
                    messagePublicId, recipientUserId);

            List<Long> participantUserIds = conversation.getParticipants().stream()
                    .map(ConversationParticipant::getUserId)
                    .collect(Collectors.toList());

            broadcastService.broadcastMessageStatusUpdate(
                    conversationPublicId, messagePublicId, MessageStatus.DELIVERED, recipientUserId, participantUserIds);
        }
    }

    @Override
    public void processReadReceipt(String conversationPublicId, String messagePublicId, Long readerUserId) {
        // 1. Validate reader is participant in conversation
        conversationService.validateUserIsParticipant(conversationPublicId, readerUserId);

        // 2. Fetch message and verify conversation ownership
        Message message = messageRepository.findByPublicId(messagePublicId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Message not found: " + messagePublicId, ErrorCode.MESSAGE_NOT_FOUND));

        Conversation conversation = message.getConversation();
        if (!Objects.equals(conversation.getPublicId(), conversationPublicId)) {
            throw new BadRequestException("Message does not belong to this conversation", ErrorCode.BAD_REQUEST);
        }

        // 3. Update participant's watermark lastReadMessageId
        ConversationParticipant participant = participantRepository.findByConversation_PublicIdAndUserId(
                conversationPublicId, readerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Participant not found", ErrorCode.PARTICIPANT_NOT_FOUND));

        Long currentLastRead = participant.getLastReadMessageId();
        if (currentLastRead == null || message.getId() > currentLastRead) {
            participant.setLastReadMessageId(message.getId());
            participantRepository.save(participant);
        }

        // Update target message directly
        if (!Objects.equals(message.getSenderId(), readerUserId) && 
                (message.getStatus() == MessageStatus.SENT || message.getStatus() == MessageStatus.DELIVERED)) {
            message.setStatus(MessageStatus.READ);
            messageRepository.save(message);
        }

        // 4. Bulk transition unread incoming messages up to this ID to READ status
        int updatedCount = messageRepository.markMessagesAsRead(
                conversation.getId(), message.getId(), readerUserId, MessageStatus.READ, Instant.now());
        log.info("Marked {} messages as READ up to message [{}] in conv [{}] by user {}",
                updatedCount, messagePublicId, conversationPublicId, readerUserId);

        // 5. Broadcast status update event to participants
        List<Long> participantUserIds = conversation.getParticipants().stream()
                .map(ConversationParticipant::getUserId)
                .collect(Collectors.toList());

        broadcastService.broadcastMessageStatusUpdate(
                conversationPublicId, messagePublicId, MessageStatus.READ, readerUserId, participantUserIds);
    }
}
