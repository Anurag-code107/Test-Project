package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.SalesRequirement;

import java.util.List;
import java.util.UUID;

public record SalesRequirementResponse(
    UUID id,
    String name,
    List<EligibilityRuleGroupResponse> eligibilityGroups,
    List<PayoutConfigResponse> payouts
) {

    public static SalesRequirementResponse from(SalesRequirement req) {
        return new SalesRequirementResponse(
            req.getId(),
            req.getName(),
            req.getEligibilityGroups().stream()
                .map(EligibilityRuleGroupResponse::from)
                .toList(),
            req.getPayouts().stream()
                .map(PayoutConfigResponse::from)
                .toList()
        );
    }
}
