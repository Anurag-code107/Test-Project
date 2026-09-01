package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CloneClientRoleRequest(
    @NotBlank @Size(max = 100)
    String name,
    @Size(max = 500)
    String description
) {}
