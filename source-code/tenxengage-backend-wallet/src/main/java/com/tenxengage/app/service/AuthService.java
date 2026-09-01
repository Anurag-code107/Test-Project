package com.tenxengage.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.dto.request.LoginRequest;
import com.tenxengage.app.dto.response.HomeDashboardTemplateResponse;
import com.tenxengage.app.dto.response.LoginResponse;
import com.tenxengage.app.dto.response.UserResponse;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.AuditAction;
import com.tenxengage.app.entity.enums.AuditResourceType;
import com.tenxengage.app.exception.AuthenticationFailedException;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.CustomUserDetails;
import com.tenxengage.app.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final FeatureFlagService featureFlagService;
    private final PermissionService permissionService;
    private final AuditLogService auditLogService;
    private final HomeDashboardTemplateService homeDashboardTemplateService;
    private final ObjectMapper objectMapper;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtTokenProvider jwtTokenProvider,
                       UserRepository userRepository,
                       FeatureFlagService featureFlagService,
                       PermissionService permissionService,
                       AuditLogService auditLogService,
                       HomeDashboardTemplateService homeDashboardTemplateService,
                       ObjectMapper objectMapper) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
        this.featureFlagService = featureFlagService;
        this.permissionService = permissionService;
        this.auditLogService = auditLogService;
        this.homeDashboardTemplateService = homeDashboardTemplateService;
        this.objectMapper = objectMapper;
    }

    private HomeDashboardTemplateResponse resolveTemplate(User user) {
        if (user.getClientRole() == null) {
            return null;
        }
        return homeDashboardTemplateService.resolveForRole(user.getClientRole())
                .map(t -> HomeDashboardTemplateResponse.from(t, objectMapper))
                .orElse(null);
    }

    /** Result containing both the response body and the raw tokens for cookie setting. */
    public record AuthResult(LoginResponse response, String accessToken, String refreshToken) {}

    public AuthResult login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String accessToken = jwtTokenProvider.generateToken(authentication);
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails.getUserId().toString());

        User user = userRepository.findById(userDetails.getUserId())
            .orElseThrow(() -> new AuthenticationFailedException("User not found"));

        // Block TENX_ADMIN from logging into the main app — they must use the admin portal
        if (user.getClientId() == null && user.getClientRoleId() == null) {
            throw new AuthenticationFailedException("Invalid email or password");
        }

        // Block users who haven't completed onboarding
        if (user.getStatus() == com.tenxengage.app.entity.enums.UserStatus.PENDING_VERIFICATION) {
            throw new AuthenticationFailedException(
                    "Please complete your account setup using the link in your welcome email");
        }

        // Block anonymized and restricted users
        if (user.getStatus() == com.tenxengage.app.entity.enums.UserStatus.ANONYMIZED
                || user.getStatus() == com.tenxengage.app.entity.enums.UserStatus.RESTRICTED) {
            throw new AuthenticationFailedException("Invalid email or password");
        }

        List<String> enabledFeatures = featureFlagService.getEnabledFeatures(user.getClientId());
        Set<String> effectivePermissions = permissionService.resolveEffectivePermissions(user.getId());

        LoginResponse response = new LoginResponse(
            jwtTokenProvider.getAccessTokenExpirationMs(),
            UserResponse.from(user, effectivePermissions, resolveTemplate(user)),
            enabledFeatures
        );

        // Audit login event
        try {
            String actorName = user.getFirstName() + " " + user.getLastName();
            auditLogService.logWithActor(AuditAction.LOGGED_IN, AuditResourceType.AUTH,
                    user.getId(), actorName, "User logged in", user.getClientId(),
                    user.getEmail(), actorName, null);
        } catch (Exception e) {
            log.warn("Failed to write login audit log: {}", e.getMessage());
        }

        return new AuthResult(response, accessToken, refreshToken);
    }

    public AuthResult refresh(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new AuthenticationFailedException("Invalid or expired refresh token");
        }
        if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new AuthenticationFailedException("Invalid token type: expected refresh token");
        }

        String userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(UUID.fromString(userId))
            .orElseThrow(() -> new AuthenticationFailedException("User not found"));

        // Block TENX_ADMIN from refreshing tokens on the main app
        if (user.getClientId() == null && user.getClientRoleId() == null) {
            throw new AuthenticationFailedException("Invalid email or password");
        }

        CustomUserDetails userDetails = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        String newAccessToken = jwtTokenProvider.generateToken(authentication);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId);

        List<String> enabledFeatures = featureFlagService.getEnabledFeatures(user.getClientId());
        Set<String> effectivePermissions = permissionService.resolveEffectivePermissions(user.getId());

        LoginResponse response = new LoginResponse(
            jwtTokenProvider.getAccessTokenExpirationMs(),
            UserResponse.from(user, effectivePermissions, resolveTemplate(user)),
            enabledFeatures
        );

        return new AuthResult(response, newAccessToken, newRefreshToken);
    }

    public UserResponse getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        User user = userRepository.findById(userDetails.getUserId())
            .orElseThrow(() -> new AuthenticationFailedException("User not found"));

        Set<String> effectivePermissions = permissionService.resolveEffectivePermissions(user.getId());
        return UserResponse.from(user, effectivePermissions, resolveTemplate(user));
    }
}
