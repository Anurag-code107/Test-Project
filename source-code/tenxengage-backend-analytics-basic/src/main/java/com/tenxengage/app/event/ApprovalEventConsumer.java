package com.tenxengage.app.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.service.ApprovalTokenService;
import com.tenxengage.app.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ApprovalEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ApprovalEventConsumer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ApprovalTokenService tokenService;
    private final EmailService emailService;

    public ApprovalEventConsumer(ApprovalTokenService tokenService, EmailService emailService) {
        this.tokenService = tokenService;
        this.emailService = emailService;
    }

    @KafkaListener(topics = "approval-events", groupId = "tenxengage-approval")
    public void handleApprovalRequest(String message) {
        ApprovalRequestEvent event;
        try {
            event = MAPPER.readValue(message, ApprovalRequestEvent.class);
        } catch (Exception e) {
            log.error("Failed to deserialize approval event: {}", e.getMessage());
            return;
        }

        log.info("Processing approval event for incentive {} with {} approvers",
            event.incentiveId(), event.approvers().size());

        for (ApprovalRequestEvent.ApproverInfo approver : event.approvers()) {
            try {
                ApprovalTokenService.ApprovalTokenResult tokenResult =
                    tokenService.generateApprovalToken(event.incentiveId(), approver.email(), event.approvalRound());

                String approveUrl = tokenService.buildApprovalUrl(tokenResult.token(), "APPROVED");
                String rejectUrl = tokenService.buildApprovalUrl(tokenResult.token(), "REJECTED");
                String reviewUrl = tokenService.buildReviewUrl(tokenResult.token());

                emailService.sendApprovalEmail(approver.email(), event.incentiveName(), approveUrl, rejectUrl, reviewUrl);
            } catch (Exception e) {
                log.error("Failed to process approval for approver {}: {}", approver.email(), e.getMessage());
            }
        }
    }
}
