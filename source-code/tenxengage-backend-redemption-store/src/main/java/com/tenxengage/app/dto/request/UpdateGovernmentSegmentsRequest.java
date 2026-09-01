package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateGovernmentSegmentsRequest(
    @NotNull(message = "Segment values list is required")
    List<String> segmentValues
) {}
