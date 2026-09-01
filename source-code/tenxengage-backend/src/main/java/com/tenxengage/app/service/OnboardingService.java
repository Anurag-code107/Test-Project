package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.AcceptPoliciesRequest;
import com.tenxengage.app.dto.request.CompleteOnboardingRequest;
import com.tenxengage.app.dto.request.CompleteProfileRequest;
import com.tenxengage.app.dto.request.SetConsentRequest;
import com.tenxengage.app.dto.request.SetPasswordRequest;
import com.tenxengage.app.dto.response.ConsentPreferenceResponse;
import com.tenxengage.app.dto.response.LegalPolicyResponse;
import com.tenxengage.app.dto.response.OnboardingStatusResponse;
import com.tenxengage.app.dto.response.RegionalComplianceConfigResponse;
import com.tenxengage.app.entity.ConsentRecord;
import com.tenxengage.app.entity.LegalPolicy;
import com.tenxengage.app.entity.OnboardingToken;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.RegionalComplianceConfig;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.UserLegalAcceptance;
import com.tenxengage.app.entity.enums.ConsentType;
import com.tenxengage.app.entity.enums.PolicyType;
import com.tenxengage.app.entity.enums.UserStatus;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.ConsentRecordRepository;
import com.tenxengage.app.repository.LegalPolicyRepository;
import com.tenxengage.app.repository.OnboardingTokenRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.RegionalComplianceConfigRepository;
import com.tenxengage.app.repository.UserLegalAcceptanceRepository;
import com.tenxengage.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingService.class);

    private static final int TOKEN_EXPIRY_DAYS = 7;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int STEP_SET_PASSWORD = 0;
    private static final int STEP_COMPLETE_PROFILE = 1;
    private static final int STEP_ACCEPT_POLICIES = 2;
    private static final int STEP_SET_CONSENT = 3;
    private static final int STEP_COMPLETE = 5;
    private static final String DEFAULT_REGION = "US";

    private final OnboardingTokenRepository onboardingTokenRepository;
    private final UserRepository userRepository;
    private final LegalPolicyRepository legalPolicyRepository;
    private final UserLegalAcceptanceRepository userLegalAcceptanceRepository;
    private final ConsentRecordRepository consentRecordRepository;
    private final RegionalComplianceConfigRepository regionalComplianceConfigRepository;
    private final PartnerCompanyRepository partnerCompanyRepository;
    private final PasswordEncoder passwordEncoder;

    // -------------------------------------------------------------------------
    // Token generation
    // -------------------------------------------------------------------------

    /**
     * Generates a secure onboarding token for the given user.
     * Any existing token for the user is deleted first.
     *
     * @return the raw (unhashed) token string that should be sent to the user
     */
    @Transactional
    public String generateOnboardingToken(UUID userId, UUID clientId) {
        log.info("Generating onboarding token for userId={} clientId={}", userId, clientId);

        userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        onboardingTokenRepository.deleteByUserId(userId);

        String rawToken = UUID.randomUUID().toString();
        String tokenHash = sha256(rawToken);

        OnboardingToken token = OnboardingToken.builder()
            .userId(userId)
            .clientId(clientId)
            .tokenHash(tokenHash)
            .expiresAt(Instant.now().plus(TOKEN_EXPIRY_DAYS, ChronoUnit.DAYS))
            .currentStep(STEP_SET_PASSWORD)
            .build();

        onboardingTokenRepository.save(token);
        log.info("Onboarding token created for userId={}, expires at {}", userId, token.getExpiresAt());

        return rawToken;
    }

    // -------------------------------------------------------------------------
    // Token validation
    // -------------------------------------------------------------------------

    /**
     * Validates a raw onboarding token and returns the current onboarding status.
     */
    @Transactional(readOnly = true)
    public OnboardingStatusResponse validateToken(String rawToken) {
        OnboardingToken token = resolveToken(rawToken);
        User user = findUserOrThrow(token.getUserId());
        return buildStatusResponse(user, token);
    }

    // -------------------------------------------------------------------------
    // Step 1: Set Password
    // -------------------------------------------------------------------------

    @Transactional
    public OnboardingStatusResponse setPassword(SetPasswordRequest request, String ipAddress) {
        OnboardingToken token = resolveToken(request.token());
        validateStep(token, STEP_SET_PASSWORD, "set password");

        String password = request.password();
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessRuleException(
                "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }

        User user = findUserOrThrow(token.getUserId());
        user.setPasswordHash(passwordEncoder.encode(password));
        userRepository.save(user);

        token.setCurrentStep(STEP_COMPLETE_PROFILE);
        onboardingTokenRepository.save(token);

        log.info("Password set for userId={} from ip={}", user.getId(), ipAddress);
        return buildStatusResponse(user, token);
    }

    // -------------------------------------------------------------------------
    // Step 2: Complete Profile
    // -------------------------------------------------------------------------

    @Transactional
    public OnboardingStatusResponse completeProfile(CompleteProfileRequest request, String ipAddress) {
        OnboardingToken token = resolveToken(request.token());
        validateMinimumStep(token, STEP_COMPLETE_PROFILE, "complete profile");

        User user = findUserOrThrow(token.getUserId());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhone(request.phone());
        user.setCountryCode(request.countryCode());
        userRepository.save(user);

        token.setCurrentStep(STEP_ACCEPT_POLICIES);
        onboardingTokenRepository.save(token);

        log.info("Profile completed for userId={} from ip={}", user.getId(), ipAddress);
        return buildStatusResponse(user, token);
    }

    // -------------------------------------------------------------------------
    // Step 3: Accept Policies
    // -------------------------------------------------------------------------

    @Transactional
    public OnboardingStatusResponse acceptPolicies(AcceptPoliciesRequest request,
                                                   String ipAddress, String userAgent) {
        OnboardingToken token = resolveToken(request.token());
        validateMinimumStep(token, STEP_ACCEPT_POLICIES, "accept policies");

        User user = findUserOrThrow(token.getUserId());
        UUID clientId = token.getClientId();

        List<LegalPolicy> activePolicies = legalPolicyRepository.findByClientIdAndActiveTrue(clientId);
        Set<UUID> activePolicyIds = activePolicies.stream()
            .map(p -> p.getId())
            .collect(Collectors.toSet());

        for (UUID policyId : request.policyIds()) {
            if (!activePolicyIds.contains(policyId)) {
                throw new BusinessRuleException(
                    "Policy " + policyId + " is not an active policy for this client");
            }

            if (!userLegalAcceptanceRepository.existsByUserIdAndPolicyId(user.getId(), policyId)) {
                UserLegalAcceptance acceptance = UserLegalAcceptance.builder()
                    .clientId(clientId)
                    .userId(user.getId())
                    .policyId(policyId)
                    .acceptedAt(Instant.now())
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .build();
                userLegalAcceptanceRepository.save(acceptance);
            }
        }

        validateRequiredPoliciesAccepted(user, clientId, activePolicies);

        token.setCurrentStep(STEP_SET_CONSENT);
        onboardingTokenRepository.save(token);

        log.info("Policies accepted for userId={}, policyCount={} from ip={}",
            user.getId(), request.policyIds().size(), ipAddress);
        return buildStatusResponse(user, token);
    }

    // -------------------------------------------------------------------------
    // Step 4: Set Consent Preferences
    // -------------------------------------------------------------------------

    @Transactional
    public OnboardingStatusResponse setConsent(SetConsentRequest request, String ipAddress) {
        OnboardingToken token = resolveToken(request.token());
        validateMinimumStep(token, STEP_SET_CONSENT, "set consent");

        User user = findUserOrThrow(token.getUserId());
        UUID clientId = token.getClientId();

        for (Map.Entry<String, Boolean> entry : request.consents().entrySet()) {
            ConsentType consentType;
            try {
                consentType = ConsentType.valueOf(entry.getKey());
            } catch (IllegalArgumentException e) {
                throw new BusinessRuleException("Unknown consent type: " + entry.getKey());
            }

            ConsentRecord record = ConsentRecord.builder()
                .clientId(clientId)
                .userId(user.getId())
                .consentType(consentType)
                .granted(entry.getValue())
                .recordedAt(Instant.now())
                .ipAddress(ipAddress)
                .consentVersion("1.0")
                .build();
            consentRecordRepository.save(record);
        }

        token.setCurrentStep(4);
        onboardingTokenRepository.save(token);

        log.info("Consent preferences set for userId={}, consentCount={} from ip={}",
            user.getId(), request.consents().size(), ipAddress);
        return buildStatusResponse(user, token);
    }

    // -------------------------------------------------------------------------
    // Step 5: Complete Onboarding
    // -------------------------------------------------------------------------

    @Transactional
    public OnboardingStatusResponse completeOnboarding(CompleteOnboardingRequest request) {
        OnboardingToken token = resolveToken(request.token());

        if (token.getCurrentStep() < STEP_SET_CONSENT) {
            throw new BusinessRuleException(
                "Cannot complete onboarding: required steps have not been finished. "
                + "Current step: " + token.getCurrentStep());
        }

        User user = findUserOrThrow(token.getUserId());

        if (user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            throw new BusinessRuleException(
                "User is not in PENDING_VERIFICATION status. Current status: " + user.getStatus());
        }

        user.setStatus(UserStatus.ACTIVE);
        user.setOnboardingCompletedAt(Instant.now());
        userRepository.save(user);

        token.setCompletedAt(Instant.now());
        token.setCurrentStep(STEP_COMPLETE);
        onboardingTokenRepository.save(token);

        log.info("Onboarding completed for userId={}", user.getId());
        return buildStatusResponse(user, token);
    }

    // -------------------------------------------------------------------------
    // Read-only queries
    // -------------------------------------------------------------------------

    /**
     * Returns all active legal policies for the client, annotated with whether
     * the given user has accepted each one.
     */
    @Transactional(readOnly = true)
    public List<LegalPolicyResponse> getActivePolicies(UUID clientId, UUID userId) {
        List<LegalPolicy> policies = legalPolicyRepository.findByClientIdAndActiveTrue(clientId);

        return policies.stream()
            .map(policy -> new LegalPolicyResponse(
                policy.getId(),
                policy.getPolicyType().name(),
                policy.getVersion(),
                policy.getTitle(),
                policy.getContentUrl(),
                policy.getSummary(),
                policy.getEffectiveDate(),
                userLegalAcceptanceRepository.existsByUserIdAndPolicyId(userId, policy.getId())
            ))
            .toList();
    }

    /**
     * Returns the latest consent state per consent type for the given user.
     */
    @Transactional(readOnly = true)
    public List<ConsentPreferenceResponse> getUserConsent(UUID userId) {
        List<ConsentRecord> records = consentRecordRepository.findByUserId(userId);

        // Group by consent type and keep the most recent record per type
        return records.stream()
            .collect(Collectors.groupingBy(ConsentRecord::getConsentType))
            .entrySet().stream()
            .map(entry -> {
                ConsentRecord latest = entry.getValue().stream()
                    .max(Comparator.comparing(ConsentRecord::getRecordedAt))
                    .orElseThrow();
                return new ConsentPreferenceResponse(
                    latest.getConsentType().name(),
                    latest.isGranted(),
                    latest.getRecordedAt()
                );
            })
            .toList();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Resolves a raw token string to the corresponding {@link OnboardingToken},
     * validating that it is neither expired nor already completed.
     */
    private OnboardingToken resolveToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessRuleException("Onboarding token is required");
        }

        String tokenHash = sha256(rawToken);
        OnboardingToken token = onboardingTokenRepository.findByTokenHash(tokenHash)
            .orElseThrow(() -> new BusinessRuleException("Invalid onboarding token"));

        if (token.isExpired()) {
            throw new BusinessRuleException("Onboarding token has expired");
        }

        if (token.isCompleted()) {
            throw new BusinessRuleException("Onboarding has already been completed");
        }

        return token;
    }

    private User findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    /**
     * Validates that the token is exactly at the expected step.
     * Used for steps that must be executed in strict order (e.g., set-password is always step 0).
     */
    private void validateStep(OnboardingToken token, int expectedStep, String stepName) {
        if (token.getCurrentStep() != expectedStep) {
            throw new BusinessRuleException(
                "Cannot " + stepName + " at step " + token.getCurrentStep()
                + ". Expected step: " + expectedStep);
        }
    }

    /**
     * Validates that the token is at or beyond the minimum required step.
     * Used for steps that allow re-execution (e.g., editing profile after initially completing it).
     */
    private void validateMinimumStep(OnboardingToken token, int minimumStep, String stepName) {
        if (token.getCurrentStep() < minimumStep) {
            throw new BusinessRuleException(
                "Cannot " + stepName + " at step " + token.getCurrentStep()
                + ". Minimum required step: " + minimumStep);
        }
    }

    /**
     * Ensures that all policies required by the user's region have been accepted.
     */
    private void validateRequiredPoliciesAccepted(User user, UUID clientId,
                                                  List<LegalPolicy> activePolicies) {
        RegionalComplianceConfig config = resolveRegionalConfig(user);
        Set<PolicyType> requiredTypes = getRequiredPolicyTypes(config);

        for (PolicyType requiredType : requiredTypes) {
            boolean policyExistsForType = activePolicies.stream()
                .anyMatch(p -> p.getPolicyType() == requiredType);

            if (!policyExistsForType) {
                // No active policy of this type configured for the client -- skip validation
                continue;
            }

            boolean accepted = activePolicies.stream()
                .filter(p -> p.getPolicyType() == requiredType)
                .allMatch(p -> userLegalAcceptanceRepository
                    .existsByUserIdAndPolicyId(user.getId(), p.getId()));

            if (!accepted) {
                throw new BusinessRuleException(
                    "Required policy not accepted: " + requiredType.name()
                    + ". This policy is required for region: " + config.getRegionCode());
            }
        }
    }

    /**
     * Determines which policy types are required based on regional compliance configuration.
     */
    private Set<PolicyType> getRequiredPolicyTypes(RegionalComplianceConfig config) {
        Set<PolicyType> required = EnumSet.noneOf(PolicyType.class);
        if (config.isPrivacyNoticeRequired()) {
            required.add(PolicyType.PRIVACY_NOTICE);
        }
        if (config.isTermsOfServiceRequired()) {
            required.add(PolicyType.TERMS_OF_SERVICE);
        }
        if (config.isAntiBriberyRequired()) {
            required.add(PolicyType.ANTI_BRIBERY_POLICY);
        }
        return required;
    }

    /**
     * Resolves the regional compliance configuration for a user.
     * Resolution order: user countryCode -> partner company region -> fallback "US".
     */
    private RegionalComplianceConfig resolveRegionalConfig(User user) {
        // 1. Try the user's country code directly
        if (user.getCountryCode() != null && !user.getCountryCode().isBlank()) {
            RegionalComplianceConfig config = regionalComplianceConfigRepository
                .findByRegionCode(user.getCountryCode())
                .orElse(null);
            if (config != null) {
                return config;
            }
        }

        // 2. Try the partner company's top-level location (region)
        if (user.getPartnerCompanyId() != null) {
            PartnerCompany company = partnerCompanyRepository
                .findById(user.getPartnerCompanyId())
                .orElse(null);
            if (company != null && company.getLocationAssignments() != null) {
                String regionCode = company.getLocationAssignments().stream()
                    .filter(pcl -> pcl.getLocationValue().getLevel().getDepth() == 0)
                    .map(pcl -> pcl.getLocationValue().getCode() != null
                        ? pcl.getLocationValue().getCode()
                        : pcl.getLocationValue().getName())
                    .findFirst().orElse(null);
                if (regionCode != null && !regionCode.isBlank()) {
                    RegionalComplianceConfig config = regionalComplianceConfigRepository
                        .findByRegionCode(regionCode)
                        .orElse(null);
                    if (config != null) {
                        return config;
                    }
                }
            }
        }

        // 3. Fallback to US
        return regionalComplianceConfigRepository.findByRegionCode(DEFAULT_REGION)
            .orElseThrow(() -> new BusinessRuleException(
                "Default regional compliance configuration (US) is not configured. "
                + "Please contact an administrator."));
    }

    /**
     * Builds the status response for the current state of the user's onboarding.
     */
    private OnboardingStatusResponse buildStatusResponse(User user, OnboardingToken token) {
        RegionalComplianceConfig config = resolveRegionalConfig(user);
        String region = config.getRegionCode();

        RegionalComplianceConfigResponse configResponse = new RegionalComplianceConfigResponse(
            config.getRegionCode(),
            config.getRegionName(),
            config.isPrivacyNoticeRequired(),
            config.isTermsOfServiceRequired(),
            config.isAntiBriberyRequired(),
            config.isConsentAiVisible(),
            config.isConsentMarketingVisible(),
            config.isConsentAnalyticsVisible(),
            config.isCookieNoticeVisible()
        );

        return new OnboardingStatusResponse(
            user.getId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            token.getCurrentStep(),
            token.isCompleted(),
            region,
            configResponse
        );
    }

    /**
     * Computes the SHA-256 hex digest of the given input.
     * Used for token hashing (deterministic, unlike BCrypt) so tokens can be looked up by hash.
     */
    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available on all JVMs
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
