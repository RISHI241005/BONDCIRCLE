package com.datingapp.chat.report.service;

import com.datingapp.chat.conversation.entity.Conversation;
import com.datingapp.chat.conversation.entity.ConversationParticipant;
import com.datingapp.chat.conversation.service.ConversationService;
import com.datingapp.chat.message.entity.Message;
import com.datingapp.chat.message.repository.MessageRepository;
import com.datingapp.chat.report.dto.CreateReportRequest;
import com.datingapp.chat.report.dto.ReportResponse;
import com.datingapp.chat.report.entity.Report;
import com.datingapp.chat.report.entity.ReportReason;
import com.datingapp.chat.report.entity.ReportStatus;
import com.datingapp.chat.report.repository.ReportRepository;
import com.datingapp.chat.report.service.impl.ReportServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ConversationService conversationService;

    @Mock
    private MessageRepository messageRepository;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportServiceImpl(reportRepository, conversationService, messageRepository);
    }

    @Test
    @DisplayName("Should submit moderation report on message successfully")
    void testCreateReportOnMessage() {
        String convId = "conv-uuid-1";
        String msgId = "msg-uuid-1";
        Long reporterId = 101L;
        Long reportedId = 202L;

        Conversation conv = new Conversation();
        conv.setId(1L);
        conv.setPublicId(convId);
        conv.addParticipant(new ConversationParticipant(conv, reporterId));
        conv.addParticipant(new ConversationParticipant(conv, reportedId));

        Message msg = new Message();
        msg.setId(10L);
        msg.setPublicId(msgId);
        msg.setConversation(conv);
        msg.setSenderId(reportedId);

        when(conversationService.getConversationEntity(convId)).thenReturn(conv);
        when(messageRepository.findByPublicId(msgId)).thenReturn(Optional.of(msg));

        Report saved = new Report();
        saved.setId(100L);
        saved.setPublicId(UUID.randomUUID().toString());
        saved.setConversation(conv);
        saved.setReporterUserId(reporterId);
        saved.setReportedUserId(reportedId);
        saved.setReason(ReportReason.HARASSMENT);
        saved.setStatus(ReportStatus.PENDING);

        when(reportRepository.save(any(Report.class))).thenReturn(saved);

        CreateReportRequest req = new CreateReportRequest(msgId, ReportReason.HARASSMENT, "Harassing message text");
        ReportResponse response = reportService.createReport(convId, reporterId, req);

        assertNotNull(response);
        assertEquals(ReportReason.HARASSMENT, response.getReason());
        assertEquals(ReportStatus.PENDING, response.getStatus());
        assertEquals(reporterId, response.getReporterUserId());
        assertEquals(reportedId, response.getReportedUserId());

        verify(reportRepository, times(1)).save(any(Report.class));
    }
}
