package com.datingapp.chat.testutil;

import com.datingapp.chat.conversation.entity.Conversation;
import com.datingapp.chat.message.dto.SendMessageRequest;
import com.datingapp.chat.message.entity.Message;
import com.datingapp.chat.message.entity.MessageStatus;
import com.datingapp.chat.message.entity.MessageType;

import java.time.Instant;
import java.util.UUID;

/**
 * Deterministic Message test factory.
 */
public class MessageTestFactory {

    public static Message createMessage(Conversation conversation, Long senderId, String content) {
        Message message = new Message();
        message.setPublicId(UUID.randomUUID().toString());
        message.setConversation(conversation);
        message.setSenderId(senderId);
        message.setContent(content);
        message.setMessageType(MessageType.TEXT);
        message.setStatus(MessageStatus.SENT);
        message.setCreatedAt(Instant.now());
        message.setUpdatedAt(Instant.now());
        return message;
    }

    public static Message createReplyMessage(Conversation conversation, Long senderId, String content, Message replyTo) {
        Message message = createMessage(conversation, senderId, content);
        message.setReplyTo(replyTo);
        return message;
    }

    public static SendMessageRequest createSendRequest(String content, String clientMessageId) {
        return new SendMessageRequest(content, clientMessageId, null);
    }

    public static SendMessageRequest createReplySendRequest(String content, String clientMessageId, String replyToMessageId) {
        return new SendMessageRequest(content, clientMessageId, replyToMessageId);
    }
}
