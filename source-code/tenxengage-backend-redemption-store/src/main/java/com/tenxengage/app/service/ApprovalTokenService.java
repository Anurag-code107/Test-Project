package com.tenxengage.app.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class ApprovalTokenService {

    private final SecretKey key;
    private final long tokenExpirationMs;
    private final String baseUrl;

    public ApprovalTokenService(
            @Value("${app.approval.token-secret}") String approvalSecret,
            @Value("${app.approval.token-expiration-ms}") long tokenExpirationMs,
            @Value("${app.approval.base-url}") String baseUrl) {
        this.key = Keys.hmacShaKeyFor(approvalSecret.getBytes(StandardCharsets.UTF_8));
        this.tokenExpirationMs = tokenExpirationMs;
        this.baseUrl = baseUrl;
    }

    public record ApprovalTokenResult(String token, UUID tokenId) {}

    public record ApprovalClaims(UUID incentiveId, String approverEmail, UUID tokenId, int approvalRound) {}

    public ApprovalTokenResult generateApprovalToken(UUID incentiveId, String approverEmail, int approvalRound) {
        UUID tokenId = UUID.randomUUID();
        Date now = new Date();
        Date expiry = new Date(now.getTime() + tokenExpirationMs);

        String token = Jwts.builder()
            .subject(approverEmail)
            .claim("incentiveId", incentiveId.toString())
            .claim("tokenId", tokenId.toString())
            .claim("approvalRound", approvalRound)
            .claim("type", "approval")
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact();

        return new ApprovalTokenResult(token, tokenId);
    }

    public ApprovalClaims parseApprovalToken(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();

        String type = claims.get("type", String.class);
        if (!"approval".equals(type)) {
            throw new JwtException("Invalid token type");
        }

        Integer round = claims.get("approvalRound", Integer.class);

        return new ApprovalClaims(
            UUID.fromString(claims.get("incentiveId", String.class)),
            claims.getSubject(),
            UUID.fromString(claims.get("tokenId", String.class)),
            round != null ? round : 1
        );
    }

    public String buildApprovalUrl(String token, String action) {
        return baseUrl + "/approvals/decide?token=" + token + "&action=" + action;
    }

    public String buildReviewUrl(String token) {
        return baseUrl + "/approvals/decide?token=" + token;
    }
}
