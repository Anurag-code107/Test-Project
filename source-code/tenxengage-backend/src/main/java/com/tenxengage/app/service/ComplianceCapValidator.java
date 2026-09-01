package com.tenxengage.app.service;

import com.tenxengage.app.entity.ComplianceValueCap;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.ComplianceValueCapRepository;
import com.tenxengage.app.repository.RewardTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Service
public class ComplianceCapValidator {

    private static final Logger log = LoggerFactory.getLogger(ComplianceCapValidator.class);

    private final ComplianceValueCapRepository complianceValueCapRepository;
    private final RewardTransactionRepository rewardTransactionRepository;

    public ComplianceCapValidator(ComplianceValueCapRepository complianceValueCapRepository,
                                  RewardTransactionRepository rewardTransactionRepository) {
        this.complianceValueCapRepository = complianceValueCapRepository;
        this.rewardTransactionRepository = rewardTransactionRepository;
    }

    /**
     * Validates that a reward claim does not cause the user to exceed the annual compliance cap
     * for the specified country. Checks the cumulative total for the current year plus this claim
     * against both the enhanced approval threshold and the hard annual cap.
     *
     * @throws BusinessRuleException if the claim would exceed the annual cap
     */
    @Transactional(readOnly = true)
    public void validateClaim(UUID userId, UUID clientId, String countryCode, BigDecimal rewardAmount) {
        Optional<ComplianceValueCap> capOpt = getEffectiveCap(countryCode, clientId);
        if (capOpt.isEmpty()) {
            log.debug("No compliance cap configured for country={}, clientId={}", countryCode, clientId);
            return;
        }

        ComplianceValueCap cap = capOpt.get();
        int currentYear = Year.now(ZoneOffset.UTC).getValue();
        BigDecimal annualTotal = getUserAnnualTotal(userId, clientId, currentYear);
        BigDecimal projectedTotal = annualTotal.add(rewardAmount);

        log.debug("Compliance cap check: userId={}, country={}, annualTotal={}, rewardAmount={}, "
                + "projectedTotal={}, cap={}", userId, countryCode, annualTotal, rewardAmount,
                projectedTotal, cap.getAnnualCapAmount());

        if (projectedTotal.compareTo(cap.getAnnualCapAmount()) > 0) {
            BigDecimal remaining = cap.getAnnualCapAmount().subtract(annualTotal).max(BigDecimal.ZERO);
            throw new BusinessRuleException(String.format(
                    "Reward of %s %s would exceed the annual compliance cap of %s %s for country %s. "
                    + "Current annual total: %s. Remaining allowance: %s",
                    rewardAmount, cap.getAnnualCapCurrency(),
                    cap.getAnnualCapAmount(), cap.getAnnualCapCurrency(),
                    countryCode, annualTotal, remaining));
        }

        if (projectedTotal.compareTo(cap.getEnhancedApprovalThreshold()) > 0) {
            log.warn("Enhanced approval required: userId={}, country={}, projectedTotal={}, threshold={}",
                    userId, countryCode, projectedTotal, cap.getEnhancedApprovalThreshold());
        }
    }

    /**
     * Returns the total reward amount awarded to a user within a specific calendar year.
     */
    @Transactional(readOnly = true)
    public BigDecimal getUserAnnualTotal(UUID userId, UUID clientId, int year) {
        Instant startOfYear = Year.of(year).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant startOfNextYear = Year.of(year + 1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return rewardTransactionRepository.sumAwardedByUserAndDateRange(
                clientId, userId, startOfYear, startOfNextYear);
    }

    /**
     * Returns the effective compliance value cap for a country, applying client-specific overrides.
     * Client-specific caps take precedence over system defaults.
     */
    @Transactional(readOnly = true)
    public Optional<ComplianceValueCap> getEffectiveCap(String countryCode, UUID clientId) {
        // Client-specific override takes precedence
        Optional<ComplianceValueCap> clientCap =
                complianceValueCapRepository.findByCountryCodeAndClientId(countryCode, clientId);
        if (clientCap.isPresent()) {
            return clientCap;
        }
        // Fall back to system default
        return complianceValueCapRepository.findByCountryCodeAndClientIdIsNull(countryCode);
    }
}
