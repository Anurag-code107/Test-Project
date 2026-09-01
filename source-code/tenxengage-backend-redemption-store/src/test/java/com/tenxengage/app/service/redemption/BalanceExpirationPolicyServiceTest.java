package com.tenxengage.app.service.redemption;

import com.tenxengage.app.dto.request.UpsertBalanceExpirationPolicyRequest;
import com.tenxengage.app.dto.response.BalanceExpirationPolicyResponse;
import com.tenxengage.app.dto.response.ExpiringBalancePreviewResponse;
import com.tenxengage.app.entity.BalanceExpirationPolicy;
import com.tenxengage.app.entity.enums.ExpirationMode;
import com.tenxengage.app.entity.enums.ExpiryNoticeStatus;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.BalanceExpirationPolicyRepository;
import com.tenxengage.app.repository.BalanceExpiryNoticeRepository;
import com.tenxengage.app.repository.projection.ExpiringBalancePreviewProjection;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.FeatureFlagService;
import com.tenxengage.app.testdata.BalanceExpirationPolicyFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BalanceExpirationPolicyServiceTest {

    @Mock private BalanceExpirationPolicyRepository policyRepository;
    @Mock private BalanceExpiryNoticeRepository noticeRepository;
    @Mock private TenantValidator tenantValidator;
    @Mock private FeatureFlagService featureFlagService;

    @InjectMocks
    private BalanceExpirationPolicyService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
        when(featureFlagService.getEnabledFeatures(CLIENT_ID))
                .thenReturn(List.of("reward_balance_expiration"));
    }

    // ── getPolicies ───────────────────────────────────────────────────────────

    @Test
    void getPolicies_returnsMappedResponses() {
        BalanceExpirationPolicy policy = BalanceExpirationPolicyFixtures.inactivityPolicy(CLIENT_ID, "points").build();
        when(policyRepository.findByClientId(CLIENT_ID)).thenReturn(List.of(policy));

        List<BalanceExpirationPolicyResponse> result = service.getPolicies();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).currencyId()).isEqualTo("points");
        assertThat(result.get(0).enabled()).isTrue();
        assertThat(result.get(0).expirationMode()).isEqualTo(ExpirationMode.INACTIVITY);
    }

    @Test
    void getPolicies_returnsEmptyList_whenNoPolicies() {
        when(policyRepository.findByClientId(CLIENT_ID)).thenReturn(List.of());

        List<BalanceExpirationPolicyResponse> result = service.getPolicies();

        assertThat(result).isEmpty();
    }

    // ── upsertPolicy — happy paths ────────────────────────────────────────────

    @Test
    void upsertPolicy_createsNewPolicy_withInactivityMode() {
        UpsertBalanceExpirationPolicyRequest req = buildInactivityRequest(true, 365, 30);
        when(policyRepository.findByClientIdAndCurrencyId(CLIENT_ID, "points")).thenReturn(Optional.empty());
        when(policyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BalanceExpirationPolicyResponse response = service.upsertPolicy("points", req);

        assertThat(response.currencyId()).isEqualTo("points");
        assertThat(response.enabled()).isTrue();
        assertThat(response.expirationMode()).isEqualTo(ExpirationMode.INACTIVITY);
        assertThat(response.inactivityDays()).isEqualTo(365);
        assertThat(response.leadTimeDays()).isEqualTo(30);
    }

    @Test
    void upsertPolicy_createsNewPolicy_withFixedDateMode() {
        LocalDate futureDate = LocalDate.now().plusYears(1);
        UpsertBalanceExpirationPolicyRequest req = buildFixedDateRequest(true, futureDate, 30);
        when(policyRepository.findByClientIdAndCurrencyId(CLIENT_ID, "cash")).thenReturn(Optional.empty());
        when(policyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BalanceExpirationPolicyResponse response = service.upsertPolicy("cash", req);

        assertThat(response.currencyId()).isEqualTo("cash");
        assertThat(response.expirationMode()).isEqualTo(ExpirationMode.FIXED_DATE);
        assertThat(response.fixedExpiryDate()).isEqualTo(futureDate);
    }

    @Test
    void upsertPolicy_setsEnabledAt_whenEnablingFirstTime() {
        UpsertBalanceExpirationPolicyRequest req = buildInactivityRequest(true, 365, 30);
        when(policyRepository.findByClientIdAndCurrencyId(CLIENT_ID, "points")).thenReturn(Optional.empty());
        when(policyRepository.save(any())).thenAnswer(inv -> {
            BalanceExpirationPolicy saved = inv.getArgument(0);
            return saved;
        });

        BalanceExpirationPolicyResponse response = service.upsertPolicy("points", req);

        verify(policyRepository).save(any(BalanceExpirationPolicy.class));
        // enabledAt should be set since enabling for first time
        assertThat(response.enabled()).isTrue();
        assertThat(response.enabledAt()).isNotNull();
    }

    @Test
    void upsertPolicy_setsEnabledAt_onMaterialChange() {
        BalanceExpirationPolicy existing = BalanceExpirationPolicyFixtures
                .inactivityPolicy(CLIENT_ID, "points")
                .inactivityDays(180)
                .leadTimeDays(30)
                .enabledAt(Instant.now().minusSeconds(3600))
                .build();
        // Change inactivityDays — material change
        UpsertBalanceExpirationPolicyRequest req = buildInactivityRequest(true, 365, 30);
        when(policyRepository.findByClientIdAndCurrencyId(CLIENT_ID, "points")).thenReturn(Optional.of(existing));
        when(policyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BalanceExpirationPolicyResponse response = service.upsertPolicy("points", req);

        assertThat(response.inactivityDays()).isEqualTo(365);
    }

    @Test
    void upsertPolicy_updatesExistingPolicy() {
        BalanceExpirationPolicy existing = BalanceExpirationPolicyFixtures
                .disabledPolicy(CLIENT_ID, "credits")
                .build();
        UpsertBalanceExpirationPolicyRequest req = buildInactivityRequest(true, 90, 15);
        when(policyRepository.findByClientIdAndCurrencyId(CLIENT_ID, "credits")).thenReturn(Optional.of(existing));
        when(policyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BalanceExpirationPolicyResponse response = service.upsertPolicy("credits", req);

        assertThat(response.enabled()).isTrue();
        assertThat(response.inactivityDays()).isEqualTo(90);
        assertThat(response.leadTimeDays()).isEqualTo(15);
    }

    // ── upsertPolicy — validation failures ────────────────────────────────────

    @Test
    void upsertPolicy_throws422_whenLeadTimeGteInactivity() {
        UpsertBalanceExpirationPolicyRequest req = buildInactivityRequest(true, 90, 120);
        when(policyRepository.findByClientIdAndCurrencyId(CLIENT_ID, "points")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsertPolicy("points", req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Lead time")
                .satisfies(e -> assertThat(((BusinessRuleException) e).getErrorCode())
                        .isEqualTo("ERR_LEAD_TIME_GTE_INACTIVITY"));
    }

    @Test
    void upsertPolicy_throws422_whenLeadTimeLessThanOne() {
        UpsertBalanceExpirationPolicyRequest req = buildInactivityRequest(true, 365, 0);
        when(policyRepository.findByClientIdAndCurrencyId(CLIENT_ID, "points")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsertPolicy("points", req))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getErrorCode())
                        .isEqualTo("ERR_LEAD_TIME_INVALID"));
    }

    @Test
    void upsertPolicy_throws422_whenInactivityDaysOutOfBounds_tooLow() {
        UpsertBalanceExpirationPolicyRequest req = buildInactivityRequest(true, 10, 5);
        when(policyRepository.findByClientIdAndCurrencyId(CLIENT_ID, "points")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsertPolicy("points", req))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getErrorCode())
                        .isEqualTo("ERR_INACTIVITY_DAYS_BOUNDS"));
    }

    @Test
    void upsertPolicy_throws422_whenInactivityDaysOutOfBounds_tooHigh() {
        UpsertBalanceExpirationPolicyRequest req = buildInactivityRequest(true, 2000, 30);
        when(policyRepository.findByClientIdAndCurrencyId(CLIENT_ID, "points")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsertPolicy("points", req))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getErrorCode())
                        .isEqualTo("ERR_INACTIVITY_DAYS_BOUNDS"));
    }

    @Test
    void upsertPolicy_throws422_whenFixedExpiryDateInPast() {
        LocalDate pastDate = LocalDate.now().minusDays(1);
        UpsertBalanceExpirationPolicyRequest req = buildFixedDateRequest(true, pastDate, 30);
        when(policyRepository.findByClientIdAndCurrencyId(CLIENT_ID, "cash")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsertPolicy("cash", req))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getErrorCode())
                        .isEqualTo("ERR_FIXED_EXPIRY_DATE_PAST"));
    }

    @Test
    void upsertPolicy_throws422_whenModeFieldMismatch_inactivityWithFixedDate() {
        UpsertBalanceExpirationPolicyRequest req = buildInactivityRequest(true, 365, 30);
        req.setFixedExpiryDate(LocalDate.now().plusYears(1)); // should not be set for INACTIVITY
        when(policyRepository.findByClientIdAndCurrencyId(CLIENT_ID, "points")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsertPolicy("points", req))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getErrorCode())
                        .isEqualTo("ERR_MODE_FIELD_MISMATCH"));
    }

    @Test
    void upsertPolicy_throws422_whenModeFieldMismatch_fixedDateWithInactivityDays() {
        UpsertBalanceExpirationPolicyRequest req = buildFixedDateRequest(true, LocalDate.now().plusYears(1), 30);
        req.setInactivityDays(180); // should not be set for FIXED_DATE
        when(policyRepository.findByClientIdAndCurrencyId(CLIENT_ID, "cash")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsertPolicy("cash", req))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getErrorCode())
                        .isEqualTo("ERR_MODE_FIELD_MISMATCH"));
    }

    @Test
    void upsertPolicy_throws422_whenUnrecognisedCurrencyId() {
        UpsertBalanceExpirationPolicyRequest req = buildInactivityRequest(true, 365, 30);

        assertThatThrownBy(() -> service.upsertPolicy("unknown_currency", req))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getErrorCode())
                        .isEqualTo("ERR_INVALID_CURRENCY"));
    }

    @Test
    void upsertPolicy_throws422_whenInactivityDaysMissingForInactivityMode() {
        UpsertBalanceExpirationPolicyRequest req = new UpsertBalanceExpirationPolicyRequest();
        req.setEnabled(true);
        req.setExpirationMode(ExpirationMode.INACTIVITY);
        req.setLeadTimeDays(30);
        // inactivityDays is null — should fail
        when(policyRepository.findByClientIdAndCurrencyId(CLIENT_ID, "points")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsertPolicy("points", req))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getErrorCode())
                        .isEqualTo("ERR_INACTIVITY_DAYS_REQUIRED"));
    }

    // ── getExpiringSoon ───────────────────────────────────────────────────────

    @Test
    void getExpiringSoon_returnsAggregatePreview() {
        ExpiringBalancePreviewProjection proj = mockProjection("points", LocalDate.now().plusDays(20), 5L, new BigDecimal("1500.00"));
        when(noticeRepository.aggregateExpiringSoon(
                eq(CLIENT_ID),
                any(Collection.class),
                any(LocalDate.class),
                eq(null)))
                .thenReturn(List.of(proj));
        when(policyRepository.findByClientId(CLIENT_ID)).thenReturn(List.of());

        List<ExpiringBalancePreviewResponse> result = service.getExpiringSoon(30, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).currencyId()).isEqualTo("points");
        assertThat(result.get(0).affectedWalletCount()).isEqualTo(5L);
        assertThat(result.get(0).totalAmountAtRisk()).isEqualByComparingTo(new BigDecimal("1500.00"));
    }

    @Test
    void getExpiringSoon_returnsEmpty_whenNoNotices() {
        when(noticeRepository.aggregateExpiringSoon(
                eq(CLIENT_ID), any(Collection.class), any(LocalDate.class), eq(null)))
                .thenReturn(List.of());
        when(policyRepository.findByClientId(CLIENT_ID)).thenReturn(List.of());

        List<ExpiringBalancePreviewResponse> result = service.getExpiringSoon(null, null);

        assertThat(result).isEmpty();
    }

    // ── feature flag disabled ─────────────────────────────────────────────────

    @Test
    void getPolicies_throwsFeatureDisabled_whenFlagOff() {
        when(featureFlagService.getEnabledFeatures(CLIENT_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.getPolicies())
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getErrorCode())
                        .isEqualTo("FEATURE_DISABLED"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UpsertBalanceExpirationPolicyRequest buildInactivityRequest(boolean enabled, int inactivityDays, int leadTimeDays) {
        UpsertBalanceExpirationPolicyRequest req = new UpsertBalanceExpirationPolicyRequest();
        req.setEnabled(enabled);
        req.setExpirationMode(ExpirationMode.INACTIVITY);
        req.setInactivityDays(inactivityDays);
        req.setLeadTimeDays(leadTimeDays);
        return req;
    }

    private UpsertBalanceExpirationPolicyRequest buildFixedDateRequest(boolean enabled, LocalDate fixedDate, int leadTimeDays) {
        UpsertBalanceExpirationPolicyRequest req = new UpsertBalanceExpirationPolicyRequest();
        req.setEnabled(enabled);
        req.setExpirationMode(ExpirationMode.FIXED_DATE);
        req.setFixedExpiryDate(fixedDate);
        req.setLeadTimeDays(leadTimeDays);
        return req;
    }

    private ExpiringBalancePreviewProjection mockProjection(String currencyId, LocalDate date, long count, BigDecimal amount) {
        return new ExpiringBalancePreviewProjection() {
            @Override public String getCurrencyId() { return currencyId; }
            @Override public LocalDate getScheduledExpiryDate() { return date; }
            @Override public long getAffectedWalletCount() { return count; }
            @Override public BigDecimal getTotalAmountAtRisk() { return amount; }
        };
    }
}
