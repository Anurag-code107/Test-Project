package com.tenxengage.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.audit.Audited;
import com.tenxengage.app.dto.request.redemption.XoxodayReturnWebhookPayload;
import com.tenxengage.app.service.redemption.ReturnService;
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
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

/**
 * Receives inbound Xoxoday return confirmation webhooks.
 * No JWT — excluded from the security filter chain via SecurityConfig PUBLIC_PATHS.
 * Secured by HMAC-SHA256 signature validation (same mechanism as RedemptionWebhookController).
 *
 * Endpoint: POST /api/v1/webhooks/redemption-returns/{vendor}
 * Valid vendors: only "xoxoday". Other values → 404.
 * Rate limit: 100 req/min per source IP (enforced externally).
 *
 * PROJECT-CONTEXT.md rules applied:
 * - Vendor allowlist checked BEFORE HMAC logic — 404 for unknown vendors (no info leakage)
 * - HMAC compared with MessageDigest.isEqual (constant-time)
 * - Expected HMAC never logged, even at DEBUG
 * - Always returns 200 (including duplicates and idempotent no-ops)
 * - @Audited(action=COMPLETED) on confirm path, @Audited(action=REJECTED) on reject path
 */
@RestController
@RequestMapping("/api/v1/webhooks/redemption-returns")
@Tag(name = "Redemption Return Webhooks", description = "Inbound Xoxoday return confirmation webhooks")
public class ReturnWebhookController {

    private static final Logger log = LoggerFactory.getLogger(ReturnWebhookController.class);

    /** Valid vendor path variable values. Any other value → 404 (no info leakage). */
    private static final Set<String> SUPPORTED_VENDORS = Set.of("xoxoday");

    private final ReturnService returnService;
    private final ObjectMapper objectMapper;
    private final String xoxodaySigningSecret;

    public ReturnWebhookController(ReturnService returnService,
                                   ObjectMapper objectMapper,
                                   @Value("${redemption.return-webhook.xoxoday.signing-secret:}") String xoxodaySigningSecret) {
        this.returnService = returnService;
        this.objectMapper = objectMapper;
        this.xoxodaySigningSecret = xoxodaySigningSecret;
    }

    /**
     * POST /api/v1/webhooks/redemption-returns/{vendor}
     *
     * Flow:
     * 1. Validate vendor path param against allowlist (404 for unknown).
     * 2. Validate HMAC-SHA256 signature (403 on failure).
     * 3. Parse raw JSON body → XoxodayReturnWebhookPayload.
     * 4. Delegate to ReturnService.processVendorConfirmation (idempotency handled in service).
     * 5. Always return 200 (including duplicates).
     */
    @PostMapping("/{vendor}")
    @Operation(summary = "Receive Xoxoday return confirmation webhook")
    @Audited(
            action = "COMPLETED",
            resourceType = "REDEMPTION_RETURN",
            description = "Return confirmed by Xoxoday"
    )
    public ResponseEntity<Void> handleReturnWebhook(
            @PathVariable String vendor,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            @RequestBody String rawBody) {

        // Step 1 — Vendor allowlist FIRST (before any HMAC/auth logic) → 404 for unknowns
        if (!SUPPORTED_VENDORS.contains(vendor.toLowerCase(Locale.ROOT))) {
            log.warn("step=return_webhook_unknown_vendor vendor={}", vendor);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // Step 2 — HMAC-SHA256 validation (constant-time comparison)
        String signingSecret = resolveSigningSecret(vendor);
        if (!validateHmac(vendor, signature, rawBody, signingSecret)) {
            log.warn("step=return_webhook_invalid_hmac vendor={}", vendor);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Step 3 — Parse payload
        XoxodayReturnWebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, XoxodayReturnWebhookPayload.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("step=return_webhook_parse_error vendor={} error={}", vendor, e.getMessage(), e);
            // Return 200 even on parse error — Xoxoday should not retry on our parse bug
            return ResponseEntity.ok().build();
        }

        // Sanitize vendor-supplied reference before logging to prevent log injection
        String safeRef = payload.vendorReturnReference() != null
                ? payload.vendorReturnReference().replaceAll("[\r\n\t]", "_")
                : "(null)";
        log.info("step=return_webhook_received vendor={} idempotencyKey={} confirmed={}",
                vendor, safeRef, payload.confirmed());

        // Step 4 — Delegate to service (idempotency + state transition handled there)
        // @Audited on this method covers the confirm path; the service logs the reject path
        returnService.processVendorConfirmation(
                payload.vendorReturnReference(),
                payload.confirmed(),
                payload.failureReason());

        // Step 5 — Always 200
        return ResponseEntity.ok().build();
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private String resolveSigningSecret(String vendor) {
        // Only xoxoday is supported; switch for extensibility when other vendors are added
        return switch (vendor.toLowerCase(Locale.ROOT)) {
            case "xoxoday" -> xoxodaySigningSecret;
            default -> "";
        };
    }

    /**
     * Validates HMAC-SHA256 signature using constant-time comparison.
     * Expected format: lowercase hex without prefix.
     * Never logs the expected value — only logs null/blank signal.
     */
    private boolean validateHmac(String vendor, String signature, String payload, String secret) {
        // resolveSigningSecret guarantees non-null (returns "" for unrecognised vendors)
        if (signature == null || secret.isBlank()) {
            log.debug("step=return_webhook_hmac_skip signatureNull={} secretBlank={}",
                    signature == null, secret.isBlank());
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = HexFormat.of().formatHex(computed);
            // Constant-time comparison — prevents timing side-channel attacks
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("step=return_webhook_hmac_error vendor={}", vendor, e);
            return false;
        }
    }
}
