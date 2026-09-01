package com.tenxengage.app.service.redemption;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tenxengage.app.entity.RedemptionReturn;
import com.tenxengage.app.entity.enums.ReturnStatus;
import com.tenxengage.app.repository.RedemptionReturnRepository;
import com.tenxengage.app.testdata.RedemptionReturnFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ReturnVendorService.
 * HTTP client (RestTemplate) is mocked — no real network calls.
 * Retry delays are suppressed via sleepQuietly override on a Spy.
 *
 * Test coverage:
 * - Successful API call → vendorReturnReference stored on entity
 * - Transient failure → retries up to MAX_ATTEMPTS (5) times
 * - All-5 failure → DLQ routed (Kafka published to .DLT topic); return stays APPROVED
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReturnVendorServiceTest {

    @Mock private RedemptionReturnRepository returnRepository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private RestTemplate restTemplate;

    private ReturnVendorService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID REDEMPTION_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID RETURN_ID = UUID.randomUUID();
    private static final String VENDOR_REF = "xoxo-ref-abc123";

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        service = new ReturnVendorService(returnRepository, kafkaTemplate, objectMapper);
        // Inject mocked RestTemplate and config values via reflection
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(service, "xoxodayReturnApiUrl",
                "https://api.xoxoday.com/api/v1/plum/returns");
        ReflectionTestUtils.setField(service, "xoxodaySigningSecret", "test-secret");
    }

    // ── Happy path ─────────────────────────────────────────────────────────────

    @Test
    void notifyXoxodayReturn_success_storesVendorReturnReference() {
        RedemptionReturn ret = buildApprovedReturn();

        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(Map.of("vendorReturnReference", VENDOR_REF));
        when(returnRepository.findById(RETURN_ID)).thenReturn(Optional.of(ret));
        when(returnRepository.save(any(RedemptionReturn.class))).thenReturn(ret);

        // @Async not active in unit test — method runs synchronously
        service.notifyXoxodayReturn(ret);

        // Verify vendorReturnReference was persisted
        ArgumentCaptor<RedemptionReturn> captor = ArgumentCaptor.forClass(RedemptionReturn.class);
        verify(returnRepository).save(captor.capture());
        assertThat(captor.getValue().getVendorReturnReference()).isEqualTo(VENDOR_REF);

        // Exactly 1 HTTP call (no retries needed)
        verify(restTemplate, times(1)).postForObject(anyString(), any(), eq(Map.class));
    }

    // ── Retry behaviour ────────────────────────────────────────────────────────

    @Test
    void notifyXoxodayReturn_transientFailure_retriesUpTo5Times() {
        ReturnVendorService spyService = buildSpyServiceWithZeroDelays();

        RedemptionReturn ret = buildApprovedReturn();
        // Fail 4 times, succeed on attempt 5
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "503"))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "503"))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "503"))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "503"))
                .thenReturn(Map.of("vendorReturnReference", VENDOR_REF));
        when(returnRepository.findById(RETURN_ID)).thenReturn(Optional.of(ret));
        when(returnRepository.save(any(RedemptionReturn.class))).thenReturn(ret);

        spyService.notifyXoxodayReturn(ret);

        // All 5 HTTP attempts made (4 failures + 1 success)
        verify(restTemplate, times(5)).postForObject(anyString(), any(), eq(Map.class));
        // Success on 5th — vendor ref stored
        verify(returnRepository).save(any(RedemptionReturn.class));
    }

    // ── DLQ routing on all-5 failure ───────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void notifyXoxodayReturn_allFiveAttemptsFail_routesToDlqAndReturnStaysApproved() {
        ReturnVendorService spyService = buildSpyServiceWithZeroDelays();

        RedemptionReturn ret = buildApprovedReturn();
        // All 5 attempts fail
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "503"));

        CompletableFuture<org.springframework.kafka.support.SendResult<String, String>> future =
                CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(contains(".DLT"), anyString(), anyString())).thenReturn(future);

        spyService.notifyXoxodayReturn(ret);

        // 5 HTTP attempts made
        verify(restTemplate, times(5)).postForObject(anyString(), any(), eq(Map.class));

        // DLQ publish attempted
        verify(kafkaTemplate, atLeast(1)).send(contains(".DLT"), anyString(), anyString());

        // Return entity NOT saved — remains in APPROVED state
        verify(returnRepository, never()).save(any());
        assertThat(ret.getStatus()).isEqualTo(ReturnStatus.APPROVED);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private RedemptionReturn buildApprovedReturn() {
        RedemptionReturn ret = RedemptionReturnFixtures
                .anApprovedReturn(CLIENT_ID, REDEMPTION_ID, USER_ID)
                .amount(new BigDecimal("100.0000"))
                .currencyId("points")
                .build();
        ret.setId(RETURN_ID);
        return ret;
    }

    /**
     * Returns a Spy on the service with sleepQuietly no-op'd to suppress retry delays.
     * The spy delegates to the real implementation for all other methods.
     */
    private ReturnVendorService buildSpyServiceWithZeroDelays() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        ReturnVendorService realService =
                new ReturnVendorService(returnRepository, kafkaTemplate, objectMapper);
        ReflectionTestUtils.setField(realService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(realService, "xoxodayReturnApiUrl",
                "https://api.xoxoday.com/api/v1/plum/returns");
        ReflectionTestUtils.setField(realService, "xoxodaySigningSecret", "test-secret");

        // Spy to intercept sleepQuietly and make it a no-op
        ReturnVendorService spy = org.mockito.Mockito.spy(realService);
        doNothing().when(spy).sleepQuietly(any(Long.TYPE));
        return spy;
    }
}
