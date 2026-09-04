package com.datingapp.chat.report.service.impl;

import com.datingapp.chat.common.exception.BadRequestException;
import com.datingapp.chat.common.exception.ErrorCode;
import com.datingapp.chat.common.exception.ResourceNotFoundException;
import com.datingapp.chat.conversation.entity.Conversation;
import com.datingapp.chat.conversation.entity.ConversationParticipant;
import com.datingapp.chat.conversation.service.ConversationService;
import com.datingapp.chat.message.entity.Message;
import com.datingapp.chat.message.repository.MessageRepository;
import com.datingapp.chat.report.dto.CreateReportRequest;
import com.datingapp.chat.report.dto.ReportResponse;
import com.datingapp.chat.report.entity.Report;
import com.datingapp.chat.report.entity.ReportStatus;
import com.datingapp.chat.report.repository.ReportRepository;
import com.datingapp.chat.report.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@Transactional
public class ReportServiceImpl implements ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportServiceImpl.class);

    private final ReportRepository reportRepository;
    private final ConversationService conversationService;
    private final MessageRepository messageRepository;

    public ReportServiceImpl(
            ReportRepository reportRepository,
            ConversationService conversationService,
            MessageRepository messageRepository) {
        this.reportRepository = reportRepository;
        this.conversationService = conversationService;
        this.messageRepository = messageRepository;
    }

    @Override
    public ReportResponse createReport(String conversationPublicId, Long reporterUserId, CreateReportRequest request) {
        // 1. Authorize reporter membership
        conversationService.validateUserIsParticipant(conversationPublicId, reporterUserId);

        // 2. Fetch conversation container
        Conversation conversation = conversationService.getConversationEntity(conversationPublicId);

        // 3. Resolve reported user ID and optional message
        Message message = null;
        Long reportedUserId;

        if (request.getMessageId() != null && !request.getMessageId().isBlank()) {
            message = messageRepository.findByPublicId(request.getMessageId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Message not found: " + request.getMessageId(), ErrorCode.MESSAGE_NOT_FOUND));

            if (!Objects.equals(message.getConversation().getId(), conversation.getId())) {
                throw new BadRequestException(
                        "Reported message does not belong to this conversation", ErrorCode.BAD_REQUEST);
            }
            reportedUserId = message.getSenderId();
        } else {
            // Default reported user to the other conversation participant
            reportedUserId = conversation.getParticipants().stream()
                    .map(ConversationParticipant::getUserId)
                    .filter(userId -> !Objects.equals(userId, reporterUserId))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException(
                            "Cannot determine reported user in conversation", ErrorCode.BAD_REQUEST));
        }

        // 4. Create and persist report
        Report report = new Report();
        report.setConversation(conversation);
        report.setReporterUserId(reporterUserId);
        report.setReportedUserId(reportedUserId);
        report.setMessage(message);
        report.setReason(request.getReason());
        report.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        report.setStatus(ReportStatus.PENDING);

        Report savedReport = reportRepository.save(report);
        log.info("Report [{}] registered by user {} against user {} (Reason: {})",
                savedReport.getPublicId(), reporterUserId, reportedUserId, request.getReason());

        return new ReportResponse(
                savedReport.getPublicId(),
                conversation.getPublicId(),
                savedReport.getReporterUserId(),
                savedReport.getReportedUserId(),
                savedReport.getReason(),
                savedReport.getStatus(),
                savedReport.getCreatedAt()
        );
    }
}
