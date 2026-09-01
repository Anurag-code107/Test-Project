package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.ComplianceValueCap;

import java.math.BigDecimal;
import java.util.UUID;

public record ComplianceValueCapResponse(
    UUID id,
    String countryCode,
    BigDecimal annualCapAmount,
    String annualCapCurrency,
    BigDecimal enhancedApprovalThreshold,
    UUID clientId
) {

    public static ComplianceValueCapResponse from(ComplianceValueCap cap) {
        return new ComplianceValueCapResponse(
            cap.getId(),
            cap.getCountryCode(),
            cap.getAnnualCapAmount(),
            cap.getAnnualCapCurrency(),
            cap.getEnhancedApprovalThreshold(),
            cap.getClientId()
        );
    }
}
