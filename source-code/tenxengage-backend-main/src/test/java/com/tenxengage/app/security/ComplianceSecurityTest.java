package com.tenxengage.app.security;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import org.junit.jupiter.api.Tag;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.OnboardingToken;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.RegionalComplianceConfig;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.ClientStatus;
import com.tenxengage.app.entity.enums.SubscriptionTier;
import com.tenxengage.app.entity.enums.UserStatus;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.OnboardingTokenRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.RegionalComplianceConfigRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.service.OnboardingService;
import com.tenxengage.app.service.UserAnonymizationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security boundary tests for compliance features. Validates that authentication,
 * authorization, and token-based onboarding security constraints are enforced.
 * Uses both MockMvc (HTTP-level) and direct service calls (service-level) depending
 * on what is most appropriate for each scenario.
 */
@AutoConfigureMockMvc
@Tag("integration")
class ComplianceSecurityTest extends AbstractLocalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OnboardingService onboardingService;

    @Autowired
    private UserAnonymizationService userAnonymizationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private PartnerCompanyRepository partnerCompanyRepository;

    @Autowired
    private OnboardingTokenRepository onboardingTokenRepository;

    @Autowired
    private RegionalComplianceConfigRepository regionalComplianceConfigRepository;

    private Client testClient;
    private Client otherClient;
    private PartnerCompany testPartnerCompany;
    private PartnerCompany otherPartnerCompany;

    @BeforeEach
    void setUp() {
        testClient = clientRepository.save(Client.builder()
                .name("Security Test Client")
                .subdomain("sec-test-" + UUID.randomUUID().toString().substring(0, 8))
                .status(ClientStatus.ACTIVE)
                .subscriptionTier(SubscriptionTier.ENTERPRISE)
                .build());

        otherClient = clientRepository.save(Client.builder()
                .name("Other Client")
                .subdomain("other-" + UUID.randomUUID().toString().substring(0, 8))
                .status(ClientStatus.ACTIVE)
                .subscriptionTier(SubscriptionTier.ENTERPRISE)
                .build());

        String partnerSuffix1 = UUID.randomUUID().toString().substring(0, 8);
        testPartnerCompany = partnerCompanyRepository.save(PartnerCompany.builder()
                .name("Test Partner " + partnerSuffix1)
                .clientId(testClient.getId())
                .externalPartnerId("CT-SEC-" + partnerSuffix1)
                .build());

        String partnerSuffix2 = UUID.randomUUID().toString().substring(0, 8);
        otherPartnerCompany = partnerCompanyRepository.save(PartnerCompany.builder()
                .name("Other Partner " + partnerSuffix2)
                .clientId(otherClient.getId())
                .externalPartnerId("CT-SEC-" + partnerSuffix2)
                .build());

        // Ensure US regional config exists for onboarding flows
        if (regionalComplianceConfigRepository.findByRegionCode("US").isEmpty()) {
            regionalComplianceConfigRepository.save(RegionalComplianceConfig.builder()
                    .regionCode("US")
                    .regionName("United States")
                    .privacyNoticeRequired(true)
                    .termsOfServiceRequired(true)
                    .antiBriberyRequired(false)
                    .consentAiVisible(true)
                    .consentMarketingVisible(true)
                    .consentAnalyticsVisible(true)
                    .cookieNoticeVisible(false)
                    .build());
        }

        TenantContext.setClientId(testClient.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        // Clean up test data to avoid polluting the database
        if (otherClient != null) {
            clientRepository.delete(otherClient);
        }
        if (testClient != null) {
            clientRepository.delete(testClient);
        }
    }

    // -------------------------------------------------------------------------
    // Onboarding Token Security
    // -------------------------------------------------------------------------

    @Test
    void skipOnboardingStep_rejected() {
        // Create a user and generate an onboarding token (starts at step 0)
        User user = createPendingUser(testClient, testPartnerCompany);
        String rawToken = onboardingService.generateOnboardingToken(user.getId(), testClient.getId());

        // Attempting to accept policies (step 2) while at step 0 should be rejected
        // because set-password (step 0) must be completed first to advance
        assertThatThrownBy(() ->
                onboardingService.acceptPolicies(
                        new com.tenxengage.app.dto.request.AcceptPoliciesRequest(rawToken, java.util.List.of()),
                        "127.0.0.1", "TestBrowser/1.0"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("step");
    }

    @Test
    void fabricatedToken_rejected() {
        // A completely fabricated token should be rejected as invalid
        String fabricatedToken = UUID.randomUUID().toString();

        assertThatThrownBy(() -> onboardingService.validateToken(fabricatedToken))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Invalid onboarding token");
    }

    @Test
    void expiredToken_rejected() {
        // Create a token, then expire it manually
        User user = createPendingUser(testClient, testPartnerCompany);
        String rawToken = onboardingService.generateOnboardingToken(user.getId(), testClient.getId());

        OnboardingToken token = onboardingTokenRepository.findByUserId(user.getId()).orElseThrow();
        token.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        onboardingTokenRepository.save(token);

        // Validate should throw because the token is expired
        assertThatThrownBy(() -> onboardingService.validateToken(rawToken))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("expired");
    }

    // -------------------------------------------------------------------------
    // Role Escalation (HTTP-level)
    // -------------------------------------------------------------------------

    @Test
    void partnerSeller_cannotAnonymizeUser() throws Exception {
        User user = createActiveUser(testClient, testPartnerCompany);

        // POST /api/v1/users/{id}/anonymize without authentication should return 401
        mockMvc.perform(post("/api/v1/users/{id}/anonymize", user.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void partnerSeller_cannotAccessBreachIncidents() throws Exception {
        // GET /api/v1/compliance/breach-incidents without authentication should return 401
        mockMvc.perform(get("/api/v1/compliance/breach-incidents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticated_cannotAccessProtectedEndpoints() throws Exception {
        // GET /api/v1/me/profile (self-service profile) without auth should return 401
        mockMvc.perform(get("/api/v1/me/data-export"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // Cross-Tenant (service-level)
    // -------------------------------------------------------------------------

    @Test
    void anonymizeUser_rejectsWrongClient() {
        // Create a user belonging to testClient
        User user = createActiveUser(testClient, testPartnerCompany);

        // Attempt to anonymize using otherClient's ID should fail because
        // the user does not belong to otherClient (tenant isolation)
        assertThatThrownBy(() ->
                userAnonymizationService.anonymizeUser(user.getId(), otherClient.getId()))
                .isInstanceOf(com.tenxengage.app.exception.ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // Anonymized User
    // -------------------------------------------------------------------------

    @Test
    void anonymizedUser_cannotBeAnonymizedAgain() {
        User user = createActiveUser(testClient, testPartnerCompany);

        // First anonymization should succeed
        userAnonymizationService.anonymizeUser(user.getId(), testClient.getId());

        // Second anonymization should throw because user is already anonymized
        assertThatThrownBy(() ->
                userAnonymizationService.anonymizeUser(user.getId(), testClient.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already anonymized");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User createPendingUser(Client client, PartnerCompany partner) {
        return userRepository.save(User.builder()
                .email("sec-pending-" + UUID.randomUUID() + "@test.com")
                .firstName("Pending")
                .lastName("User")
                .passwordHash("$2a$10$placeholder")
                .status(UserStatus.PENDING_VERIFICATION)
                .clientId(client.getId())
                .partnerCompanyId(partner.getId())
                .build());
    }

    private User createActiveUser(Client client, PartnerCompany partner) {
        return userRepository.save(User.builder()
                .email("sec-active-" + UUID.randomUUID() + "@test.com")
                .firstName("Active")
                .lastName("User")
                .passwordHash("$2a$10$dummyBcryptHashForTesting123456789012345678901234")
                .status(UserStatus.ACTIVE)
                .clientId(client.getId())
                .partnerCompanyId(partner.getId())
                .countryCode("US")
                .build());
    }
}
