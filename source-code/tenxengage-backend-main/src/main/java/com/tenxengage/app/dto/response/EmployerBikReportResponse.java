package com.tenxengage.app.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record EmployerBikReportResponse(
    String partnerCompanyName,
    int year,
    List<EmployeeReward> employees
) {

    public record EmployeeReward(
        String firstName,
        String lastName,
        String email,
        String countryCode,
        Map<String, BigDecimal> rewardsByCurrency,
        BigDecimal totalUsdEquivalent
    ) {}
}
