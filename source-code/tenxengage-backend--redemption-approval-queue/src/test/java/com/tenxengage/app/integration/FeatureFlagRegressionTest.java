package com.tenxengage.app.integration;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import org.junit.jupiter.api.Tag;
import com.tenxengage.app.dto.request.AcceptPoliciesRequest;
import com.tenxengage.app.dto.request.CompleteOnboardingRequest;
import com.tenxengage.app.dto.request.CompleteProfileRequest;
import com.tenxengage.app.dto.request.SetConsentRequest;
import com.tenxengage.app.dto.request.SetPasswordRequest;
import com.tenxengage.app.dto.response.OnboardingStatusResponse;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.LegalPolicy;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.RegionalComplianceConfig;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.ClientStatus;
import com.tenxengage.app.entity.enums.PolicyType;
import com.tenxengage.app.entity.enums.SubscriptionTier;
import com.tenxengage.app.entity.enums.UserStatus;
import com.tenxengage.app.repository.AuditLogRepository;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.ConsentRecordRepository;
import com.tenxengage.app.repository.FeatureFlagRepository;
import com.tenxengage.app.repository.LegalPolicyRepository;
import com.tenxengage.app.repository.OnboardingTokenRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.RegionalComplianceConfigRepository;
import com.tenxengage.app.repository.UserLegalAcceptanceRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.TenantContext;
import com.tenxengage.app.service.ComplianceCapValidator;
import com.tenxengage.app.service.DataExportService;
import com.tenxengage.app.service.DataRetentionService;
import com.tenxengage.app.service.FeatureFlagService;
import com.tenxengage.app.service.OnboardingService;
import com.tenxengage.app.service.UserAnonymizationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regression tests that verify existing functionality works correctly when compliance
 * feature flags are OFF for the test client's subscription tier. Compliance feature
 * flags are seeded as enabled only for ENTERPRISE tier, so these tests use a
 * STARTER-tier client to confirm the application behaves identically to pre-compliance
 * code when the flags are not enabled for the client's tier.
 *
 * <p>Not {@code @Transactional} because {@code dataSubjectRights_alwaysAvailable} calls
 * {@code UserAnonymizationService} which uses {@code AuditLogService} with
 * {@code Propagation.REQUIRES_NEW}. A test-level transaction would be invisible to
 * that new transaction, causing FK violations. Cleanup is done in {@code @AfterEach}.
 */
@Tag("integration")
class FeatureFlagRegressionTest extends AbstractLocalIntegrationTest {

    @Autowired
    private FeatureFlagService featureFlagService;

    @Autowired
    private FeatureFlagRepository featureFlagRepository;

    @Autowired
    private ComplianceCapValidator complianceCapValidator;

    @Autowired
    private OnboardingService onboardingService;

    @Autowired
    private DataExportService dataExportService;

    @Autowired
    private UserAnonymizationService userAnonymizationService;

    @Autowired
    private DataRetentionService dataRetentionService;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PartnerCompanyRepository partnerCompanyRepository;

    @Autowired
    private LegalPolicyRepository legalPolicyRepository;

    @Autowired
    private RegionalComplianceConfigRepository regionalComplianceConfigRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private OnboardingTokenRepository onboardingTokenRepository;

    @Autowired
    private UserLegalAcceptanceRepository userLegalAcceptanceRepository;

    @Autowired
    private ConsentRecordRepository consentRecordRepository;

    private Client testClient;
    private PartnerCompany testPartnerCompany;
    private LegalPolicy privacyPolicy;
    private LegalPolicy termsPolicy;

    // Track created entities for cleanup
    private final List<UUID> createdUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        testClient = clientRepository.saveAndFlush(Client.builder()
                .name("Feature Flag Regression Client")
                .subdomain("ff-test-" + UUID.randomUUID().toString().substring(0, 8))
                .status(ClientStatus.ACTIVE)
                .subscriptionTier(SubscriptionTier.STARTER)
                .build());

        String partnerSuffix = UUID.randomUUID().toString().substring(0, 8);
        testPartnerCompany = partnerCompanyRepository.saveAndFlush(PartnerCompany.builder()
                .name("FF Test Partner " + partnerSuffix)
                .clientId(testClient.getId())
                .externalPartnerId("CT-FF-" + partnerSuffix)
                .build());

        privacyPolicy = legalPolicyRepository.saveAndFlush(LegalPolicy.builder()
                .clientId(testClient.getId())
                .policyType(PolicyType.PRIVACY_NOTICE)
                .version("1.0")
                .title("Privacy Notice")
                .contentUrl("https://example.com/privacy")
                .summary("Privacy notice summary")
                .active(true)
                .build());

