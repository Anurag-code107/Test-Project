package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.AcceptPoliciesRequest;
import com.tenxengage.app.dto.request.CompleteOnboardingRequest;
import com.tenxengage.app.dto.request.CompleteProfileRequest;
import com.tenxengage.app.dto.request.SetPasswordRequest;
import com.tenxengage.app.dto.response.OnboardingStatusResponse;
import com.tenxengage.app.entity.LegalPolicy;
import com.tenxengage.app.entity.OnboardingToken;
import com.tenxengage.app.entity.RegionalComplianceConfig;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.PolicyType;
import com.tenxengage.app.entity.enums.UserStatus;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.ConsentRecordRepository;
import com.tenxengage.app.repository.LegalPolicyRepository;
import com.tenxengage.app.repository.OnboardingTokenRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.RegionalComplianceConfigRepository;
import com.tenxengage.app.repository.UserLegalAcceptanceRepository;
import com.tenxengage.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock
    private OnboardingTokenRepository onboardingTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LegalPolicyRepository legalPolicyRepository;

    @Mock
    private UserLegalAcceptanceRepository userLegalAcceptanceRepository;

    @Mock
    private ConsentRecordRepository consentRecordRepository;

    @Mock
    private RegionalComplianceConfigRepository regionalComplianceConfigRepository;

    @Mock
    private PartnerCompanyRepository partnerCompanyRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private OnboardingService onboardingService;

    private User testUser;
    private OnboardingToken testToken;
    private RegionalComplianceConfig testRegionalConfig;
    private UUID userId;
    private UUID clientId;
    private String rawToken;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        clientId = UUID.randomUUID();
        rawToken = UUID.randomUUID().toString();

        testUser = User.builder()
                .email("new.user@example.com")
                .firstName("New")
                .lastName("User")
                .passwordHash("$2a$12$placeholder")
                .status(UserStatus.PENDING_VERIFICATION)
                .clientId(clientId)
                .countryCode("US")
                .build();
        testUser.setId(userId);
        testUser.setCreatedAt(Instant.now());
        testUser.setUpdatedAt(Instant.now());

        testToken = OnboardingToken.builder()
                .userId(userId)
                .clientId(clientId)
                .tokenHash(sha256(rawToken))
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .currentStep(0)
                .build();
        testToken.setId(UUID.randomUUID());

        testRegionalConfig = RegionalComplianceConfig.builder()
                .regionCode("US")
                .regionName("United States")
                .privacyNoticeRequired(true)
                .termsOfServiceRequired(true)
                .antiBriberyRequired(true)
                .consentAiVisible(false)
                .consentMarketingVisible(false)
                .consentAnalyticsVisible(false)
                .cookieNoticeVisible(false)
                .build();
        testRegionalConfig.setId(UUID.randomUUID());
        testRegionalConfig.setCreatedAt(Instant.now());
        testRegionalConfig.setUpdatedAt(Instant.now());
    }

    // -------------------------------------------------------------------------
    // Token generation
    // -------------------------------------------------------------------------

    @Test
    void generateOnboardingToken_createsTokenWithExpiry() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(onboardingTokenRepository.save(any(OnboardingToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        String token = onboardingService.generateOnboardingToken(userId, clientId);

        assertThat(token).isNotNull().isNotBlank();

        verify(onboardingTokenRepository).deleteByUserId(userId);

        ArgumentCaptor<OnboardingToken> captor = ArgumentCaptor.forClass(OnboardingToken.class);
        verify(onboardingTokenRepository).save(captor.capture());
        OnboardingToken saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getClientId()).isEqualTo(clientId);
        assertThat(saved.getExpiresAt())
                .isCloseTo(Instant.now().plus(7, ChronoUnit.DAYS), within(5, ChronoUnit.SECONDS));
        assertThat(saved.getCurrentStep()).isZero();
    }

    // -------------------------------------------------------------------------
    // Token validation
    // -------------------------------------------------------------------------

    @Test
    void validateToken_returnsStatusForValidToken() {
        when(onboardingTokenRepository.findByTokenHash(sha256(rawToken)))
                .thenReturn(Optional.of(testToken));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(regionalComplianceConfigRepository.findByRegionCode("US"))
                .thenReturn(Optional.of(testRegionalConfig));

        OnboardingStatusResponse response = onboardingService.validateToken(rawToken);

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo("new.user@example.com");
        assertThat(response.currentStep()).isZero();
        assertThat(response.completed()).isFalse();
    }

    @Test
    void validateToken_throwsForExpiredToken() {
        OnboardingToken expiredToken = OnboardingToken.builder()
                .userId(userId)
                .clientId(clientId)
                .tokenHash(sha256(rawToken))
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .currentStep(0)
                .build();

        when(onboardingTokenRepository.findByTokenHash(sha256(rawToken)))
                .thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> onboardingService.validateToken(rawToken))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void validateToken_throwsForCompletedToken() {
        OnboardingToken completedToken = OnboardingToken.builder()
                .userId(userId)
                .clientId(clientId)
                .tokenHash(sha256(rawToken))
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .completedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .currentStep(5)
                .build();

        when(onboardingTokenRepository.findByTokenHash(sha256(rawToken)))
                .thenReturn(Optional.of(completedToken));

        assertThatThrownBy(() -> onboardingService.validateToken(rawToken))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already been completed");
    }

    @Test
    void validateToken_throwsForInvalidHash() {
        when(onboardingTokenRepository.findByTokenHash(any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> onboardingService.validateToken("invalid-token-string"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Invalid onboarding token");
    }

    // -------------------------------------------------------------------------
    // Step 1: Set Password
    // -------------------------------------------------------------------------

    @Test
    void setPassword_encodesPasswordAndAdvancesStep() {
        when(onboardingTokenRepository.findByTokenHash(sha256(rawToken)))
                .thenReturn(Optional.of(testToken));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("SecurePass123!")).thenReturn("$2a$12$encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(onboardingTokenRepository.save(any(OnboardingToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(regionalComplianceConfigRepository.findByRegionCode("US"))
                .thenReturn(Optional.of(testRegionalConfig));

        SetPasswordRequest request = new SetPasswordRequest(rawToken, "SecurePass123!");
        onboardingService.setPassword(request, "127.0.0.1");

        verify(passwordEncoder).encode("SecurePass123!");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("$2a$12$encoded");

        assertThat(testToken.getCurrentStep()).isEqualTo(1);
    }

    @Test
    void setPassword_throwsForWrongStep() {
        testToken.setCurrentStep(2);
        when(onboardingTokenRepository.findByTokenHash(sha256(rawToken)))
                .thenReturn(Optional.of(testToken));

        SetPasswordRequest request = new SetPasswordRequest(rawToken, "SecurePass123!");

        assertThatThrownBy(() -> onboardingService.setPassword(request, "127.0.0.1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot set password");
    }

    // -------------------------------------------------------------------------
    // Step 2: Complete Profile
    // -------------------------------------------------------------------------

    @Test
    void completeProfile_updatesUserAndAdvancesStep() {
        testToken.setCurrentStep(1);
        when(onboardingTokenRepository.findByTokenHash(sha256(rawToken)))
                .thenReturn(Optional.of(testToken));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(onboardingTokenRepository.save(any(OnboardingToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(regionalComplianceConfigRepository.findByRegionCode("US"))
                .thenReturn(Optional.of(testRegionalConfig));

        CompleteProfileRequest request =
                new CompleteProfileRequest(rawToken, "Jane", "Smith", "+1555123456", "US");
        OnboardingStatusResponse response =
                onboardingService.completeProfile(request, "127.0.0.1");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getFirstName()).isEqualTo("Jane");
        assertThat(saved.getLastName()).isEqualTo("Smith");
        assertThat(saved.getPhone()).isEqualTo("+1555123456");
        assertThat(saved.getCountryCode()).isEqualTo("US");

        assertThat(testToken.getCurrentStep()).isEqualTo(2);
        assertThat(response.currentStep()).isEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // Step 3: Accept Policies
    // -------------------------------------------------------------------------

    @Test
    void acceptPolicies_createsAcceptancesAndAdvances() {
        testToken.setCurrentStep(2);

        UUID policyId1 = UUID.randomUUID();
        UUID policyId2 = UUID.randomUUID();

        LegalPolicy policy1 = LegalPolicy.builder()
                .clientId(clientId)
                .policyType(PolicyType.PRIVACY_NOTICE)
                .version("1.0")
                .title("Privacy Notice")
                .build();
        policy1.setId(policyId1);
        policy1.setCreatedAt(Instant.now());
        policy1.setUpdatedAt(Instant.now());

        LegalPolicy policy2 = LegalPolicy.builder()
                .clientId(clientId)
                .policyType(PolicyType.TERMS_OF_SERVICE)
                .version("1.0")
                .title("Terms of Service")
                .build();
        policy2.setId(policyId2);
        policy2.setCreatedAt(Instant.now());
        policy2.setUpdatedAt(Instant.now());

        when(onboardingTokenRepository.findByTokenHash(sha256(rawToken)))
                .thenReturn(Optional.of(testToken));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(legalPolicyRepository.findByClientIdAndActiveTrue(clientId))
                .thenReturn(List.of(policy1, policy2));
        when(userLegalAcceptanceRepository.existsByUserIdAndPolicyId(userId, policyId1))
                .thenReturn(false).thenReturn(true);
        when(userLegalAcceptanceRepository.existsByUserIdAndPolicyId(userId, policyId2))
                .thenReturn(false).thenReturn(true);
        when(onboardingTokenRepository.save(any(OnboardingToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(regionalComplianceConfigRepository.findByRegionCode("US"))
                .thenReturn(Optional.of(testRegionalConfig));

        AcceptPoliciesRequest request = new AcceptPoliciesRequest(rawToken, List.of(policyId1, policyId2));
        OnboardingStatusResponse response =
                onboardingService.acceptPolicies(request, "127.0.0.1", "Mozilla/5.0");

        verify(userLegalAcceptanceRepository, times(2)).save(any());
        assertThat(testToken.getCurrentStep()).isEqualTo(3);
        assertThat(response.currentStep()).isEqualTo(3);
    }

    // -------------------------------------------------------------------------
    // Step 5: Complete Onboarding
    // -------------------------------------------------------------------------

    @Test
    void completeOnboarding_setsUserActiveAndComplete() {
        testToken.setCurrentStep(3);
        testUser.setStatus(UserStatus.PENDING_VERIFICATION);

        when(onboardingTokenRepository.findByTokenHash(sha256(rawToken)))
                .thenReturn(Optional.of(testToken));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(onboardingTokenRepository.save(any(OnboardingToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(regionalComplianceConfigRepository.findByRegionCode("US"))
                .thenReturn(Optional.of(testRegionalConfig));

        CompleteOnboardingRequest request = new CompleteOnboardingRequest(rawToken);
        OnboardingStatusResponse response = onboardingService.completeOnboarding(request);

        assertThat(testUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(testUser.getOnboardingCompletedAt()).isNotNull();
        assertThat(testToken.getCompletedAt()).isNotNull();
        assertThat(testToken.getCurrentStep()).isEqualTo(5);
        assertThat(response.completed()).isTrue();
    }

    // -------------------------------------------------------------------------
    // SHA-256 helper (mirrors production implementation)
    // -------------------------------------------------------------------------

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
