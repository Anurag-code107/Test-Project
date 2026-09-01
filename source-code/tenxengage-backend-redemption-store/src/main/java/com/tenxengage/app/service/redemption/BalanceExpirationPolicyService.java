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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// Note: BalanceExpiryNoticeService injected below — circular-safe because Spring resolves
// the @Service bean graph lazily. Both services are in the same package; no proxy needed.

@Service
@Transactional(readOnly = true)
public class BalanceExpirationPolicyService {

    private static final Logger log = LoggerFactory.getLogger(BalanceExpirationPolicyService.class);

    private static final String FEATURE_KEY = "reward_balance_expiration";
    private static final int MIN_INACTIVITY_DAYS = 30;
    private static final int MAX_INACTIVITY_DAYS = 1825;

    private static final Set<String> VALID_CURRENCY_IDS = Set.of("cash", "points", "credits", "tickets");

    private final BalanceExpirationPolicyRepository policyRepository;
    private final BalanceExpiryNoticeRepository noticeRepository;
    private final TenantValidator tenantValidator;
    private final FeatureFlagService featureFlagService;
    private final BalanceExpiryNoticeService balanceExpiryNoticeService;

    public BalanceExpirationPolicyService(BalanceExpirationPolicyRepository policyRepository,
                                          BalanceExpiryNoticeRepository noticeRepository,
                                          TenantValidator tenantValidator,
                                          FeatureFlagService featureFlagService,
                                          BalanceExpiryNoticeService balanceExpiryNoticeService) {
        this.policyRepository = policyRepository;
        this.noticeRepository = noticeRepository;
        this.tenantValidator = tenantValidator;
        this.featureFlagService = featureFlagService;
        this.balanceExpiryNoticeService = balanceExpiryNoticeService;
    }

    public List<BalanceExpirationPolicyResponse> getPolicies() {
        UUID clientId = tenantValidator.getCurrentClientId();
        checkFeatureEnabled(clientId);
        return policyRepository.findByClientId(clientId).stream()
                .map(BalanceExpirationPolicyResponse::from)
                .toList();
    }

    @Transactional
    public BalanceExpirationPolicyResponse upsertPolicy(String currencyId,
                                                         UpsertBalanceExpirationPolicyRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();
        checkFeatureEnabled(clientId);
        validateCurrencyId(currencyId);
        validateRequest(request);

        Optional<BalanceExpirationPolicy> existing =
                policyRepository.findByClientIdAndCurrencyId(clientId, currencyId);

        BalanceExpirationPolicy policy = existing.orElseGet(() ->
                BalanceExpirationPolicy.builder()
                        .clientId(clientId)
                        .currencyId(currencyId)
                        .build()
        );

        boolean wasPreviouslyEnabled = policy.isEnabled();
        boolean isBeingEnabled = Boolean.TRUE.equals(request.getEnabled());
        boolean isMaterialChange = isMaterialChange(policy, request);

        // Detect disable or relax BEFORE updating the policy fields (FR-09.10, AC-5)
        boolean isDisabling = wasPreviouslyEnabled && !isBeingEnabled;
        boolean isRelaxing = wasPreviouslyEnabled && isBeingEnabled && isMaterialChange;
        boolean shouldCancelPending = isDisabling || isRelaxing;

        policy.setEnabled(isBeingEnabled);
        policy.setExpirationMode(request.getExpirationMode());
        policy.setInactivityDays(request.getInactivityDays());
        policy.setFixedExpiryDate(request.getFixedExpiryDate());
        policy.setLeadTimeDays(request.getLeadTimeDays());

        // Set enabled_at when enabling for the first time, re-enabling, or on material change (FR-09.3)
        // On relax: reset enabled_at to restart the grace window (spec.md § Workflow / Status Transitions)
        if (isBeingEnabled && (!wasPreviouslyEnabled || isMaterialChange)) {
            policy.setEnabledAt(Instant.now());
        }

        BalanceExpirationPolicy saved = policyRepository.save(policy);

        // Cancel pending SCHEDULED/NOTIFIED notices when disabling or relaxing (FR-09.10, AC-5)
        // This runs within the same @Transactional so notice cancellations and policy save are atomic.
        // The notification events are registered afterCommit so they never fire for a rolled-back save.
        if (shouldCancelPending && saved.getId() != null) {
            int cancelledCount = balanceExpiryNoticeService.cancelPendingForPolicy(saved.getId(), clientId);
            log.info("step=balance_expiry_cancelled currencyId={} cancelledCount={} policyId={} reason={}",
                    currencyId, cancelledCount, saved.getId(), isDisabling ? "disabled" : "relaxed");
        }

        log.info("Balance expiration policy upserted: currencyId={} enabled={} expirationMode={} clientId={}",
                currencyId, isBeingEnabled, request.getExpirationMode(), clientId);

        return BalanceExpirationPolicyResponse.from(saved);
    }

