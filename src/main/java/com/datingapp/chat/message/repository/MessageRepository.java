package com.datingapp.chat.message.repository;

import com.datingapp.chat.message.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    Optional<Message> findByPublicId(String publicId);

    Optional<Message> findBySenderIdAndClientMessageId(Long senderId, String clientMessageId);

    @Query("SELECT m FROM Message m LEFT JOIN FETCH m.replyTo " +
           "WHERE m.conversation.id = :conversationId " +
           "ORDER BY m.createdAt DESC, m.id DESC")
    List<Message> findInitialMessagesByConversationId(
            @Param("conversationId") Long conversationId,
            Pageable pageable);

    @Query("SELECT m FROM Message m LEFT JOIN FETCH m.replyTo " +
           "WHERE m.conversation.id = :conversationId " +
           "AND (m.createdAt < :cursorCreatedAt OR (m.createdAt = :cursorCreatedAt AND m.id < :cursorId)) " +
           "ORDER BY m.createdAt DESC, m.id DESC")
    List<Message> findMessagesBeforeCursor(
            @Param("conversationId") Long conversationId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    @Query("SELECT COUNT(m) FROM Message m " +
           "WHERE m.conversation.id = :conversationId " +
           "AND (:lastReadId IS NULL OR m.id > :lastReadId) " +
           "AND m.senderId != :userId")
    long countUnreadMessages(
            @Param("conversationId") Long conversationId,
            @Param("lastReadId") Long lastReadId,
            @Param("userId") Long userId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Message m SET m.status = :newStatus, m.updatedAt = :now " +
           "WHERE m.conversation.id = :conversationId AND m.id <= :upToMessageId " +
           "AND m.senderId != :readerUserId AND (m.status = com.datingapp.chat.message.entity.MessageStatus.SENT OR m.status = com.datingapp.chat.message.entity.MessageStatus.DELIVERED)")
    int markMessagesAsRead(
            @Param("conversationId") Long conversationId,
            @Param("upToMessageId") Long upToMessageId,
            @Param("readerUserId") Long readerUserId,
            @Param("newStatus") com.datingapp.chat.message.entity.MessageStatus newStatus,
            @Param("now") Instant now);
}
