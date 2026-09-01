package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateBreachIncidentRequest(
    @NotBlank(message = "Description is required")
    String description,

    @NotBlank(message = "Severity is required")
    String severity,

    String dataAffected,

    @NotNull(message = "Detected at timestamp is required")
    Instant detectedAt
) {}
