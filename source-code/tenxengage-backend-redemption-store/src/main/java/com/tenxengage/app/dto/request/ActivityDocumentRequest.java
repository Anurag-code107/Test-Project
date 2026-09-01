package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ActivityDocumentRequest(
    @NotBlank String name,
    String description,
    boolean required
) {
}