        termsPolicy = legalPolicyRepository.saveAndFlush(LegalPolicy.builder()
                .clientId(testClient.getId())
                .policyType(PolicyType.TERMS_OF_SERVICE)
                .version("1.0")
                .title("Terms of Service")
                .contentUrl("https://example.com/terms")
                .summary("Terms of service summary")
                .active(true)
                .build());

        TenantContext.setClientId(testClient.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();

        // Clean up in reverse dependency order
        UUID clientId = testClient.getId();

        // Audit logs (created by REQUIRES_NEW transactions during anonymization)
        auditLogRepository.findAll().stream()
                .filter(log -> clientId.equals(log.getClientId()))
                .forEach(log -> auditLogRepository.deleteById(log.getId()));

        // Consent records and legal acceptances referencing users
        for (UUID userId : createdUserIds) {
            consentRecordRepository.findByUserId(userId).forEach(
                    r -> consentRecordRepository.deleteById(r.getId()));
            userLegalAcceptanceRepository.findByUserId(userId).forEach(
                    a -> userLegalAcceptanceRepository.deleteById(a.getId()));
            onboardingTokenRepository.findByUserId(userId).ifPresent(
                    t -> onboardingTokenRepository.deleteById(t.getId()));
        }

        // Users
        for (UUID userId : createdUserIds) {
            userRepository.deleteById(userId);
        }

        // Legal policies referencing client
        legalPolicyRepository.deleteById(privacyPolicy.getId());
        legalPolicyRepository.deleteById(termsPolicy.getId());

        // Partner company
        partnerCompanyRepository.deleteById(testPartnerCompany.getId());

        // Client
        clientRepository.deleteById(clientId);

        createdUserIds.clear();
    }

    @Test
    void featureFlagService_returnsNoComplianceFlags_forStarterTier() {
        // Compliance feature flags are seeded as enabled only for ENTERPRISE tier.
        // Since our test client is STARTER tier, no compliance flags should be enabled.
        List<String> enabledFeatures = featureFlagService.getEnabledFeatures(testClient.getId());

        // Verify no compliance-related feature keys are enabled for STARTER tier
        assertThat(enabledFeatures)
                .filteredOn(key -> key.toLowerCase().contains("compliance")
                        || key.toLowerCase().contains("kyc")
                        || key.toLowerCase().contains("aml")
                        || key.toLowerCase().contains("government_deal"))
                .isEmpty();
    }

    @Test
    void complianceCapValidator_passesWhenNoCapsConfiguredForCountry() {
        // Default compliance caps are seeded for EU countries but not for US.
        // The validator should pass any claim amount for countries without caps configured.
        User user = createActiveUser();

        assertThatCode(() ->
                complianceCapValidator.validateClaim(
                        user.getId(), testClient.getId(), "US", new BigDecimal("99999.99")))
                .as("Cap validation should pass when no caps are configured for the country")
                .doesNotThrowAnyException();
    }

