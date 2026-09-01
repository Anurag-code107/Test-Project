package com.tenxengage.app.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.RedemptionWebhookEvent;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.WebhookStatus;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import com.tenxengage.app.repository.RedemptionWebhookEventRepository;
import com.tenxengage.app.service.RedemptionWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks/redemption")
@Tag(name = "Redemption Webhooks")
public class RedemptionWebhookController {

    private static final Logger log = LoggerFactory.getLogger(RedemptionWebhookController.class);
    private static final Set<String> SUPPORTED_VENDORS = Set.of("xtrm", "xoxoday");

    /*
     * PLACEHOLDER signing secrets — confirm header names and HMAC format with each vendor
     * before going live (US-07 BE-1). Set these in application.yml / environment variables.
     *
     * Expected header name is also a PLACEHOLDER ("X-Webhook-Signature"). Rename to the
     * vendor-specific header (e.g. "X-XTRM-Signature", "X-Xoxoday-Hmac-Sha256") once confirmed.
     */
    @Value("${redemption.webhook.xtrm.signing-secret:}")
    private String xtrmSigningSecret;

    @Value("${redemption.webhook.xoxoday.signing-secret:}")
    private String xoxodaySigningSecret;

    private final RedemptionWebhookEventRepository webhookEventRepository;
    private final RedemptionRequestRepository redemptionRequestRepository;
    private final RedemptionWebhookService webhookService;
    private final ObjectMapper mapper;

    public RedemptionWebhookController(RedemptionWebhookEventRepository webhookEventRepository,
                                        RedemptionRequestRepository redemptionRequestRepository,
                                        RedemptionWebhookService webhookService,
                                        ObjectMapper mapper) {
        this.webhookEventRepository = webhookEventRepository;
        this.redemptionRequestRepository = redemptionRequestRepository;
        this.webhookService = webhookService;
        this.mapper = mapper;
    }

