package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.EligibilityRuleGroup;

import java.util.List;
import java.util.UUID;

public record EligibilityRuleGroupResponse(
    UUID id,
    List<EligibilityRuleResponse> rules
) {

    public static EligibilityRuleGroupResponse from(EligibilityRuleGroup group) {
        return new EligibilityRuleGroupResponse(
            group.getId(),
            group.getRules().stream()
                .map(EligibilityRuleResponse::from)
                .toList()
        );
    }
}
