package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.IncentiveAudienceRule;

import java.util.UUID;

public record AudienceRuleResponse(
    UUID id,
    String ruleType,
    String ruleValue,
    UUID locationLevelId,
    String locationValueName
) {

    public static AudienceRuleResponse from(IncentiveAudienceRule rule) {
        return from(rule, null);
    }

    public static AudienceRuleResponse from(IncentiveAudienceRule rule, String resolvedValueName) {
        UUID levelId = null;
        if ("LOCATION".equals(rule.getRuleType()) && rule.getLocationLevel() != null) {
            levelId = rule.getLocationLevel().getId();
        }
        return new AudienceRuleResponse(
            rule.getId(),
            rule.getRuleType(),
            rule.getRuleValue(),
            levelId,
            resolvedValueName
        );
    }
}
