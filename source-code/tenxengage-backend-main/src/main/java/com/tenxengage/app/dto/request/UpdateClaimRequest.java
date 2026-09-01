package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record UpdateClaimRequest(
    Map<String, String> rewardAdjustments,
    String statusChange,
    @NotBlank(message = "Comment is required") String comment
) {}
