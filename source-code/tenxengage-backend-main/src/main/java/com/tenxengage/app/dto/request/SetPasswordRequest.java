package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SetPasswordRequest(
    @NotBlank String token,
    @NotBlank String password
) {
}
