package com.tenxengage.app.integration;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import com.tenxengage.app.dto.request.LoginRequest;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.UserStatus;
import com.tenxengage.app.exception.AuthenticationFailedException;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.service.AuthService;
import com.tenxengage.app.testdata.ClientFixtures;
import com.tenxengage.app.testdata.PartnerFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
@Tag("integration")
class AuthFlowIntegrationTest extends AbstractLocalIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private ClientRepository clientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PartnerCompanyRepository partnerCompanyRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Client testClient;
    private PartnerCompany testPartner;
    private User activeUser;

    @BeforeEach
    void setUp() {
        testClient = clientRepository.save(ClientFixtures.activeEnterprise().build());
        testPartner = partnerCompanyRepository.save(
                PartnerFixtures.activeReseller(testClient.getId()).build());

        activeUser = userRepository.save(User.builder()
                .email("auth-test-" + java.util.UUID.randomUUID() + "@test.com")
                .firstName("Auth")
                .lastName("Test")
                .passwordHash(passwordEncoder.encode("TestPassword123!"))
                .status(UserStatus.ACTIVE)
                .clientId(testClient.getId())
                .partnerCompanyId(testPartner.getId())
                .countryCode("US")
                .build());
    }

    @Test
    void login_returnsTokensAndUserWithPermissions() {
        LoginRequest request = new LoginRequest(activeUser.getEmail(), "TestPassword123!");

        AuthService.AuthResult result = authService.login(request);

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.response().user()).isNotNull();
        assertThat(result.response().expiresIn()).isGreaterThan(0);
    }

    @Test
    void login_blocksAnonymizedUser() {
        activeUser.setStatus(UserStatus.ANONYMIZED);
        userRepository.save(activeUser);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest(activeUser.getEmail(), "TestPassword123!")))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void login_blocksPendingVerificationUser() {
        activeUser.setStatus(UserStatus.PENDING_VERIFICATION);
        userRepository.save(activeUser);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest(activeUser.getEmail(), "TestPassword123!")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("complete your account setup");
    }

    @Test
    void refresh_rotatesTokens() {
        LoginRequest request = new LoginRequest(activeUser.getEmail(), "TestPassword123!");
        AuthService.AuthResult loginResult = authService.login(request);

        AuthService.AuthResult refreshResult = authService.refresh(loginResult.refreshToken());

        assertThat(refreshResult.accessToken()).isNotBlank();
        assertThat(refreshResult.refreshToken()).isNotBlank();
        assertThat(refreshResult.accessToken()).isNotEqualTo(loginResult.accessToken());
    }

    @Test
    void login_blocksRestrictedUser() {
        activeUser.setStatus(UserStatus.RESTRICTED);
        userRepository.save(activeUser);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest(activeUser.getEmail(), "TestPassword123!")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Invalid email or password");
    }
}
