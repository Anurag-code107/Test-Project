package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.redemption.ReturnService;
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
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest for ReturnWebhookController.
 *
 * Covers:
 * - Valid HMAC + confirmed=true → 200
 * - Valid HMAC + confirmed=false → 200
 * - Invalid HMAC → 403
 * - Unknown vendor path → 404
 * - Missing signature → 403
 *
 * Idempotency is tested at the service layer (ReturnServiceTest) — controller always
 * delegates and returns 200 regardless of whether the service performed a no-op.
 */
@WebMvcTest(ReturnWebhookController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "redemption.return-webhook.xoxoday.signing-secret=test-return-xoxoday-secret",
        "redemption.xoxoday.return-api-url=https://api.xoxoday.com/api/v1/plum/returns"
})
class ReturnWebhookControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ReturnService returnService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final String XOXODAY_SECRET = "test-return-xoxoday-secret";
    private static final String VENDOR_XOXODAY = "xoxoday";
    private static final String VENDOR_UNKNOWN = "acme-returns";
    private static final String WEBHOOK_URL = "/api/v1/webhooks/redemption-returns/";

    // ── Happy path — confirmed=true ────────────────────────────────────────────

    @Test
    void post_validHmac_confirmed_returns200() throws Exception {
        String payload = buildPayload(UUID.randomUUID().toString(), true, null);
        String sig = hmac(payload, XOXODAY_SECRET);
        doNothing().when(returnService)
                .processVendorConfirmation(anyString(), anyBoolean(), any());

        mockMvc.perform(post(WEBHOOK_URL + VENDOR_XOXODAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", sig)
                        .content(payload))
                .andExpect(status().isOk());

        verify(returnService).processVendorConfirmation(anyString(), anyBoolean(), any());
    }

    // ── Happy path — confirmed=false ───────────────────────────────────────────

    @Test
    void post_validHmac_rejected_returns200() throws Exception {
        String payload = buildPayload(UUID.randomUUID().toString(), false, "Item was already used");
        String sig = hmac(payload, XOXODAY_SECRET);
        doNothing().when(returnService)
                .processVendorConfirmation(anyString(), anyBoolean(), any());

        mockMvc.perform(post(WEBHOOK_URL + VENDOR_XOXODAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", sig)
                        .content(payload))
                .andExpect(status().isOk());

        verify(returnService).processVendorConfirmation(anyString(), anyBoolean(), any());
    }

    // ── Invalid HMAC → 403 ─────────────────────────────────────────────────────

    @Test
    void post_invalidHmac_returns403() throws Exception {
        String payload = buildPayload(UUID.randomUUID().toString(), true, null);

        mockMvc.perform(post(WEBHOOK_URL + VENDOR_XOXODAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", "invalid-signature")
                        .content(payload))
                .andExpect(status().isForbidden());

        verify(returnService, never()).processVendorConfirmation(anyString(), anyBoolean(), any());
    }

    @Test
    void post_missingSignatureHeader_returns403() throws Exception {
        String payload = buildPayload(UUID.randomUUID().toString(), true, null);

        mockMvc.perform(post(WEBHOOK_URL + VENDOR_XOXODAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        // No X-Webhook-Signature header
                        .content(payload))
                .andExpect(status().isForbidden());

        verify(returnService, never()).processVendorConfirmation(anyString(), anyBoolean(), any());
    }

    // ── Unknown vendor → 404 ───────────────────────────────────────────────────

    @Test
    void post_unknownVendor_returns404() throws Exception {
        String payload = buildPayload(UUID.randomUUID().toString(), true, null);
        String sig = hmac(payload, XOXODAY_SECRET);

        mockMvc.perform(post(WEBHOOK_URL + VENDOR_UNKNOWN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", sig)
                        .content(payload))
                .andExpect(status().isNotFound());

        // No HMAC check and no service call for unknown vendors
        verify(returnService, never()).processVendorConfirmation(anyString(), anyBoolean(), any());
    }

    // ── Idempotent webhook → 200 (service handles no-op) ──────────────────────

    @Test
    void post_duplicateWebhook_returns200_serviceCalledButNoStateChange() throws Exception {
        // Service handles idempotency internally (returns without state change)
        String payload = buildPayload("xoxo-ref-already-confirmed", true, null);
        String sig = hmac(payload, XOXODAY_SECRET);
        doNothing().when(returnService)
                .processVendorConfirmation(anyString(), anyBoolean(), any());

        mockMvc.perform(post(WEBHOOK_URL + VENDOR_XOXODAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", sig)
                        .content(payload))
                .andExpect(status().isOk());

        // Service is still called — idempotency guard is inside the service, not controller
        verify(returnService).processVendorConfirmation(anyString(), anyBoolean(), any());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String buildPayload(String vendorReturnReference, boolean confirmed, String failureReason) {
        if (failureReason != null) {
            return String.format(
                    "{\"vendorReturnReference\":\"%s\",\"confirmed\":%s,\"failureReason\":\"%s\"}",
                    vendorReturnReference, confirmed, failureReason);
        }
        return String.format(
                "{\"vendorReturnReference\":\"%s\",\"confirmed\":%s}",
                vendorReturnReference, confirmed);
    }

    private String hmac(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] computed = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(computed);
    }
}
