package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateClientRoleRequest(
    @Size(max = 100)
    String name,
    @Size(max = 500)
    String description
) {}
