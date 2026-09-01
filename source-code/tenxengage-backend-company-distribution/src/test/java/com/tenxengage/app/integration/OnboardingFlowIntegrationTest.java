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
import com.tenxengage.app.entity.OnboardingToken;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.RegionalComplianceConfig;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.UserLegalAcceptance;
import com.tenxengage.app.entity.enums.ClientStatus;
import com.tenxengage.app.entity.enums.PolicyType;
import com.tenxengage.app.entity.enums.SubscriptionTier;
import com.tenxengage.app.entity.enums.UserStatus;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.LegalPolicyRepository;
import com.tenxengage.app.repository.OnboardingTokenRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.RegionalComplianceConfigRepository;
import com.tenxengage.app.repository.UserLegalAcceptanceRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.service.OnboardingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
@Tag("integration")
class OnboardingFlowIntegrationTest extends AbstractLocalIntegrationTest {

    @Autowired
    private OnboardingService onboardingService;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LegalPolicyRepository legalPolicyRepository;

    @Autowired
    private RegionalComplianceConfigRepository regionalComplianceConfigRepository;

    @Autowired
    private PartnerCompanyRepository partnerCompanyRepository;

    @Autowired
    private OnboardingTokenRepository onboardingTokenRepository;

    @Autowired
    private UserLegalAcceptanceRepository userLegalAcceptanceRepository;

    private Client testClient;
    private LegalPolicy privacyPolicy;
    private LegalPolicy termsPolicy;
    private LegalPolicy antiBriberyPolicy;
    private RegionalComplianceConfig usComplianceConfig;
    private PartnerCompany testPartnerCompany;

    @BeforeEach
    void setUp() {
        testClient = clientRepository.save(Client.builder()
                .name("Integration Test Client")
                .subdomain("integration-test-" + UUID.randomUUID().toString().substring(0, 8))
                .status(ClientStatus.ACTIVE)
                .subscriptionTier(SubscriptionTier.ENTERPRISE)
                .build());

        privacyPolicy = legalPolicyRepository.save(LegalPolicy.builder()
                .clientId(testClient.getId())
                .policyType(PolicyType.PRIVACY_NOTICE)
                .version("1.0")
                .title("Privacy Notice")
                .contentUrl("https://example.com/privacy")
                .summary("Privacy notice summary")
                .active(true)
                .build());

        termsPolicy = legalPolicyRepository.save(LegalPolicy.builder()
                .clientId(testClient.getId())
                .policyType(PolicyType.TERMS_OF_SERVICE)
                .version("1.0")
                .title("Terms of Service")
                .contentUrl("https://example.com/terms")
                .summary("Terms of service summary")
                .active(true)
                .build());

        antiBriberyPolicy = legalPolicyRepository.save(LegalPolicy.builder()
                .clientId(testClient.getId())
                .policyType(PolicyType.ANTI_BRIBERY_POLICY)
                .version("1.0")
                .title("Anti-Bribery Policy")
                .contentUrl("https://example.com/anti-bribery")
                .summary("Anti-bribery policy summary")
                .active(true)
                .build());

        usComplianceConfig = regionalComplianceConfigRepository.findByRegionCode("US")
                .orElseThrow(() -> new IllegalStateException(
                        "US regional compliance config must exist in baseline seed data"));

        String partnerSuffix = UUID.randomUUID().toString().substring(0, 8);
        testPartnerCompany = partnerCompanyRepository.save(PartnerCompany.builder()
                .name("Test Partner " + partnerSuffix)
                .clientId(testClient.getId())
                .externalPartnerId("CT-TEST-" + partnerSuffix)
                .build());
    }