    public List<ExpiringBalancePreviewResponse> getExpiringSoon(Integer withinDays, String currencyId) {
        UUID clientId = tenantValidator.getCurrentClientId();
        checkFeatureEnabled(clientId);

        int days = resolveWithinDays(withinDays, clientId);
        LocalDate upToDate = LocalDate.now(ZoneOffset.UTC).plusDays(days);

        List<ExpiryNoticeStatus> statuses = List.of(ExpiryNoticeStatus.SCHEDULED, ExpiryNoticeStatus.NOTIFIED);

        List<ExpiringBalancePreviewProjection> projections =
                noticeRepository.aggregateExpiringSoon(clientId, statuses, upToDate, currencyId);

        return projections.stream()
                .map(p -> ExpiringBalancePreviewResponse.of(
                        p.getCurrencyId(),
                        p.getScheduledExpiryDate(),
                        p.getAffectedWalletCount(),
                        p.getTotalAmountAtRisk() != null ? p.getTotalAmountAtRisk() : BigDecimal.ZERO
                ))
                .toList();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void checkFeatureEnabled(UUID clientId) {
        List<String> enabled = featureFlagService.getEnabledFeatures(clientId);
        if (!enabled.contains(FEATURE_KEY)) {
            throw new BusinessRuleException("FEATURE_DISABLED",
                    "The reward_balance_expiration feature is not enabled for this tenant");
        }
    }

    private void validateCurrencyId(String currencyId) {
        if (currencyId == null || !VALID_CURRENCY_IDS.contains(currencyId.toLowerCase())) {
            throw new BusinessRuleException("ERR_INVALID_CURRENCY",
                    "Unrecognised currency type: " + currencyId);
        }
    }

    private void validateRequest(UpsertBalanceExpirationPolicyRequest request) {
        ExpirationMode mode = request.getExpirationMode();
        Integer leadTimeDays = request.getLeadTimeDays();

        if (leadTimeDays == null || leadTimeDays < 1) {
            log.warn("leadTimeDays invalid: {}", leadTimeDays);
            throw new BusinessRuleException("ERR_LEAD_TIME_INVALID",
                    "Lead time must be at least 1 day and less than the inactivity period");
        }

        if (mode == ExpirationMode.INACTIVITY) {
            Integer inactivityDays = request.getInactivityDays();
            if (inactivityDays == null) {
                log.warn("inactivityDays is required for INACTIVITY mode");
                throw new BusinessRuleException("ERR_INACTIVITY_DAYS_REQUIRED",
                        "Inactivity period is required when mode is INACTIVITY");
            }
            if (inactivityDays < MIN_INACTIVITY_DAYS || inactivityDays > MAX_INACTIVITY_DAYS) {
                log.warn("inactivityDays out of bounds: {}", inactivityDays);
                throw new BusinessRuleException("ERR_INACTIVITY_DAYS_BOUNDS",
                        "Inactivity period must be between " + MIN_INACTIVITY_DAYS + " and " + MAX_INACTIVITY_DAYS + " days");
            }
            if (leadTimeDays >= inactivityDays) {
                log.warn("leadTimeDays={} >= inactivityDays={}", leadTimeDays, inactivityDays);
                throw new BusinessRuleException("ERR_LEAD_TIME_GTE_INACTIVITY",
                        "Lead time must be at least 1 day and less than the inactivity period");
            }
            if (request.getFixedExpiryDate() != null) {
                log.warn("fixedExpiryDate must be null for INACTIVITY mode");
                throw new BusinessRuleException("ERR_MODE_FIELD_MISMATCH",
                        "fixedExpiryDate must be null when expirationMode is INACTIVITY");
            }
        } else if (mode == ExpirationMode.FIXED_DATE) {
            LocalDate fixedExpiryDate = request.getFixedExpiryDate();
            if (fixedExpiryDate == null) {
                log.warn("fixedExpiryDate is required for FIXED_DATE mode");
                throw new BusinessRuleException("ERR_FIXED_EXPIRY_DATE_REQUIRED",
                        "Fixed expiry date is required when mode is FIXED_DATE");
            }
            if (!fixedExpiryDate.isAfter(LocalDate.now(ZoneOffset.UTC))) {
                log.warn("fixedExpiryDate is not in the future: {}", fixedExpiryDate);
                throw new BusinessRuleException("ERR_FIXED_EXPIRY_DATE_PAST",
                        "Fixed expiry date must be in the future");
            }
            if (request.getInactivityDays() != null) {
                log.warn("inactivityDays must be null for FIXED_DATE mode");
                throw new BusinessRuleException("ERR_MODE_FIELD_MISMATCH",
                        "inactivityDays must be null when expirationMode is FIXED_DATE");
            }
        }
    }

    private boolean isMaterialChange(BalanceExpirationPolicy existing, UpsertBalanceExpirationPolicyRequest request) {
        if (existing.getId() == null) {
            // New entity — treat as material change when enabling
            return false;
        }
        // Material change: mode, inactivityDays, fixedExpiryDate, or leadTimeDays changed
        return !Objects.equals(existing.getExpirationMode(), request.getExpirationMode())
                || !Objects.equals(existing.getInactivityDays(), request.getInactivityDays())
                || !Objects.equals(existing.getFixedExpiryDate(), request.getFixedExpiryDate())
                || !Objects.equals(existing.getLeadTimeDays(), request.getLeadTimeDays());
    }

    private int resolveWithinDays(Integer withinDays, UUID clientId) {
        if (withinDays != null && withinDays > 0) {
            return withinDays;
        }
        // Default: max configured leadTimeDays across the tenant's policies
        return policyRepository.findByClientId(clientId).stream()
                .mapToInt(BalanceExpirationPolicy::getLeadTimeDays)
                .max()
                .orElse(30);
    }
}
