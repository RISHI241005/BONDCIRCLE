package com.datingapp.chat.conversation.repository;

import com.datingapp.chat.conversation.entity.ConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, Long> {

    Optional<ConversationParticipant> findByConversationIdAndUserId(Long conversationId, Long userId);

    Optional<ConversationParticipant> findByConversation_PublicIdAndUserId(String conversationPublicId, Long userId);

    List<ConversationParticipant> findByConversationId(Long conversationId);

    List<ConversationParticipant> findByConversation_PublicId(String conversationPublicId);

    boolean existsByConversation_PublicIdAndUserId(String conversationPublicId, Long userId);

    @Query("SELECT p.userId FROM ConversationParticipant p WHERE p.conversation.publicId = :publicId AND p.userId != :userId")
    List<Long> findOtherParticipantUserIds(@Param("publicId") String publicId, @Param("userId") Long userId);
}
