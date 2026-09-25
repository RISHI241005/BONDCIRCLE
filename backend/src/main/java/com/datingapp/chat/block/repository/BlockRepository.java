package com.datingapp.chat.block.repository;

import com.datingapp.chat.block.entity.Block;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BlockRepository extends JpaRepository<Block, Long> {

    boolean existsByBlockerUserIdAndBlockedUserId(Long blockerUserId, Long blockedUserId);

    Optional<Block> findByBlockerUserIdAndBlockedUserId(Long blockerUserId, Long blockedUserId);

    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN TRUE ELSE FALSE END FROM Block b " +
           "WHERE (b.blockerUserId = :user1 AND b.blockedUserId = :user2) " +
           "   OR (b.blockerUserId = :user2 AND b.blockedUserId = :user1)")
    boolean isBlockedBetween(@Param("user1") Long user1, @Param("user2") Long user2);
}
