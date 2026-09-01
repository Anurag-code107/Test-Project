package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.RedemptionWebhookEvent;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.entity.enums.WebhookStatus;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import com.tenxengage.app.repository.RedemptionWebhookEventRepository;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.RedemptionWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCAFFOLD tests for US-07 — covers HMAC validation, idempotency, routing, and public access.
 *
 * When US-07 BE-1 is unblocked (vendor credentials confirmed), update:
 *   1. @TestPropertySource — replace test secrets with real secrets (or use environment injection)
 *   2. setUp() validPayload — replace placeholder JSON field names with actual XTRM/Xoxoday field names
 *   3. POST_invalidHmac_returns401 — update header name from "X-Webhook-Signature" to the actual vendor header
 *   4. isCompletionEvent() values ("TRANSFER_COMPLETED", "ORDER_FULFILLED") in RedemptionWebhookController
 *      to match real vendor event type strings
 */
@WebMvcTest(RedemptionWebhookController.class)
@Import(SecurityConfig.class)
// PLACEHOLDER signing secrets — replace with real values or env injection when US-07 BE-1 is unblocked
@TestPropertySource(properties = {
        "redemption.webhook.xtrm.signing-secret=test-xtrm-secret",
        "redemption.webhook.xoxoday.signing-secret=test-xoxoday-secret"
})
class RedemptionWebhookControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private RedemptionWebhookEventRepository webhookEventRepository;
    @MockBean private RedemptionRequestRepository redemptionRequestRepository;
    @MockBean private RedemptionWebhookService webhookService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final String XTRM_SECRET = "test-xtrm-secret";
    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();

    private String validPayload;

    @BeforeEach
    void setUp() {
        // PLACEHOLDER JSON field names — replace "idempotency_key", "redemption_reference_id",
        // and "event_type" with the actual XTRM field names when confirmed (US-07 BE-1).
        // "TRANSFER_COMPLETED" is also a placeholder — confirm the real success event type string.
        validPayload = String.format(
                "{\"idempotency_key\":\"%s\",\"redemption_reference_id\":\"%s\",\"event_type\":\"TRANSFER_COMPLETED\"}",
                UUID.randomUUID(), REQUEST_ID);
    }

    /** Computes the HMAC-SHA256 hex signature the same way the controller validates it. */
    private String hmac(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private RedemptionRequest stubRequest() {
        RedemptionRequest r = RedemptionRequest.builder()
                .clientId(CLIENT_ID)
                .userId(UUID.randomUUID())
                .walletId(UUID.randomUUID())
                .catalogItemId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .currencyId("cash")
                .walletType(WalletType.INDIVIDUAL)
                .status(RedemptionStatus.PROCESSING)
                .processingMode(RedemptionProcessingMode.INSTANT)
                .category(RedemptionCategory.CASH)
                .submittedAt(Instant.now())
                .deleted(false)
                .build();
        r.setId(REQUEST_ID);
        return r;
    }

    @Test
    void POST_invalidHmac_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/redemption/xtrm")
                        // PLACEHOLDER header name — update to actual XTRM header name when confirmed (US-07 BE-1)
                        .header("X-Webhook-Signature", "invalid-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload))
                .andExpect(status().isForbidden());

        verify(webhookService, never()).process(any(), any(), any(Boolean.class), any());
    }

    @Test
    void POST_duplicateIdempotencyKey_returns200_noSideEffects() throws Exception {
        String sig = hmac(validPayload, XTRM_SECRET);
        RedemptionWebhookEvent existing = RedemptionWebhookEvent.builder()
                .idempotencyKey("existing-key")
                .status(WebhookStatus.PROCESSED)
                .vendor("xtrm")
                .clientId(CLIENT_ID)
                .redemptionRequestId(REQUEST_ID)
                .payload(validPayload)
                .receivedAt(Instant.now())
                .build();
        when(webhookEventRepository.findByIdempotencyKey(any())).thenReturn(Optional.of(existing));

        mockMvc.perform(post("/api/v1/webhooks/redemption/xtrm")
                        .header("X-Webhook-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload))
                .andExpect(status().isOk());

        verify(webhookService, never()).process(any(), any(), any(Boolean.class), any());
    }

    @Test
    void POST_validCompletion_returns200() throws Exception {
        String sig = hmac(validPayload, XTRM_SECRET);
        when(webhookEventRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(redemptionRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(stubRequest()));
        when(webhookEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/webhooks/redemption/xtrm")
                        .header("X-Webhook-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload))
                .andExpect(status().isOk());

        verify(webhookService).process(any(), any(), any(Boolean.class), any());
    }

    @Test
    void POST_xtrmNativeFields_completion_mapsAndReconciles() throws Exception {
        // XTRM echoes IssuerTransactionId (= the redemption id we sent on TransferFund) and
        // PaymentTransactionId (the payout txn id, used as the idempotency key) — no generic field names.
        String payload = String.format(
                "{\"IssuerTransactionId\":\"%s\",\"PaymentTransactionId\":\"XTRM-TX-1\",\"event_type\":\"TRANSFER_COMPLETED\"}",
                REQUEST_ID);
        String sig = hmac(payload, XTRM_SECRET);
        when(webhookEventRepository.findByIdempotencyKey("XTRM-TX-1")).thenReturn(Optional.empty());
        when(redemptionRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(stubRequest()));
        when(webhookEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/webhooks/redemption/xtrm")
                        .header("X-Webhook-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        verify(webhookService).process(any(), any(), any(Boolean.class), any());
    }

    @Test
    void POST_validFailure_returns200() throws Exception {
        // PLACEHOLDER: "TRANSFER_FAILED" and "failure_reason" are guessed field names.
        // Update to actual XTRM failure event type and field name when confirmed (US-07 BE-1).
        String failPayload = String.format(
                "{\"idempotency_key\":\"%s\",\"redemption_reference_id\":\"%s\"," +
                "\"event_type\":\"TRANSFER_FAILED\",\"failure_reason\":\"Insufficient vendor funds\"}",
                UUID.randomUUID(), REQUEST_ID);
        String sig = hmac(failPayload, XTRM_SECRET);
        when(webhookEventRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(redemptionRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(stubRequest()));
        when(webhookEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/webhooks/redemption/xtrm")
                        .header("X-Webhook-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(failPayload))
                .andExpect(status().isOk());

        verify(webhookService).process(any(), any(), any(Boolean.class), any());
    }

    @Test
    void POST_unknownEventType_deadLetters_withoutCallingWebhookService() throws Exception {
        String unknownPayload = String.format(
                "{\"idempotency_key\":\"%s\",\"redemption_reference_id\":\"%s\",\"event_type\":\"SOME_UNKNOWN_TYPE\"}",
                UUID.randomUUID(), REQUEST_ID);
        String sig = hmac(unknownPayload, XTRM_SECRET);
        when(webhookEventRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(redemptionRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(stubRequest()));
        when(webhookEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/webhooks/redemption/xtrm")
                        .header("X-Webhook-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unknownPayload))
                .andExpect(status().isOk());

        verify(webhookService, never()).process(any(), any(), any(Boolean.class), any());
    }

    @Test
    void POST_noAuthorizationHeaderRequired_returns200() throws Exception {
        String sig = hmac(validPayload, XTRM_SECRET);
        when(webhookEventRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(redemptionRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(stubRequest()));
        when(webhookEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // No Authorization header — endpoint is public (SecurityConfig PUBLIC_PATHS)
        mockMvc.perform(post("/api/v1/webhooks/redemption/xtrm")
                        .header("X-Webhook-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload))
                .andExpect(status().isOk());
    }
}
