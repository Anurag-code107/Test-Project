package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Batch update for permission grants. Key = permission_key, Value = granted (true/false).
 */
public record UpdatePermissionsRequest(
    @NotNull
    Map<String, Boolean> permissions
) {}
