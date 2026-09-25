package com.datingapp.chat.block.service.impl;

import com.datingapp.chat.block.entity.Block;
import com.datingapp.chat.block.repository.BlockRepository;
import com.datingapp.chat.block.service.BlockService;
import com.datingapp.chat.common.exception.BadRequestException;
import com.datingapp.chat.common.exception.ErrorCode;
import com.datingapp.chat.common.exception.ForbiddenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@Transactional
public class BlockServiceImpl implements BlockService {

    private static final Logger log = LoggerFactory.getLogger(BlockServiceImpl.class);

    private final BlockRepository blockRepository;

    public BlockServiceImpl(BlockRepository blockRepository) {
        this.blockRepository = blockRepository;
    }

    @Override
    public void blockUser(Long blockerUserId, Long blockedUserId) {
        if (Objects.equals(blockerUserId, blockedUserId)) {
            throw new BadRequestException("Users cannot block themselves", ErrorCode.BAD_REQUEST);
        }

        if (blockRepository.existsByBlockerUserIdAndBlockedUserId(blockerUserId, blockedUserId)) {
            log.info("User {} is already blocked by user {}", blockedUserId, blockerUserId);
            return;
        }

        Block block = new Block(blockerUserId, blockedUserId);
        blockRepository.save(block);
        log.info("User {} blocked user {}", blockerUserId, blockedUserId);
    }

    @Override
    public void unblockUser(Long blockerUserId, Long blockedUserId) {
        blockRepository.findByBlockerUserIdAndBlockedUserId(blockerUserId, blockedUserId)
                .ifPresent(block -> {
                    blockRepository.delete(block);
                    log.info("User {} unblocked user {}", blockerUserId, blockedUserId);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCommunicationBlocked(Long user1, Long user2) {
        if (user1 == null || user2 == null) {
            return false;
        }
        return blockRepository.isBlockedBetween(user1, user2);
    }

    @Override
    @Transactional(readOnly = true)
    public void validateCommunicationNotBlocked(Long user1, Long user2) {
        if (isCommunicationBlocked(user1, user2)) {
            throw new ForbiddenException(
                    "Cannot send message: A block exists between participants", ErrorCode.USER_BLOCKED);
        }
    }
}
