package com.datingapp.chat.block.service;

import com.datingapp.chat.block.entity.Block;
import com.datingapp.chat.block.repository.BlockRepository;
import com.datingapp.chat.block.service.impl.BlockServiceImpl;
import com.datingapp.chat.common.exception.BadRequestException;
import com.datingapp.chat.common.exception.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlockServiceTest {

    @Mock
    private BlockRepository blockRepository;

    private BlockService blockService;

    @BeforeEach
    void setUp() {
        blockService = new BlockServiceImpl(blockRepository);
    }

    @Test
    @DisplayName("Should block user successfully and save record")
    void testBlockUser() {
        Long blocker = 101L;
        Long blocked = 202L;

        when(blockRepository.existsByBlockerUserIdAndBlockedUserId(blocker, blocked)).thenReturn(false);

        blockService.blockUser(blocker, blocked);

        verify(blockRepository, times(1)).save(any(Block.class));
    }

    @Test
    @DisplayName("Should reject self-blocking with BadRequestException")
    void testSelfBlockRejection() {
        assertThrows(BadRequestException.class, () -> blockService.blockUser(101L, 101L));
    }

    @Test
    @DisplayName("Should unblock user when record exists")
    void testUnblockUser() {
        Long blocker = 101L;
        Long blocked = 202L;
        Block block = new Block(blocker, blocked);

        when(blockRepository.findByBlockerUserIdAndBlockedUserId(blocker, blocked))
                .thenReturn(Optional.of(block));

        blockService.unblockUser(blocker, blocked);

        verify(blockRepository, times(1)).delete(block);
    }

    @Test
    @DisplayName("Should throw ForbiddenException when communication is blocked")
    void testValidateCommunicationBlocked() {
        Long user1 = 101L;
        Long user2 = 202L;

        when(blockRepository.isBlockedBetween(user1, user2)).thenReturn(true);

        assertThrows(ForbiddenException.class, () -> blockService.validateCommunicationNotBlocked(user1, user2));
    }
}
