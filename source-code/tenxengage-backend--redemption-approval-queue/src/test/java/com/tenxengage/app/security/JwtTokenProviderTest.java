package com.tenxengage.app.security;

import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.UserStatus;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String SECRET =
            "test-secret-key-for-unit-tests-must-be-at-least-256-bits-long-for-hmac-sha";
    private static final long ACCESS_EXPIRATION_MS = 3600000L;
    private static final long REFRESH_EXPIRATION_MS = 604800000L;

    private JwtTokenProvider jwtTokenProvider;
    private UUID userId;
    private UUID clientId;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET, ACCESS_EXPIRATION_MS, REFRESH_EXPIRATION_MS);

        userId = UUID.randomUUID();
        clientId = UUID.randomUUID();

        User user = User.builder()
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .passwordHash("$2a$10$hash")
                .status(UserStatus.ACTIVE)
                .clientId(clientId)
                .partnerCompanyId(UUID.randomUUID())
                .build();
        user.setId(userId);

        userDetails = new CustomUserDetails(user);
    }

    // -------------------------------------------------------------------------
    // Token Generation
    // -------------------------------------------------------------------------

    @Test
    void generateToken_returnsNonBlankAccessToken() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());

        String token = jwtTokenProvider.generateToken(auth);

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3); // JWT has 3 parts
    }

    @Test
    void generateRefreshToken_returnsNonBlankRefreshToken() {
        String token = jwtTokenProvider.generateRefreshToken(userId.toString());

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    // -------------------------------------------------------------------------
    // Token Type Discrimination
    // -------------------------------------------------------------------------

    @Test
    void isAccessToken_trueForAccessToken() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        String token = jwtTokenProvider.generateToken(auth);

        assertThat(jwtTokenProvider.isAccessToken(token)).isTrue();
    }

    @Test
    void isAccessToken_falseForRefreshToken() {
        String token = jwtTokenProvider.generateRefreshToken(userId.toString());

        assertThat(jwtTokenProvider.isAccessToken(token)).isFalse();
    }

    @Test
    void isRefreshToken_trueForRefreshToken() {
        String token = jwtTokenProvider.generateRefreshToken(userId.toString());

        assertThat(jwtTokenProvider.isRefreshToken(token)).isTrue();
    }

    @Test
    void isRefreshToken_falseForAccessToken() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        String token = jwtTokenProvider.generateToken(auth);

        assertThat(jwtTokenProvider.isRefreshToken(token)).isFalse();
    }

    // -------------------------------------------------------------------------
    // Token Validation
    // -------------------------------------------------------------------------

    @Test
    void validateToken_trueForValidToken() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        String token = jwtTokenProvider.generateToken(auth);

        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_falseForExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .subject(userId.toString())
                .claim("type", "access")
                .issuedAt(new Date(System.currentTimeMillis() - 7200000))
                .expiration(new Date(System.currentTimeMillis() - 3600000))
                .signWith(key)
                .compact();

        assertThat(jwtTokenProvider.validateToken(expiredToken)).isFalse();
    }

    @Test
    void validateToken_falseForTamperedSignature() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        String token = jwtTokenProvider.generateToken(auth);

        // Replace a character in the middle of the signature portion for reliable tampering
        String[] parts = token.split("\\.");
        String sig = parts[2];
        char[] chars = sig.toCharArray();
        chars[sig.length() / 2] = (chars[sig.length() / 2] == 'X') ? 'Y' : 'X';
        String tampered = parts[0] + "." + parts[1] + "." + new String(chars);

        assertThat(jwtTokenProvider.validateToken(tampered)).isFalse();
    }

    @Test
    void validateToken_falseForWrongSigningKey() {
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "wrong-secret-key-that-is-different-from-the-real-one-256-bits-minimum".getBytes(StandardCharsets.UTF_8));
        String wrongToken = Jwts.builder()
                .subject(userId.toString())
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(wrongKey)
                .compact();

        assertThat(jwtTokenProvider.validateToken(wrongToken)).isFalse();
    }

    @Test
    void validateToken_falseForMalformedToken() {
        assertThat(jwtTokenProvider.validateToken("not.a.jwt.token")).isFalse();
    }

    @Test
    void validateToken_falseForEmptyToken() {
        assertThat(jwtTokenProvider.validateToken("")).isFalse();
    }

    @Test
    void validateToken_falseForNoneAlgorithmAttack() {
        // Craft a token with {"alg":"none"} header -- classic CVE-2015-9235
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"sub\":\"" + userId + "\",\"type\":\"access\"}").getBytes(StandardCharsets.UTF_8));
        String noneToken = header + "." + payload + ".";

        assertThat(jwtTokenProvider.validateToken(noneToken)).isFalse();
    }

    // -------------------------------------------------------------------------
    // Claim Extraction
    // -------------------------------------------------------------------------

    @Test
    void getUserIdFromToken_returnsCorrectUserId() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        String token = jwtTokenProvider.generateToken(auth);

        String extractedUserId = jwtTokenProvider.getUserIdFromToken(token);

        assertThat(extractedUserId).isEqualTo(userId.toString());
    }

    @Test
    void getUserIdFromToken_worksForRefreshToken() {
        String token = jwtTokenProvider.generateRefreshToken(userId.toString());

        String extractedUserId = jwtTokenProvider.getUserIdFromToken(token);

        assertThat(extractedUserId).isEqualTo(userId.toString());
    }

    // -------------------------------------------------------------------------
    // Token with Modified Claims (Security)
    // -------------------------------------------------------------------------

    @Test
    void validateToken_falseWhenClaimsModifiedWithoutResigning() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        String token = jwtTokenProvider.generateToken(auth);

        // Decode, modify the payload, re-encode without re-signing
        String[] parts = token.split("\\.");
        String decodedPayload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String modifiedPayload = decodedPayload.replace(userId.toString(), UUID.randomUUID().toString());
        String reEncodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(modifiedPayload.getBytes(StandardCharsets.UTF_8));
        String modifiedToken = parts[0] + "." + reEncodedPayload + "." + parts[2];

        assertThat(jwtTokenProvider.validateToken(modifiedToken)).isFalse();
    }

    // -------------------------------------------------------------------------
    // Expiration Configuration
    // -------------------------------------------------------------------------

    @Test
    void getAccessTokenExpirationMs_returnsConfiguredValue() {
        assertThat(jwtTokenProvider.getAccessTokenExpirationMs()).isEqualTo(ACCESS_EXPIRATION_MS);
    }

    @Test
    void getRefreshTokenExpirationMs_returnsConfiguredValue() {
        assertThat(jwtTokenProvider.getRefreshTokenExpirationMs()).isEqualTo(REFRESH_EXPIRATION_MS);
    }
}