    @Test
    void testCompleteOnboardingFlow() {
        // Step 0: Create a user in PENDING_VERIFICATION status
        User user = createPendingUser();

        // Generate an onboarding token
        String rawToken = onboardingService.generateOnboardingToken(user.getId(), testClient.getId());
        assertThat(rawToken).isNotBlank();

        // Validate the token - should start at step 0
        OnboardingStatusResponse status = onboardingService.validateToken(rawToken);
        assertThat(status.currentStep()).isEqualTo(0);
        assertThat(status.completed()).isFalse();
        assertThat(status.userId()).isEqualTo(user.getId());

        // Step 1: Set password - should advance to step 1
        OnboardingStatusResponse afterPassword = onboardingService.setPassword(
                new SetPasswordRequest(rawToken, "SecurePassword123!"), "127.0.0.1");
        assertThat(afterPassword.currentStep()).isEqualTo(1);

        // Step 2: Complete profile - should advance to step 2
        OnboardingStatusResponse afterProfile = onboardingService.completeProfile(
                new CompleteProfileRequest(rawToken, "John", "Doe", "+1234567890", "US"),
                "127.0.0.1");
        assertThat(afterProfile.currentStep()).isEqualTo(2);

        // Step 3: Accept all required policies - should advance to step 3
        List<UUID> policyIds = List.of(
                privacyPolicy.getId(),
                termsPolicy.getId(),
                antiBriberyPolicy.getId());
        OnboardingStatusResponse afterPolicies = onboardingService.acceptPolicies(
                new AcceptPoliciesRequest(rawToken, policyIds),
                "127.0.0.1", "TestBrowser/1.0");
        assertThat(afterPolicies.currentStep()).isEqualTo(3);

        // Step 4: Set consent preferences
        Map<String, Boolean> consents = Map.of(
                "AI_RECOMMENDATIONS", true,
                "MARKETING_EMAIL", false,
                "ANALYTICS", true);
        onboardingService.setConsent(
                new SetConsentRequest(rawToken, consents), "127.0.0.1");

        // Step 5: Complete onboarding - should set step to 5
        OnboardingStatusResponse afterComplete = onboardingService.completeOnboarding(
                new CompleteOnboardingRequest(rawToken));
        assertThat(afterComplete.currentStep()).isEqualTo(5);
        assertThat(afterComplete.completed()).isTrue();

        // Verify user status in DB
        User reloadedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloadedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(reloadedUser.getOnboardingCompletedAt()).isNotNull();
        assertThat(reloadedUser.getFirstName()).isEqualTo("John");
        assertThat(reloadedUser.getLastName()).isEqualTo("Doe");

        // Verify legal acceptances were persisted
        List<UserLegalAcceptance> acceptances = userLegalAcceptanceRepository.findByUserId(user.getId());
        assertThat(acceptances).hasSize(3);
        assertThat(acceptances).extracting(UserLegalAcceptance::getPolicyId)
                .containsExactlyInAnyOrder(
                        privacyPolicy.getId(),
                        termsPolicy.getId(),
                        antiBriberyPolicy.getId());

        // Verify onboarding token is marked completed
        OnboardingToken token = onboardingTokenRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(token.isCompleted()).isTrue();
        assertThat(token.getCompletedAt()).isNotNull();
    }

    @Test
    void testExpiredTokenRejected() {
        User user = createPendingUser();
        String rawToken = onboardingService.generateOnboardingToken(user.getId(), testClient.getId());

        // Directly expire the token in the DB
        OnboardingToken token = onboardingTokenRepository.findByUserId(user.getId()).orElseThrow();
        token.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        onboardingTokenRepository.save(token);

        // Attempting to validate should throw
        assertThatThrownBy(() -> onboardingService.validateToken(rawToken))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void testCannotReuseCompletedToken() {
        // Complete the full onboarding flow
        User user = createPendingUser();
        String rawToken = completeFullOnboarding(user);

        // Trying to validate the completed token should throw
        assertThatThrownBy(() -> onboardingService.validateToken(rawToken))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already been completed");
    }

    @Test
    void testDuplicateTokenReplacedOnRegeneration() {
        User user = createPendingUser();

        // Generate first token
        String firstToken = onboardingService.generateOnboardingToken(user.getId(), testClient.getId());
        OnboardingToken firstTokenEntity = onboardingTokenRepository.findByUserId(user.getId()).orElseThrow();
        UUID firstTokenId = firstTokenEntity.getId();

        // Generate second token for the same user
        String secondToken = onboardingService.generateOnboardingToken(user.getId(), testClient.getId());
        assertThat(secondToken).isNotEqualTo(firstToken);

        // Old token should be deleted
        assertThat(onboardingTokenRepository.findById(firstTokenId)).isEmpty();

        // New token should work
        OnboardingStatusResponse status = onboardingService.validateToken(secondToken);
        assertThat(status.currentStep()).isEqualTo(0);
        assertThat(status.userId()).isEqualTo(user.getId());

        // Old token should not work
        assertThatThrownBy(() -> onboardingService.validateToken(firstToken))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Invalid onboarding token");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User createPendingUser() {
        return userRepository.save(User.builder()
                .email("onboarding-" + UUID.randomUUID() + "@test.com")
                .firstName("Pending")
                .lastName("User")
                .passwordHash("$2a$10$placeholder")
                .status(UserStatus.PENDING_VERIFICATION)
                .clientId(testClient.getId())
                .partnerCompanyId(testPartnerCompany.getId())
                .build());
    }

    private String completeFullOnboarding(User user) {
        String rawToken = onboardingService.generateOnboardingToken(user.getId(), testClient.getId());

        onboardingService.setPassword(
                new SetPasswordRequest(rawToken, "SecurePassword123!"), "127.0.0.1");

        onboardingService.completeProfile(
                new CompleteProfileRequest(rawToken, "John", "Doe", "+1234567890", "US"),
                "127.0.0.1");

        List<UUID> policyIds = List.of(
                privacyPolicy.getId(),
                termsPolicy.getId(),
                antiBriberyPolicy.getId());
        onboardingService.acceptPolicies(
                new AcceptPoliciesRequest(rawToken, policyIds),
                "127.0.0.1", "TestBrowser/1.0");

        Map<String, Boolean> consents = Map.of(
                "AI_RECOMMENDATIONS", true,
                "MARKETING_EMAIL", false,
                "ANALYTICS", true);
        onboardingService.setConsent(
                new SetConsentRequest(rawToken, consents), "127.0.0.1");

        onboardingService.completeOnboarding(new CompleteOnboardingRequest(rawToken));

        return rawToken;
    }
}
