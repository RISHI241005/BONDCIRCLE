package com.datingapp.chat.conversation.repository;

import com.datingapp.chat.conversation.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByPublicId(String publicId);

    @Query("SELECT c FROM Conversation c JOIN c.participants p " +
           "WHERE p.userId = :userId AND p.isArchived = false " +
           "ORDER BY c.lastMessageAt DESC NULLS LAST, c.createdAt DESC")
    List<Conversation> findActiveConversationsByUserId(@Param("userId") Long userId);

    @Query("SELECT c FROM Conversation c " +
           "WHERE c.id IN (SELECT p1.conversation.id FROM ConversationParticipant p1 WHERE p1.userId = :user1Id) " +
           "AND c.id IN (SELECT p2.conversation.id FROM ConversationParticipant p2 WHERE p2.userId = :user2Id)")
    List<Conversation> findDirectConversationsBetween(@Param("user1Id") Long user1Id, @Param("user2Id") Long user2Id);
}
