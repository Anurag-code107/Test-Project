package com.tenxengage.app.controller;

import com.tenxengage.app.dto.request.AcceptPoliciesRequest;
import com.tenxengage.app.dto.request.CompleteOnboardingRequest;
import com.tenxengage.app.dto.request.CompleteProfileRequest;
import com.tenxengage.app.dto.request.SetConsentRequest;
import com.tenxengage.app.dto.request.SetPasswordRequest;
import com.tenxengage.app.dto.request.ValidateOnboardingTokenRequest;
import com.tenxengage.app.dto.response.ApiResponse;
import com.tenxengage.app.dto.response.ConsentPreferenceResponse;
import com.tenxengage.app.dto.response.LegalPolicyResponse;
import com.tenxengage.app.dto.response.OnboardingStatusResponse;
import com.tenxengage.app.entity.OnboardingToken;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.OnboardingTokenRepository;
import com.tenxengage.app.service.OnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(name = "Onboarding", description = "Public user onboarding endpoints (token-protected)")
@RequiredArgsConstructor
public class OnboardingController {

    private static final Logger log = LoggerFactory.getLogger(OnboardingController.class);

    private final OnboardingService onboardingService;
    private final OnboardingTokenRepository onboardingTokenRepository;

    // -------------------------------------------------------------------------
    // POST endpoints (token in request body)
    // -------------------------------------------------------------------------

    @PostMapping("/validate")
    @Operation(summary = "Validate onboarding token",
        description = "Validates the token and returns current onboarding status including user info and regional compliance config")
    public ResponseEntity<ApiResponse<OnboardingStatusResponse>> validateToken(
            @Valid @RequestBody ValidateOnboardingTokenRequest request) {
        log.debug("Validating onboarding token");
        OnboardingStatusResponse status = onboardingService.validateToken(request.token());
        return ResponseEntity.ok(ApiResponse.success(status, "Token validated successfully"));
    }

    @PostMapping("/set-password")
    @Operation(summary = "Set password (Step 1)",
        description = "Sets the user's password during onboarding. Requires step 0 (initial state).")
    public ResponseEntity<ApiResponse<OnboardingStatusResponse>> setPassword(
            @Valid @RequestBody SetPasswordRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = extractIpAddress(httpRequest);
        log.debug("Set password request from ip={}", ipAddress);
        OnboardingStatusResponse status = onboardingService.setPassword(request, ipAddress);
        return ResponseEntity.ok(ApiResponse.success(status, "Password set successfully"));
    }

    @PostMapping("/complete-profile")
    @Operation(summary = "Complete profile (Step 2)",
        description = "Updates user profile fields (name, phone, country). Requires step >= 1.")
    public ResponseEntity<ApiResponse<OnboardingStatusResponse>> completeProfile(
            @Valid @RequestBody CompleteProfileRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = extractIpAddress(httpRequest);
        log.debug("Complete profile request from ip={}", ipAddress);
        OnboardingStatusResponse status = onboardingService.completeProfile(request, ipAddress);
        return ResponseEntity.ok(ApiResponse.success(status, "Profile completed successfully"));
    }

    @PostMapping("/accept-policies")
    @Operation(summary = "Accept legal policies (Step 3)",
        description = "Records legal policy acceptances. Validates all required policies for the user's region are accepted. Requires step >= 2.")
    public ResponseEntity<ApiResponse<OnboardingStatusResponse>> acceptPolicies(
            @Valid @RequestBody AcceptPoliciesRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = extractIpAddress(httpRequest);
        String userAgent = extractUserAgent(httpRequest);
        log.debug("Accept policies request from ip={}", ipAddress);
        OnboardingStatusResponse status = onboardingService.acceptPolicies(
            request, ipAddress, userAgent);
        return ResponseEntity.ok(ApiResponse.success(status, "Policies accepted successfully"));
    }

    @PostMapping("/set-consent")
    @Operation(summary = "Set consent preferences (Step 4)",
        description = "Records user consent preferences (AI, marketing, analytics). Requires step >= 3.")
    public ResponseEntity<ApiResponse<OnboardingStatusResponse>> setConsent(
            @Valid @RequestBody SetConsentRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = extractIpAddress(httpRequest);
        log.debug("Set consent request from ip={}", ipAddress);
        OnboardingStatusResponse status = onboardingService.setConsent(request, ipAddress);
        return ResponseEntity.ok(ApiResponse.success(status, "Consent preferences saved successfully"));
    }

    @PostMapping("/complete")
    @Operation(summary = "Complete onboarding (Step 5)",
        description = "Finalizes onboarding: activates the user account and marks the token as completed. Requires step >= 3 (consent step is optional).")
    public ResponseEntity<ApiResponse<OnboardingStatusResponse>> completeOnboarding(
            @Valid @RequestBody CompleteOnboardingRequest request) {
        log.debug("Complete onboarding request");
        OnboardingStatusResponse status = onboardingService.completeOnboarding(request);
        return ResponseEntity.ok(ApiResponse.success(status, "Onboarding completed successfully"));
    }

    // -------------------------------------------------------------------------
    // GET endpoints (token as query parameter)
    // -------------------------------------------------------------------------

    @GetMapping("/policies")
    @Operation(summary = "Get active legal policies",
        description = "Returns all active legal policies for the token's client, with acceptance status per user")
    public ResponseEntity<ApiResponse<List<LegalPolicyResponse>>> getActivePolicies(
            @RequestParam String token) {
        OnboardingToken onboardingToken = resolveTokenForRead(token);
        List<LegalPolicyResponse> policies = onboardingService.getActivePolicies(
            onboardingToken.getClientId(), onboardingToken.getUserId());
        return ResponseEntity.ok(ApiResponse.success(policies));
    }

    @GetMapping("/consent")
    @Operation(summary = "Get current consent state",
        description = "Returns the latest consent preferences per consent type for the token's user")
    public ResponseEntity<ApiResponse<List<ConsentPreferenceResponse>>> getUserConsent(
            @RequestParam String token) {
        OnboardingToken onboardingToken = resolveTokenForRead(token);
        List<ConsentPreferenceResponse> consents = onboardingService.getUserConsent(
            onboardingToken.getUserId());
        return ResponseEntity.ok(ApiResponse.success(consents));
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Resolves a raw token for read-only GET endpoints.
     * Validates the token is not expired and not completed.
     */
    private OnboardingToken resolveTokenForRead(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessRuleException("Onboarding token is required");
        }

        String tokenHash = sha256(rawToken);
        OnboardingToken onboardingToken = onboardingTokenRepository.findByTokenHash(tokenHash)
            .orElseThrow(() -> new BusinessRuleException("Invalid onboarding token"));

        if (onboardingToken.isExpired()) {
            throw new BusinessRuleException("Onboarding token has expired");
        }

        if (onboardingToken.isCompleted()) {
            throw new BusinessRuleException("Onboarding has already been completed");
        }

        return onboardingToken;
    }

    /**
     * Extracts the client IP address from the request, respecting the X-Forwarded-For
     * header for proxied requests.
     */
    private static String extractIpAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For may contain multiple IPs; the first is the original client
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String extractUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && userAgent.length() > 500) {
            return userAgent.substring(0, 500);
        }
        return userAgent;
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
