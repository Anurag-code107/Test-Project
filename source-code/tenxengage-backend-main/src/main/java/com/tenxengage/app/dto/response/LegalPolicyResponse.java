package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.LegalPolicy;

import java.time.Instant;
import java.util.UUID;

public record LegalPolicyResponse(
    UUID id,
    String policyType,
    String version,
    String title,
    String contentUrl,
    String summary,
    Instant effectiveDate,
    boolean accepted
) {

    public static LegalPolicyResponse from(LegalPolicy policy, boolean accepted) {
        return new LegalPolicyResponse(
            policy.getId(),
            policy.getPolicyType().name(),
            policy.getVersion(),
            policy.getTitle(),
            policy.getContentUrl(),
            policy.getSummary(),
            policy.getEffectiveDate(),
            accepted
        );
    }
}
