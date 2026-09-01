package com.tenxengage.app.dto.request;

import java.util.List;

public record EligibilityRuleGroupRequest(
    List<EligibilityRuleRequest> rules
) {
}
