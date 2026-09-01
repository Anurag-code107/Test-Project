package com.tenxengage.app.dto.request;

import java.util.List;

public record EligibilityRuleRequest(
    String ruleType,
    String operator,
    String value,
    String valueMax,
    List<String> selectedProducts,
    List<String> customerTypes,
    List<String> listValues
) {
}
