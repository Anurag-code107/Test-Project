package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UnclaimRequest(
    @NotBlank(message = "Comment is required") String comment
) {}
