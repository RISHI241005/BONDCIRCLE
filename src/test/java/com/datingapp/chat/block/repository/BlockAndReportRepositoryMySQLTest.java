package com.datingapp.chat.block.repository;

import com.datingapp.chat.block.entity.Block;
import com.datingapp.chat.conversation.entity.Conversation;
import com.datingapp.chat.conversation.repository.ConversationRepository;
import com.datingapp.chat.report.entity.Report;
import com.datingapp.chat.report.entity.ReportReason;
import com.datingapp.chat.report.entity.ReportStatus;
import com.datingapp.chat.report.repository.ReportRepository;
import com.datingapp.chat.testutil.AbstractMySQLIntegrationTest;
import com.datingapp.chat.testutil.ConversationTestFactory;
import com.datingapp.chat.testutil.UserTestFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BlockAndReportRepositoryMySQLTest extends AbstractMySQLIntegrationTest {

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Test
    @DisplayName("Should verify bidirectional blocking queries in MySQL")
    void testBidirectionalBlockQueries() {
        // Initially no block
        assertFalse(blockRepository.isBlockedBetween(UserTestFactory.USER_A, UserTestFactory.USER_B));

        // User A blocks User B
        blockRepository.save(new Block(UserTestFactory.USER_A, UserTestFactory.USER_B));

        // Both directions must return TRUE
        assertTrue(blockRepository.isBlockedBetween(UserTestFactory.USER_A, UserTestFactory.USER_B));
        assertTrue(blockRepository.isBlockedBetween(UserTestFactory.USER_B, UserTestFactory.USER_A));

        // Unrelated User C must return FALSE
        assertFalse(blockRepository.isBlockedBetween(UserTestFactory.USER_A, UserTestFactory.USER_C));
    }

    @Test
    @DisplayName("Should persist and retrieve moderation reports in MySQL")
    void testReportPersistence() {
        Conversation conv = conversationRepository.save(
                ConversationTestFactory.createConversation(UserTestFactory.USER_A, UserTestFactory.USER_B));

        Report report = new Report();
        report.setConversation(conv);
        report.setReporterUserId(UserTestFactory.USER_A);
        report.setReportedUserId(UserTestFactory.USER_B);
        report.setReason(ReportReason.HARASSMENT);
        report.setDescription("Detailed harassment report");
        report.setStatus(ReportStatus.PENDING);

        Report saved = reportRepository.save(report);
        assertNotNull(saved.getPublicId());

        Optional<Report> found = reportRepository.findByPublicId(saved.getPublicId());
        assertTrue(found.isPresent());
        assertEquals(ReportReason.HARASSMENT, found.get().getReason());
        assertEquals(UserTestFactory.USER_A, found.get().getReporterUserId());
        assertEquals(UserTestFactory.USER_B, found.get().getReportedUserId());
    }
}
