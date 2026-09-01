package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CreateClientRoleRequest(
    @NotBlank @Size(max = 100)
    String name,
    @Size(max = 500)
    String description,
    String baseRoleName,
    @NotBlank
    @Pattern(regexp = "INTERNAL|EXTERNAL", message = "roleType must be INTERNAL or EXTERNAL")
    String roleType,
    Map<String, Boolean> permissions
) {}