    /**
     * Receives inbound webhook callbacks from XTRM and Xoxoday.
     * No JWT required — this endpoint is excluded from the JWT filter chain via SecurityConfig.
     *
     * PLACEHOLDER: the signature header name "X-Webhook-Signature" must be confirmed with each
     * vendor and updated here + in RedemptionWebhookControllerTest before going live (US-07 BE-1).
     */
    @PostMapping("/{vendor}")
    @Operation(summary = "Receive vendor redemption webhook")
    public ResponseEntity<Void> handleWebhook(
            @PathVariable String vendor,
            // PLACEHOLDER header name — confirm actual header from XTRM / Xoxoday docs (US-07 BE-1)
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            @RequestBody String rawPayload) {

        if (!SUPPORTED_VENDORS.contains(vendor.toLowerCase())) {
            log.warn("[step=webhook-unknown-vendor] vendor={}", vendor);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String signingSecret = resolveSigningSecret(vendor);

        if (!validateHmac(signature, rawPayload, signingSecret)) {
            log.warn("[step=webhook-invalid-hmac] vendor={}", vendor);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String idempotencyKey = extractField(rawPayload, "idempotency_key");
        if (idempotencyKey == null) {
            log.error("[step=webhook-missing-idempotency-key] vendor={}", vendor);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // Idempotency check — return 200 immediately if this key was already processed
        Optional<RedemptionWebhookEvent> existing = webhookEventRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("[step=webhook-duplicate] vendor={}, idempotencyKey={}", vendor, idempotencyKey);
            return ResponseEntity.ok().build();
        }

        /*
         * PLACEHOLDER field extraction — "redemption_reference_id" is a guessed field name.
         * Confirm the actual field XTRM / Xoxoday use to echo back our reference UUID (US-07 BE-1).
         */
        String refId = extractField(rawPayload, "redemption_reference_id");
        if (refId == null) {
            log.error("[step=webhook-invalid-reference-id] vendor={}, refId=null", vendor);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        UUID redemptionRequestId;
        try {
            redemptionRequestId = UUID.fromString(refId);
        } catch (IllegalArgumentException e) {
            log.error("[step=webhook-invalid-reference-id] vendor={}, refId={}", vendor, refId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        RedemptionRequest request = redemptionRequestRepository.findById(redemptionRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionRequest", "id", redemptionRequestId));

        // Vendor-channel guard: reject callbacks whose vendor path does not match the request's category.
        // CASH → XTRM, NON_CASH → Xoxoday. A Xoxoday-signed webhook must never finalize a CASH/XTRM redemption.
        RedemptionCategory expectedCategory = vendor.equalsIgnoreCase("xtrm")
                ? RedemptionCategory.CASH : RedemptionCategory.NON_CASH;
        if (request.getCategory() != expectedCategory) {
            log.warn("[step=webhook-vendor-mismatch] vendor={}, requestId={}, requestCategory={}",
                    vendor, redemptionRequestId, request.getCategory());
            RedemptionWebhookEvent mismatchEvent = RedemptionWebhookEvent.builder()
                    .clientId(request.getClientId())
                    .vendor(vendor)
                    .redemptionRequestId(redemptionRequestId)
                    .idempotencyKey(idempotencyKey)
                    .payload(rawPayload)
                    .status(WebhookStatus.DEAD_LETTERED)
                    .receivedAt(Instant.now())
                    .failureReason("Vendor mismatch: " + vendor + " callback for "
                            + request.getCategory() + " redemption")
                    .build();
            webhookEventRepository.save(mismatchEvent);
            return ResponseEntity.ok().build();
        }

        RedemptionWebhookEvent webhookEvent = RedemptionWebhookEvent.builder()
                .clientId(request.getClientId())
                .vendor(vendor)
                .redemptionRequestId(redemptionRequestId)
                .idempotencyKey(idempotencyKey)
                .payload(rawPayload)
                .status(WebhookStatus.RECEIVED)
                .receivedAt(Instant.now())
                .build();
        webhookEventRepository.save(webhookEvent);

        /*
         * PLACEHOLDER event type detection — "event_type" field name and the values that signal
         * success vs failure must be confirmed with vendor docs (US-07 BE-1).
         * e.g. XTRM might use "TRANSFER_COMPLETED" / "TRANSFER_FAILED";
         *      Xoxoday might use "ORDER_FULFILLED" / "ORDER_FAILED".
         */
        String eventType = extractField(rawPayload, "event_type");
        if (!isKnownEventType(eventType, vendor)) {
            webhookEvent.setStatus(WebhookStatus.DEAD_LETTERED);
            webhookEvent.setFailureReason("Unknown event_type: " + eventType);
            webhookEventRepository.save(webhookEvent);
            log.warn("[step=webhook-unknown-event-type] vendor={}, eventType={}", vendor, eventType);
            return ResponseEntity.ok().build();
        }

        boolean completed = isCompletionEvent(eventType, vendor);
        String failureReason = completed ? null : extractField(rawPayload, "failure_reason");

        webhookService.process(redemptionRequestId, webhookEvent, completed, failureReason);

        return ResponseEntity.ok().build();
    }

    private String resolveSigningSecret(String vendor) {
        return switch (vendor.toLowerCase()) {
            case "xtrm" -> xtrmSigningSecret;
            case "xoxoday" -> xoxodaySigningSecret;
            default -> "";
        };
    }

    /**
     * Validates HMAC-SHA256 signature.
     * PLACEHOLDER: the signature format (hex vs base64, with/without prefix like "sha256=")
     * must be confirmed with each vendor before going live (US-07 BE-1).
     * Current implementation expects lowercase hex with no prefix.
     */
    private boolean validateHmac(String signature, String payload, String secret) {
        if (signature == null || secret == null || secret.isBlank()) {
            log.debug("[step=webhook-hmac-debug] secret blank or signature null, secret blank={}", secret == null || secret.isBlank());
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = HexFormat.of().formatHex(computed);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("[step=webhook-hmac-error]", e);
            return false;
        }
    }

    private boolean isKnownEventType(String eventType, String vendor) {
        if (eventType == null) return false;
        return switch (vendor.toLowerCase()) {
            // PLACEHOLDER — update when XTRM event type strings are confirmed (US-07 BE-1)
            case "xtrm" -> "TRANSFER_COMPLETED".equalsIgnoreCase(eventType)
                        || "TRANSFER_FAILED".equalsIgnoreCase(eventType);
            // PLACEHOLDER — update when Xoxoday event type strings are confirmed (US-07 BE-1)
            case "xoxoday" -> "ORDER_FULFILLED".equalsIgnoreCase(eventType)
                           || "ORDER_FAILED".equalsIgnoreCase(eventType);
            default -> false;
        };
    }

    /**
     * PLACEHOLDER: determines if this is a completion event.
     * Update the success event type strings once confirmed with each vendor (US-07 BE-1).
     */
    private boolean isCompletionEvent(String eventType, String vendor) {
        if (eventType == null) return false;
        return switch (vendor.toLowerCase()) {
            // PLACEHOLDER — replace "TRANSFER_COMPLETED" with actual XTRM success event type (US-07 BE-1)
            case "xtrm" -> "TRANSFER_COMPLETED".equalsIgnoreCase(eventType);
            // PLACEHOLDER — replace "ORDER_FULFILLED" with actual Xoxoday success event type (US-07 BE-1)
            case "xoxoday" -> "ORDER_FULFILLED".equalsIgnoreCase(eventType);
            default -> false;
        };
    }

    /** Generic JSON field extractor — avoids hard coupling to vendor DTO during scaffold phase. */
    @SuppressWarnings("unchecked")
    private String extractField(String json, String field) {
        try {
            Map<String, Object> map = mapper.readValue(json, Map.class);
            Object value = map.get(field);
            return value != null ? value.toString() : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
