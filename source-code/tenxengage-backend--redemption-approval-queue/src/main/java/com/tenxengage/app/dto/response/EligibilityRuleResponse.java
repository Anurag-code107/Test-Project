package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.EligibilityRule;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record EligibilityRuleResponse(
    UUID id,
    String ruleType,
    String operator,
    String value,
    String valueMax,
    List<String> selectedProducts,
    List<String> customerTypes,
    List<String> listValues,
    UUID fieldId
) {

    public static EligibilityRuleResponse from(EligibilityRule rule) {
        List<String> products = Collections.emptyList();
        List<String> customers = Collections.emptyList();
        List<String> listVals = Collections.emptyList();

        if (rule.getSelectedProducts() != null && !rule.getSelectedProducts().isBlank()) {
            String raw = rule.getSelectedProducts();
            if (rule.getRuleType().name().equals("CUSTOMER_TYPE")) {
                customers = Arrays.asList(raw.split(","));
                listVals = customers;
            } else if (rule.getRuleType().name().equals("PRODUCTS")) {
                products = Arrays.asList(raw.split(","));
            } else {
                // Generic list values
                listVals = Arrays.asList(raw.split(","));
            }
        }

        // Return fieldId as ruleType if present, so frontend gets the field UUID back
        String responseRuleType = rule.getFieldId() != null
            ? rule.getFieldId().toString()
            : rule.getRuleType().name();

        return new EligibilityRuleResponse(
            rule.getId(),
            responseRuleType,
            rule.getOperator() != null ? rule.getOperator().name() : null,
            rule.getValue(),
            rule.getValueMax(),
            products,
            customers,
            listVals,
            rule.getFieldId()
        );
    }
}
