package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record SalesRequirementRequest(
    @NotBlank String name,
    List<EligibilityRuleGroupRequest> eligibilityGroups,
    List<PayoutConfigRequest> payouts
) {
}
