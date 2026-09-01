package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.LoginRequest;
import com.tenxengage.app.dto.response.LoginResponse;
import com.tenxengage.app.dto.response.UserResponse;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.UserStatus;
import com.tenxengage.app.exception.AuthenticationFailedException;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.CustomUserDetails;
import com.tenxengage.app.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FeatureFlagService featureFlagService;
    @Mock
    private PermissionService permissionService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private HomeDashboardTemplateService homeDashboardTemplateService;
    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @InjectMocks
    private AuthService authService;

    private UUID userId;
    private UUID clientId;
    private User activeUser;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        clientId = UUID.randomUUID();

        activeUser = User.builder()
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .passwordHash("$2a$10$hash")
                .status(UserStatus.ACTIVE)
                .clientId(clientId)
                .partnerCompanyId(UUID.randomUUID())
                .build();
        // Set the ID via reflection-like approach since BaseEntity generates it
        activeUser.setId(userId);

        userDetails = new CustomUserDetails(activeUser);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // -------------------------------------------------------------------------
    // login()
    // -------------------------------------------------------------------------

    @Test
    void login_returnsAuthResult_withValidCredentials() {
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        Authentication authentication = mockAuthentication();

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtTokenProvider.generateToken(authentication)).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(userId.toString())).thenReturn("refresh-token");
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));
        when(featureFlagService.getEnabledFeatures(clientId)).thenReturn(List.of("AI_CHAT"));
        when(permissionService.resolveEffectivePermissions(userId)).thenReturn(Set.of("action.claim.submit"));
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(3600000L);

        AuthService.AuthResult result = authService.login(request);

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.response().expiresIn()).isEqualTo(3600000L);
        assertThat(result.response().enabledFeatures()).containsExactly("AI_CHAT");
        assertThat(result.response().user()).isNotNull();
    }

    @Test
    void login_throwsAuthFailed_whenTenxAdminAttemptsMainAppLogin() {
        LoginRequest request = new LoginRequest("admin@tenx.com", "password123");
        Authentication authentication = mockAuthentication();

        // TENX_ADMIN: no clientId and no clientRoleId
        User tenxAdmin = User.builder()
                .email("admin@tenx.com")
                .firstName("Admin")
                .lastName("User")
                .passwordHash("$2a$10$hash")
                .status(UserStatus.ACTIVE)
                .build();
        tenxAdmin.setId(userId);

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtTokenProvider.generateToken(authentication)).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(userId.toString())).thenReturn("refresh-token");
        when(userRepository.findById(userId)).thenReturn(Optional.of(tenxAdmin));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void login_throwsAuthFailed_whenUserIsPendingVerification() {
        LoginRequest request = new LoginRequest("pending@example.com", "password123");
        Authentication authentication = mockAuthentication();

        activeUser.setStatus(UserStatus.PENDING_VERIFICATION);

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtTokenProvider.generateToken(authentication)).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(userId.toString())).thenReturn("refresh-token");
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("complete your account setup");
    }

    @Test
    void login_throwsAuthFailed_whenUserIsAnonymized() {
        LoginRequest request = new LoginRequest("anon@example.com", "password123");
        Authentication authentication = mockAuthentication();

        activeUser.setStatus(UserStatus.ANONYMIZED);

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtTokenProvider.generateToken(authentication)).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(userId.toString())).thenReturn("refresh-token");
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void login_throwsAuthFailed_whenUserIsRestricted() {
        LoginRequest request = new LoginRequest("restricted@example.com", "password123");
        Authentication authentication = mockAuthentication();

        activeUser.setStatus(UserStatus.RESTRICTED);

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtTokenProvider.generateToken(authentication)).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(userId.toString())).thenReturn("refresh-token");
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void login_throwsAuthFailed_whenUserNotFound() {
        LoginRequest request = new LoginRequest("missing@example.com", "password123");
        Authentication authentication = mockAuthentication();

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtTokenProvider.generateToken(authentication)).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(userId.toString())).thenReturn("refresh-token");
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("User not found");
    }

    @Test
    void login_auditsLoginEvent() {
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        Authentication authentication = mockAuthentication();

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtTokenProvider.generateToken(authentication)).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(userId.toString())).thenReturn("refresh-token");
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));
        when(featureFlagService.getEnabledFeatures(clientId)).thenReturn(List.of());
        when(permissionService.resolveEffectivePermissions(userId)).thenReturn(Set.of());
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(3600000L);

        authService.login(request);

        verify(auditLogService).logWithActor(
                any(), any(), eq(userId), anyString(), anyString(), eq(clientId),
                eq("test@example.com"), anyString(), any());
    }

    @Test
    void login_continuesWhenAuditFails() {
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        Authentication authentication = mockAuthentication();

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtTokenProvider.generateToken(authentication)).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(userId.toString())).thenReturn("refresh-token");
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));
        when(featureFlagService.getEnabledFeatures(clientId)).thenReturn(List.of());
        when(permissionService.resolveEffectivePermissions(userId)).thenReturn(Set.of());
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(3600000L);
        doThrow(new RuntimeException("Audit DB down")).when(auditLogService)
                .logWithActor(any(), any(), any(), any(), any(), any(), any(), any(), any());

        AuthService.AuthResult result = authService.login(request);

        assertThat(result).isNotNull();
        assertThat(result.accessToken()).isEqualTo("access-token");
    }

    @Test
    void login_includesEnabledFeaturesAndPermissions() {
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        Authentication authentication = mockAuthentication();

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtTokenProvider.generateToken(authentication)).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(userId.toString())).thenReturn("refresh-token");
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));
        when(featureFlagService.getEnabledFeatures(clientId))
                .thenReturn(List.of("AI_CHAT", "RECOMMENDATIONS", "FORECASTING"));
        when(permissionService.resolveEffectivePermissions(userId))
                .thenReturn(Set.of("action.claim.submit", "action.users.view"));
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(3600000L);

        AuthService.AuthResult result = authService.login(request);

        assertThat(result.response().enabledFeatures())
                .containsExactlyInAnyOrder("AI_CHAT", "RECOMMENDATIONS", "FORECASTING");
    }

    // -------------------------------------------------------------------------
    // refresh()
    // -------------------------------------------------------------------------

    @Test
    void refresh_returnsNewTokens_withValidRefreshToken() {
        when(jwtTokenProvider.validateToken("valid-refresh")).thenReturn(true);
        when(jwtTokenProvider.isRefreshToken("valid-refresh")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken("valid-refresh")).thenReturn(userId.toString());
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));
        when(jwtTokenProvider.generateToken(any(Authentication.class))).thenReturn("new-access");
        when(jwtTokenProvider.generateRefreshToken(userId.toString())).thenReturn("new-refresh");
        when(featureFlagService.getEnabledFeatures(clientId)).thenReturn(List.of());
        when(permissionService.resolveEffectivePermissions(userId)).thenReturn(Set.of());
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(3600000L);

        AuthService.AuthResult result = authService.refresh("valid-refresh");

        assertThat(result.accessToken()).isEqualTo("new-access");
        assertThat(result.refreshToken()).isEqualTo("new-refresh");
        assertThat(result.response()).isNotNull();
    }

    @Test
    void refresh_throwsAuthFailed_whenTokenInvalid() {
        when(jwtTokenProvider.validateToken("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh("bad-token"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Invalid or expired refresh token");
    }

    @Test
    void refresh_throwsAuthFailed_whenTokenIsNotRefreshType() {
        when(jwtTokenProvider.validateToken("access-token")).thenReturn(true);
        when(jwtTokenProvider.isRefreshToken("access-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh("access-token"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Invalid token type: expected refresh token");
    }

    @Test
    void refresh_throwsAuthFailed_whenTenxAdminAttempts() {
        // TENX_ADMIN: no clientId and no clientRoleId
        User tenxAdmin = User.builder()
                .email("admin@tenx.com")
                .firstName("Admin")
                .lastName("User")
                .passwordHash("$2a$10$hash")
                .status(UserStatus.ACTIVE)
                .build();
        tenxAdmin.setId(userId);

        when(jwtTokenProvider.validateToken("refresh")).thenReturn(true);
        when(jwtTokenProvider.isRefreshToken("refresh")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken("refresh")).thenReturn(userId.toString());
        when(userRepository.findById(userId)).thenReturn(Optional.of(tenxAdmin));

        assertThatThrownBy(() -> authService.refresh("refresh"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void refresh_throwsAuthFailed_whenUserNotFound() {
        when(jwtTokenProvider.validateToken("refresh")).thenReturn(true);
        when(jwtTokenProvider.isRefreshToken("refresh")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken("refresh")).thenReturn(userId.toString());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("refresh"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("User not found");
    }

    // -------------------------------------------------------------------------
    // getCurrentUser()
    // -------------------------------------------------------------------------

    @Test
    void getCurrentUser_returnsUserFromSecurityContext() {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));
        when(permissionService.resolveEffectivePermissions(userId))
                .thenReturn(Set.of("action.claim.submit"));

        UserResponse response = authService.getCurrentUser();

        assertThat(response).isNotNull();
    }

    @Test
    void getCurrentUser_throwsWhenUserNotFound() {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getCurrentUser())
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("User not found");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Authentication mockAuthentication() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        return authentication;
    }
}
