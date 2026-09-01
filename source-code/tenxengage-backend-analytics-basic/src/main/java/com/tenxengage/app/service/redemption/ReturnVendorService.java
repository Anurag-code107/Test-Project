package com.tenxengage.app.service.redemption;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.config.KafkaConfig;
import com.tenxengage.app.entity.RedemptionReturn;
import com.tenxengage.app.event.ReturnEvent;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Asynchronous vendor notification service for approved returns.
 * Calls Xoxoday's return API with exponential backoff (5 attempts: 1s/2s/4s/8s/32s).
 * On success: updates the return record with the vendorReturnReference.
 * On all-5 failure: routes to DLQ and raises an ops alert.
 *
 * PROJECT-CONTEXT.md rules applied:
 * - @Async — invoked only via the injected bean (never self-call) to preserve AOP proxy
 * - No @Transactional here — external HTTP call must not hold a DB connection (anti-pattern)
 * - KafkaTemplate.send() failure is logged (.whenComplete error handler)
 * - signing-secret validated non-blank at startup via @PostConstruct
 */
@Service
public class ReturnVendorService {

    private static final Logger log = LoggerFactory.getLogger(ReturnVendorService.class);

    /**
     * Retry delay schedule (milliseconds): 1s / 2s / 4s / 8s / 32s (cap).
     */
    private static final long[] RETRY_DELAYS_MS = {1_000L, 2_000L, 4_000L, 8_000L, 32_000L};
    private static final int MAX_ATTEMPTS = 5;

    @Value("${redemption.xoxoday.return-api-url}")
    private String xoxodayReturnApiUrl;

    @Value("${redemption.return-webhook.xoxoday.signing-secret:}")
    private String xoxodaySigningSecret;

    private final com.tenxengage.app.repository.RedemptionReturnRepository returnRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public ReturnVendorService(
            com.tenxengage.app.repository.RedemptionReturnRepository returnRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.returnRepository = returnRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Validates that the Xoxoday signing secret is set.
     * Skipped in test/local profiles where the secret is deliberately a placeholder.
     * The signing secret is used inbound (webhook validation) not outbound, so a blank
     * outbound secret does not break the API call — but it should still be configured.
     */
    @PostConstruct
    @Profile("!test & !localtest")
    void validateConfig() {
        if (xoxodaySigningSecret == null || xoxodaySigningSecret.isBlank()) {
            log.warn("redemption.return-webhook.xoxoday.signing-secret is blank — " +
                    "webhook HMAC validation will reject all inbound Xoxoday return callbacks");
        }
    }

    /**
     * Asynchronously notifies Xoxoday of an approved return.
     * Called from ReturnService.approveReturn() via afterCommit synchronization so the
     * DB transaction has already committed before the HTTP call goes out.
     *
     * On success: persists vendorReturnReference on the return record.
     * On all-5 failure: publishes to DLQ topic and logs at ERROR (ops alert).
     *
     * @param ret the approved RedemptionReturn (detached copy passed after commit)
     */
    @Async("taskExecutor")
    public void notifyXoxodayReturn(RedemptionReturn ret) {
        String returnId = ret.getId().toString();
        String vendorReturnReference = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            log.info("step=return_vendor_notify_start returnId={} attempt={}", returnId, attempt);
            try {
                vendorReturnReference = callXoxodayReturnApi(ret);
                // Success — persist vendorReturnReference and stop retrying
                persistVendorReference(ret.getId(), vendorReturnReference);
                log.info("step=return_vendor_notify_success returnId={} vendorReturnReference={}",
                        returnId, vendorReturnReference);
                return;
            } catch (Exception e) {
                if (attempt < MAX_ATTEMPTS) {
                    long delayMs = RETRY_DELAYS_MS[attempt - 1];
                    log.warn("step=return_vendor_notify_retry returnId={} attempt={} error={}",
                            returnId, attempt, e.getMessage(), e);
                    sleepQuietly(delayMs);
                } else {
                    // All attempts exhausted — route to DLQ and raise ops alert
                    log.error("step=return_vendor_notify_failed_dlq returnId={} attempts={} error={}",
                            returnId, MAX_ATTEMPTS, e.getMessage(), e);
                    routeToDlq(ret, e.getMessage());
                }
            }
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Calls the Xoxoday return API (HTTP POST) with return details.
     * Returns the vendorReturnReference string on success.
     * Throws RestClientException on HTTP error or network failure.
     */
    private String callXoxodayReturnApi(RedemptionReturn ret) {
        Map<String, Object> body = Map.of(
                "returnReferenceId", ret.getId().toString(),
                "redemptionId", ret.getRedemptionId().toString(),
                "amount", ret.getAmount(),
                "currencyId", ret.getCurrencyId()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            String jsonBody = objectMapper.writeValueAsString(body);
            HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    xoxodayReturnApiUrl, request, Map.class);

            if (response == null) {
                throw new RestClientException("Empty response from Xoxoday return API");
            }

            Object ref = response.get("vendorReturnReference");
            if (ref == null) {
                throw new RestClientException("Missing vendorReturnReference in Xoxoday response");
            }
            return ref.toString();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RestClientException("Failed to serialize Xoxoday return request body", e);
        }
    }

    /**
     * Persists the vendorReturnReference on the return entity.
     * Uses a fresh findById to avoid stale-entity overwrite — the entity was passed
     * as a detached copy from an already-committed transaction.
     */
    private void persistVendorReference(UUID returnId, String vendorReturnReference) {
        returnRepository.findById(returnId).ifPresent(ret -> {
            ret.setVendorReturnReference(vendorReturnReference);
            returnRepository.save(ret);
        });
    }

    /**
     * Routes the failed return notification to the DLQ Kafka topic.
     * Failure to publish to the DLQ is logged but does not throw —
     * the ops alert (ERROR-level log above) is the primary signal.
     */
    private void routeToDlq(RedemptionReturn ret, String failureReason) {
        try {
            ReturnEvent dlqEvent = new ReturnEvent(
                    UUID.randomUUID(),
                    "RETURN_VENDOR_NOTIFY_FAILED",
                    Instant.now(),
                    ret.getClientId(),
                    ret.getId(),
                    ret.getRedemptionId(),
                    ret.getAmount(),
                    ret.getCurrencyId(),
                    ret.getStatus(),
                    null,
                    null
            );
            String payload = objectMapper.writeValueAsString(dlqEvent);
            kafkaTemplate.send(KafkaConfig.RETURN_EVENTS_TOPIC + ".DLT",
                            ret.getClientId().toString(), payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("step=return_dlq_send_failed returnId={} error={}",
                                    ret.getId(), ex.getMessage(), ex);
                        } else {
                            log.info("step=return_dlq_routed returnId={}", ret.getId());
                        }
                    });
        } catch (Exception e) {
            log.error("step=return_dlq_routing_error returnId={}", ret.getId(), e);
        }
    }

    /**
     * Sleeps for the given number of milliseconds.
     * Package-private to allow test subclasses to override with zero-duration sleeps,
     * avoiding real wall-clock delays in unit tests.
     */
    void sleepQuietly(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Retry sleep interrupted for return vendor notification");
        }
    }
}
