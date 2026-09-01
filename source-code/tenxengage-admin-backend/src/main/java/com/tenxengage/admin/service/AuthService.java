package com.tenxengage.admin.service;

import com.tenxengage.admin.dto.request.LoginRequest;
import com.tenxengage.admin.dto.response.LoginResponse;
import com.tenxengage.admin.dto.response.UserResponse;
import com.tenxengage.admin.entity.User;
import com.tenxengage.admin.exception.AuthenticationFailedException;
import com.tenxengage.admin.repository.UserRepository;
import com.tenxengage.admin.security.CustomUserDetails;
import com.tenxengage.admin.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtTokenProvider jwtTokenProvider,
                       UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }

    /** Result containing both the response body and the raw tokens for cookie setting. */
    public record AuthResult(LoginResponse response, String accessToken, String refreshToken) {}

    public AuthResult login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        User user = userRepository.findById(userDetails.getUserId())
            .orElseThrow(() -> new AuthenticationFailedException("User not found"));

        requireTenxAdmin(user);

        String accessToken = jwtTokenProvider.generateToken(authentication);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails.getUserId().toString());

        LoginResponse response = new LoginResponse(
            jwtTokenProvider.getAccessTokenExpirationMs(),
            UserResponse.from(user),
            List.of()
        );

        log.info("TENX_ADMIN user '{}' logged in", user.getEmail());
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

        requireTenxAdmin(user);

        CustomUserDetails userDetails = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        String newAccessToken = jwtTokenProvider.generateToken(authentication);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId);

        LoginResponse response = new LoginResponse(
            jwtTokenProvider.getAccessTokenExpirationMs(),
            UserResponse.from(user),
            List.of()
        );

        return new AuthResult(response, newAccessToken, newRefreshToken);
    }

    public UserResponse getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        User user = userRepository.findById(userDetails.getUserId())
            .orElseThrow(() -> new AuthenticationFailedException("User not found"));

        return UserResponse.from(user);
    }

    private void requireTenxAdmin(User user) {
        if (user.getClientId() != null) {
            throw new AuthenticationFailedException("Access restricted to platform administrators");
        }
    }
}
