package com.tenxengage.app.service;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalTokenServiceTest {

    private static final String SECRET = "test-approval-secret-key-for-local-integration-tests-256-bits-long";
    private static final long EXPIRATION_MS = 172800000L; // 48 hours
    private static final String BASE_URL = "http://localhost:3000";

    private ApprovalTokenService approvalTokenService;

    @BeforeEach
    void setUp() {
        approvalTokenService = new ApprovalTokenService(SECRET, EXPIRATION_MS, BASE_URL);
    }

    @Test
    void generateApprovalToken_returnsTokenAndId() {
        UUID incentiveId = UUID.randomUUID();
        String approverEmail = "approver@test.com";

        ApprovalTokenService.ApprovalTokenResult result =
                approvalTokenService.generateApprovalToken(incentiveId, approverEmail, 1);

        assertThat(result.token()).isNotBlank();
        assertThat(result.tokenId()).isNotNull();
    }

    @Test
    void parseApprovalToken_extractsAllClaims() {
        UUID incentiveId = UUID.randomUUID();
        String approverEmail = "approver@test.com";
        int approvalRound = 2;

        ApprovalTokenService.ApprovalTokenResult generated =
                approvalTokenService.generateApprovalToken(incentiveId, approverEmail, approvalRound);

        ApprovalTokenService.ApprovalClaims claims =
                approvalTokenService.parseApprovalToken(generated.token());

        assertThat(claims.incentiveId()).isEqualTo(incentiveId);
        assertThat(claims.approverEmail()).isEqualTo(approverEmail);
        assertThat(claims.tokenId()).isEqualTo(generated.tokenId());
        assertThat(claims.approvalRound()).isEqualTo(approvalRound);
    }

    @Test
    void parseApprovalToken_throwsOnExpired() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .subject("approver@test.com")
                .claim("incentiveId", UUID.randomUUID().toString())
                .claim("tokenId", UUID.randomUUID().toString())
                .claim("approvalRound", 1)
                .claim("type", "approval")
                .issuedAt(new Date(System.currentTimeMillis() - 200000))
                .expiration(new Date(System.currentTimeMillis() - 100000))
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> approvalTokenService.parseApprovalToken(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void parseApprovalToken_throwsOnInvalidSignature() {
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "wrong-secret-key-that-is-long-enough-for-hmac-sha-256-bits-ok".getBytes(StandardCharsets.UTF_8));
        String wrongSignatureToken = Jwts.builder()
                .subject("approver@test.com")
                .claim("incentiveId", UUID.randomUUID().toString())
                .claim("tokenId", UUID.randomUUID().toString())
                .claim("approvalRound", 1)
                .claim("type", "approval")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(wrongKey)
                .compact();

        assertThatThrownBy(() -> approvalTokenService.parseApprovalToken(wrongSignatureToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void parseApprovalToken_throwsOnWrongTokenType() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String accessToken = Jwts.builder()
                .subject("user@test.com")
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> approvalTokenService.parseApprovalToken(accessToken))
                .isInstanceOf(JwtException.class)
                .hasMessage("Invalid token type");
    }

    @Test
    void parseApprovalToken_handlesNullApprovalRound() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String tokenWithoutRound = Jwts.builder()
                .subject("approver@test.com")
                .claim("incentiveId", UUID.randomUUID().toString())
                .claim("tokenId", UUID.randomUUID().toString())
                .claim("type", "approval")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();

        ApprovalTokenService.ApprovalClaims claims =
                approvalTokenService.parseApprovalToken(tokenWithoutRound);

        assertThat(claims.approvalRound()).isEqualTo(1);
    }

    @Test
    void buildApprovalUrl_includesTokenAndAction() {
        String url = approvalTokenService.buildApprovalUrl("my-token", "approve");

        assertThat(url).isEqualTo("http://localhost:3000/approvals/decide?token=my-token&action=approve");
    }

    @Test
    void buildReviewUrl_includesToken() {
        String url = approvalTokenService.buildReviewUrl("my-token");

        assertThat(url).isEqualTo("http://localhost:3000/approvals/decide?token=my-token");
    }
}
