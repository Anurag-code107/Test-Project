package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddCaseUpdateRequest(
    @NotBlank(message = "Update text is required")
    @Size(max = 5000, message = "Update text must not exceed 5000 characters")
    String updateText
) {}
