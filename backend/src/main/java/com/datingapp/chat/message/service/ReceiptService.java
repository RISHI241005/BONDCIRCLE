package com.datingapp.chat.message.service;

/**
 * Service managing delivery and read receipt transitions and broadcasts.
 */
public interface ReceiptService {

    /**
     * Processes a delivery acknowledgment from the recipient client.
     */
    void processDeliveryReceipt(String conversationPublicId, String messagePublicId, Long recipientUserId);

    /**
     * Processes a read receipt from a participant, watermarking read status up to messagePublicId.
     */
    void processReadReceipt(String conversationPublicId, String messagePublicId, Long readerUserId);
}
