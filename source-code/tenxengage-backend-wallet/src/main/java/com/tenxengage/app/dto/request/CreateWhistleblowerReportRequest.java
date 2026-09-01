package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateWhistleblowerReportRequest(
    @NotBlank(message = "Report type is required")
    String reportType,

    @NotBlank(message = "Description is required")
    @Size(min = 20, max = 5000, message = "Description must be between 20 and 5000 characters")
    String description,

    @Size(max = 500, message = "Evidence URL must not exceed 500 characters")
    String evidenceUrl,

    String reporterEmail,

    String reporterName,

    boolean anonymous,

    UUID clientId
) {}
