package com.datingapp.chat.block.service;

/**
 * Service managing user blocking logic and validation.
 */
public interface BlockService {

    /**
     * Blocks target user from communicating with blocker.
     */
    void blockUser(Long blockerUserId, Long blockedUserId);

    /**
     * Unblocks a previously blocked user.
     */
    void unblockUser(Long blockerUserId, Long blockedUserId);

    /**
     * Checks if either user has blocked the other.
     */
    boolean isCommunicationBlocked(Long user1, Long user2);

    /**
     * Validates that communication is not blocked between participants.
     * Throws ForbiddenException(USER_BLOCKED) if a block exists.
     */
    void validateCommunicationNotBlocked(Long user1, Long user2);
}
