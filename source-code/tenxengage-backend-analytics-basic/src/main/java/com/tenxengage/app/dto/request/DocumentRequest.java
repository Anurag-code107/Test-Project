package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentRequest(
    @NotBlank @Size(max = 255) String name,
    @NotBlank @Size(max = 50) String documentType,
    @NotBlank @Size(max = 10) String fileType,
    @NotBlank @Size(max = 20) String size
) {
}
