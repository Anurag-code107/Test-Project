package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.RetentionPolicy;

import java.util.UUID;

public record RetentionPolicyResponse(
    UUID id,
    UUID clientId,
    String dataCategory,
    int retentionDays,
    String actionType
) {

    public static RetentionPolicyResponse from(RetentionPolicy policy) {
        return new RetentionPolicyResponse(
            policy.getId(),
            policy.getClientId(),
            policy.getDataCategory().name(),
            policy.getRetentionDays(),
            policy.getActionType().name()
        );
    }
}
