package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignHomeDashboardTemplateRequest(
        @NotNull UUID templateId) {
}
