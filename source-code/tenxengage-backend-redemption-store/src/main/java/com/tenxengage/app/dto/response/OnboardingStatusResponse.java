package com.tenxengage.app.dto.response;

import java.util.UUID;

public record OnboardingStatusResponse(
    UUID userId,
    String email,
    String firstName,
    String lastName,
    int currentStep,
    boolean completed,
    String region,
    RegionalComplianceConfigResponse complianceConfig
) {
}
