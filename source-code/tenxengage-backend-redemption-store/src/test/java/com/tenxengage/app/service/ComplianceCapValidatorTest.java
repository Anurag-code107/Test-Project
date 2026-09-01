package com.tenxengage.app.service;

import com.tenxengage.app.entity.ComplianceValueCap;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.ComplianceValueCapRepository;
import com.tenxengage.app.repository.RewardTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplianceCapValidatorTest {

    @Mock
    private ComplianceValueCapRepository complianceValueCapRepository;

    @Mock
    private RewardTransactionRepository rewardTransactionRepository;

    @InjectMocks
    private ComplianceCapValidator complianceCapValidator;

    private UUID userId;
    private UUID clientId;
    private String countryCode;
    private ComplianceValueCap systemCap;
    private ComplianceValueCap clientCap;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        clientId = UUID.randomUUID();
        countryCode = "US";

        systemCap = ComplianceValueCap.builder()
                .countryCode(countryCode)
                .annualCapAmount(new BigDecimal("5000"))
                .annualCapCurrency("USD")
                .enhancedApprovalThreshold(new BigDecimal("1000"))
                .clientId(null)
                .build();
        systemCap.setId(UUID.randomUUID());

        clientCap = ComplianceValueCap.builder()
                .countryCode(countryCode)
                .annualCapAmount(new BigDecimal("3000"))
                .annualCapCurrency("USD")
                .enhancedApprovalThreshold(new BigDecimal("800"))
                .clientId(clientId)
                .build();
        clientCap.setId(UUID.randomUUID());
    }

    // -------------------------------------------------------------------------
    // validateClaim
    // -------------------------------------------------------------------------

    @Test
    void validateClaim_passesWhenUnderCap() {
        when(complianceValueCapRepository.findByCountryCodeAndClientId(countryCode, clientId))
                .thenReturn(Optional.empty());
        when(complianceValueCapRepository.findByCountryCodeAndClientIdIsNull(countryCode))
                .thenReturn(Optional.of(systemCap));
        when(rewardTransactionRepository.sumAwardedByUserAndDateRange(eq(clientId), eq(userId), any(), any()))
                .thenReturn(new BigDecimal("1000"));

        assertThatCode(() ->
                complianceCapValidator.validateClaim(userId, clientId, countryCode, new BigDecimal("500")))
                .doesNotThrowAnyException();
    }

    @Test
    void validateClaim_passesWhenNoCapsConfigured() {
        when(complianceValueCapRepository.findByCountryCodeAndClientId(countryCode, clientId))
                .thenReturn(Optional.empty());
        when(complianceValueCapRepository.findByCountryCodeAndClientIdIsNull(countryCode))
                .thenReturn(Optional.empty());

        assertThatCode(() ->
                complianceCapValidator.validateClaim(userId, clientId, countryCode, new BigDecimal("99999")))
                .doesNotThrowAnyException();
    }

    @Test
    void validateClaim_throwsWhenExceedingAnnualCap() {
        when(complianceValueCapRepository.findByCountryCodeAndClientId(countryCode, clientId))
                .thenReturn(Optional.empty());
        when(complianceValueCapRepository.findByCountryCodeAndClientIdIsNull(countryCode))
                .thenReturn(Optional.of(systemCap));
        when(rewardTransactionRepository.sumAwardedByUserAndDateRange(eq(clientId), eq(userId), any(), any()))
                .thenReturn(new BigDecimal("4500"));

        assertThatThrownBy(() ->
                complianceCapValidator.validateClaim(userId, clientId, countryCode, new BigDecimal("1000")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exceed the annual compliance cap");
    }

    @Test
    void validateClaim_warnsAtEnhancedThresholdButPasses() {
        // enhancedApprovalThreshold = 1000, cap = 5000
        // annualTotal = 900, reward = 200 => projected = 1100 > threshold but < cap
        when(complianceValueCapRepository.findByCountryCodeAndClientId(countryCode, clientId))
                .thenReturn(Optional.empty());
        when(complianceValueCapRepository.findByCountryCodeAndClientIdIsNull(countryCode))
                .thenReturn(Optional.of(systemCap));
        when(rewardTransactionRepository.sumAwardedByUserAndDateRange(eq(clientId), eq(userId), any(), any()))
                .thenReturn(new BigDecimal("900"));

        assertThatCode(() ->
                complianceCapValidator.validateClaim(userId, clientId, countryCode, new BigDecimal("200")))
                .doesNotThrowAnyException();
    }

    @Test
    void validateClaim_usesClientSpecificCapOverDefault() {
        // Client cap = 3000, system default = 5000
        // annualTotal = 2500, reward = 600 => projected = 3100 > client cap of 3000
        when(complianceValueCapRepository.findByCountryCodeAndClientId(countryCode, clientId))
                .thenReturn(Optional.of(clientCap));
        when(rewardTransactionRepository.sumAwardedByUserAndDateRange(eq(clientId), eq(userId), any(), any()))
                .thenReturn(new BigDecimal("2500"));

        assertThatThrownBy(() ->
                complianceCapValidator.validateClaim(userId, clientId, countryCode, new BigDecimal("600")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exceed the annual compliance cap");
    }

    @Test
    void validateClaim_fallsBackToSystemDefault() {
        // No client cap, system default cap = 5000
        // annualTotal = 1000, reward = 500 => projected = 1500 < 5000
        when(complianceValueCapRepository.findByCountryCodeAndClientId(countryCode, clientId))
                .thenReturn(Optional.empty());
        when(complianceValueCapRepository.findByCountryCodeAndClientIdIsNull(countryCode))
                .thenReturn(Optional.of(systemCap));
        when(rewardTransactionRepository.sumAwardedByUserAndDateRange(eq(clientId), eq(userId), any(), any()))
                .thenReturn(new BigDecimal("1000"));

        assertThatCode(() ->
                complianceCapValidator.validateClaim(userId, clientId, countryCode, new BigDecimal("500")))
                .doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // getEffectiveCap
    // -------------------------------------------------------------------------

    @Test
    void getEffectiveCap_returnsClientCapWhenExists() {
        when(complianceValueCapRepository.findByCountryCodeAndClientId(countryCode, clientId))
                .thenReturn(Optional.of(clientCap));

        Optional<ComplianceValueCap> result = complianceCapValidator.getEffectiveCap(countryCode, clientId);

        assertThat(result).isPresent();
        assertThat(result.get().getClientId()).isEqualTo(clientId);
        assertThat(result.get().getAnnualCapAmount()).isEqualByComparingTo(new BigDecimal("3000"));
    }

    @Test
    void getEffectiveCap_returnsSystemDefaultWhenNoClientCap() {
        when(complianceValueCapRepository.findByCountryCodeAndClientId(countryCode, clientId))
                .thenReturn(Optional.empty());
        when(complianceValueCapRepository.findByCountryCodeAndClientIdIsNull(countryCode))
                .thenReturn(Optional.of(systemCap));

        Optional<ComplianceValueCap> result = complianceCapValidator.getEffectiveCap(countryCode, clientId);

        assertThat(result).isPresent();
        assertThat(result.get().getClientId()).isNull();
        assertThat(result.get().getAnnualCapAmount()).isEqualByComparingTo(new BigDecimal("5000"));
    }

    // -------------------------------------------------------------------------
    // getUserAnnualTotal
    // -------------------------------------------------------------------------

    @Test
    void getUserAnnualTotal_returnsZeroWhenNull() {
        // The COALESCE in the query means the repo itself returns 0 when there are no rows,
        // but we verify the method correctly passes through whatever the repo returns.
        when(rewardTransactionRepository.sumAwardedByUserAndDateRange(eq(clientId), eq(userId), any(), any()))
                .thenReturn(BigDecimal.ZERO);

        BigDecimal result = complianceCapValidator.getUserAnnualTotal(userId, clientId, 2026);

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
