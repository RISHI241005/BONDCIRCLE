package com.datingapp.chat.message.service.impl;

import com.datingapp.chat.message.entity.Message;
import com.datingapp.chat.message.repository.MessageRepository;
import com.datingapp.chat.message.service.IdempotencyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class IdempotencyServiceImpl implements IdempotencyService {

    private final MessageRepository messageRepository;

    public IdempotencyServiceImpl(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @Override
    public Optional<Message> findExistingMessage(Long senderId, String clientMessageId) {
        if (clientMessageId == null || clientMessageId.isBlank()) {
            return Optional.empty();
        }
        return messageRepository.findBySenderIdAndClientMessageId(senderId, clientMessageId.trim());
    }
}
