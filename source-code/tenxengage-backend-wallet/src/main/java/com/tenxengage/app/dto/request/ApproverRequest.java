package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ApproverRequest(
    @NotBlank String email,
    @NotBlank String category
) {
}
