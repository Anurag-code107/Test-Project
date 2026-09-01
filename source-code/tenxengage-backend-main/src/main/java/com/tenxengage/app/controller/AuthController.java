package com.tenxengage.app.controller;

import com.tenxengage.app.audit.Audited;
import com.tenxengage.app.dto.request.LoginRequest;
import com.tenxengage.app.dto.response.LoginResponse;
import com.tenxengage.app.dto.response.UserResponse;
import com.tenxengage.app.exception.AuthenticationFailedException;
import com.tenxengage.app.security.CookieUtil;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Authentication endpoints")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final CookieUtil cookieUtil;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(AuthService authService, CookieUtil cookieUtil, JwtTokenProvider jwtTokenProvider) {
        this.authService = authService;
        this.cookieUtil = cookieUtil;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate user and return JWT tokens via HTTPOnly cookies")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                                HttpServletResponse response) {
        AuthService.AuthResult result = authService.login(request);
        cookieUtil.addAccessTokenCookie(response, result.accessToken(), jwtTokenProvider.getAccessTokenExpirationMs());
        cookieUtil.addRefreshTokenCookie(response, result.refreshToken(), jwtTokenProvider.getRefreshTokenExpirationMs());
        return ResponseEntity.ok(result.response());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Get new access token using refresh token cookie")
    public ResponseEntity<LoginResponse> refresh(
            @CookieValue(name = CookieUtil.REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            log.debug("Refresh attempted without token — no active session");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        AuthService.AuthResult result = authService.refresh(refreshToken);
        cookieUtil.addAccessTokenCookie(response, result.accessToken(), jwtTokenProvider.getAccessTokenExpirationMs());
        cookieUtil.addRefreshTokenCookie(response, result.refreshToken(), jwtTokenProvider.getRefreshTokenExpirationMs());
        return ResponseEntity.ok(result.response());
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Logout", description = "Clear auth cookies")
    @Audited(action = "Logged_Out", resourceType = "AUTH", description = "User logged out")
    public ResponseEntity<Map<String, String>> logout(HttpServletResponse response) {
        cookieUtil.clearAuthCookies(response);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get current user", description = "Returns the currently authenticated user")
    public ResponseEntity<UserResponse> getCurrentUser() {
        UserResponse user = authService.getCurrentUser();
        return ResponseEntity.ok(user);
    }
}
