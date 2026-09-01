package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiTourMatchRequest(
    @NotBlank @Size(max = 500) String query,
    @NotBlank String role
) {}
