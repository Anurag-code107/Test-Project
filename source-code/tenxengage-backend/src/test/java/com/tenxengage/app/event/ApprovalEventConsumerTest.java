package com.tenxengage.app.event;

import com.tenxengage.app.service.ApprovalTokenService;
import com.tenxengage.app.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalEventConsumerTest {

    @Mock
    private ApprovalTokenService tokenService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private ApprovalEventConsumer consumer;

    @Test
    void handleApprovalRequest_validJson_callsEmailService() throws Exception {
        UUID incentiveId = UUID.randomUUID();
        String approverEmail = "approver@example.com";
        String json = String.format(
                "{\"incentiveId\":\"%s\",\"incentiveName\":\"Test Incentive\",\"approvalRound\":1," +
                "\"approvers\":[{\"email\":\"%s\",\"category\":\"MANAGER\"}]}",
                incentiveId, approverEmail);

        ApprovalTokenService.ApprovalTokenResult tokenResult =
                new ApprovalTokenService.ApprovalTokenResult("mock-token", UUID.randomUUID());
        when(tokenService.generateApprovalToken(incentiveId, approverEmail, 1)).thenReturn(tokenResult);
        when(tokenService.buildApprovalUrl("mock-token", "APPROVED")).thenReturn("http://example.com/approve");
        when(tokenService.buildApprovalUrl("mock-token", "REJECTED")).thenReturn("http://example.com/reject");
        when(tokenService.buildReviewUrl("mock-token")).thenReturn("http://example.com/review");

        consumer.handleApprovalRequest(json);

        verify(emailService).sendApprovalEmail(
                anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void handleApprovalRequest_invalidJson_doesNotCallEmailService() {
        consumer.handleApprovalRequest("invalid-json");

        verify(emailService, never()).sendApprovalEmail(
                anyString(), anyString(), anyString(), anyString(), anyString());
    }
}