    @Test
    void onboardingFlowWorks_regardlessOfFlags() {
        // The onboarding flow is always active (not feature-flagged).
        // Verify the full flow works with no compliance feature flags enabled.
        User user = createPendingUser();

        String rawToken = onboardingService.generateOnboardingToken(user.getId(), testClient.getId());
        assertThat(rawToken).isNotBlank();

        // Step 0: Validate token
        OnboardingStatusResponse status = onboardingService.validateToken(rawToken);
        assertThat(status.currentStep()).isEqualTo(0);
        assertThat(status.completed()).isFalse();

        // Step 1: Set password
        OnboardingStatusResponse afterPassword = onboardingService.setPassword(
                new SetPasswordRequest(rawToken, "SecurePassword123!"), "127.0.0.1");
        assertThat(afterPassword.currentStep()).isEqualTo(1);

        // Step 2: Complete profile
        OnboardingStatusResponse afterProfile = onboardingService.completeProfile(
                new CompleteProfileRequest(rawToken, "John", "Doe", "+1234567890", "US"),
                "127.0.0.1");
        assertThat(afterProfile.currentStep()).isEqualTo(2);

        // Step 3: Accept policies
        List<UUID> policyIds = List.of(privacyPolicy.getId(), termsPolicy.getId());
        OnboardingStatusResponse afterPolicies = onboardingService.acceptPolicies(
                new AcceptPoliciesRequest(rawToken, policyIds),
                "127.0.0.1", "TestBrowser/1.0");
        assertThat(afterPolicies.currentStep()).isEqualTo(3);

        // Step 4: Set consent
        Map<String, Boolean> consents = Map.of(
                "AI_RECOMMENDATIONS", true,
                "MARKETING_EMAIL", false,
                "ANALYTICS", true);
        onboardingService.setConsent(new SetConsentRequest(rawToken, consents), "127.0.0.1");

        // Step 5: Complete onboarding
        OnboardingStatusResponse afterComplete = onboardingService.completeOnboarding(
                new CompleteOnboardingRequest(rawToken));
        assertThat(afterComplete.currentStep()).isEqualTo(5);
        assertThat(afterComplete.completed()).isTrue();

        // Verify user is now ACTIVE
        User reloadedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloadedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void dataSubjectRights_alwaysAvailable() {
        // Data export and anonymization are not feature-flagged; they must always work.
        User user = createActiveUser();

        // Data export should work
        Map<String, Object> exportData = dataExportService.exportUserData(
                user.getId(), testClient.getId());
        assertThat(exportData).isNotNull();
        assertThat(exportData).containsKeys("exportedAt", "profile");

        Map<String, Object> profile = (Map<String, Object>) exportData.get("profile");
        assertThat(profile.get("email")).isEqualTo(user.getEmail());

        // Anonymization should work
        userAnonymizationService.anonymizeUser(user.getId(), testClient.getId());

        User anonymized = userRepository.findById(user.getId()).orElseThrow();
        assertThat(anonymized.getStatus()).isEqualTo(UserStatus.ANONYMIZED);
        assertThat(anonymized.getEmail()).startsWith("anonymized-");
    }

    @Test
    void retentionPolicies_returnDefaults_whenNoOverrides() {
        // With no client-specific retention policy overrides, the service should
        // return system defaults (which may be empty in a clean test DB, and that is fine).
        List<com.tenxengage.app.entity.RetentionPolicy> policies =
                dataRetentionService.getRetentionPolicies(testClient.getId());

        // The result should be a list (possibly empty if no system defaults are seeded).
        // The key assertion is that it does not throw an error.
        assertThat(policies).isNotNull();

        // System defaults should also be retrievable without error
        List<com.tenxengage.app.entity.RetentionPolicy> defaults =
                dataRetentionService.getSystemDefaults();
        assertThat(defaults).isNotNull();
    }

    @Test
    void claimsWorkWithoutComplianceFlags() {
        // Verify the full compliance-related service stack does not interfere
        // when no compliance feature flags or caps are configured.

        // Feature flag service returns empty/no compliance flags
        List<String> flags = featureFlagService.getEnabledFeatures(testClient.getId());
        assertThat(flags).isNotNull();

        // Cap validator passes with no caps configured
        User user = createActiveUser();
        assertThatCode(() ->
                complianceCapValidator.validateClaim(
                        user.getId(), testClient.getId(), "US", new BigDecimal("1000.00")))
                .doesNotThrowAnyException();

        // Cap validator also passes for a country with no configuration at all
        assertThatCode(() ->
                complianceCapValidator.validateClaim(
                        user.getId(), testClient.getId(), "ZZ", new BigDecimal("50000.00")))
                .as("Validation should pass for unconfigured country codes")
                .doesNotThrowAnyException();

        // getUserAnnualTotal returns zero when no transactions exist
        BigDecimal annualTotal = complianceCapValidator.getUserAnnualTotal(
                user.getId(), testClient.getId(), 2026);
        assertThat(annualTotal).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User createPendingUser() {
        User user = userRepository.saveAndFlush(User.builder()
                .email("ff-pending-" + UUID.randomUUID() + "@test.com")
                .firstName("Pending")
                .lastName("User")
                .passwordHash("$2a$10$placeholder")
                .status(UserStatus.PENDING_VERIFICATION)
                .clientId(testClient.getId())
                .partnerCompanyId(testPartnerCompany.getId())
                .build());
        createdUserIds.add(user.getId());
        return user;
    }

    private User createActiveUser() {
        User user = userRepository.saveAndFlush(User.builder()
                .email("ff-active-" + UUID.randomUUID() + "@test.com")
                .firstName("Active")
                .lastName("User")
                .passwordHash("$2a$10$dummyBcryptHashForTesting123456789012345678901234")
                .status(UserStatus.ACTIVE)
                .clientId(testClient.getId())
                .partnerCompanyId(testPartnerCompany.getId())
                .countryCode("US")
                .build());
        createdUserIds.add(user.getId());
        return user;
    }
}
