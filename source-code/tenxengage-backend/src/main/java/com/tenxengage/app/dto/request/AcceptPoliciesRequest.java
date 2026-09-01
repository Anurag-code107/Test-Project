package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record AcceptPoliciesRequest(
    @NotBlank String token,
    @NotEmpty List<UUID> policyIds
) {
}
