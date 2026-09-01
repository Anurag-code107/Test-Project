package com.tenxengage.app.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkUpdateUserPreferencesRequest(
    @NotEmpty(message = "At least one preference update is required")
    @Valid
    List<UpdateUserNotificationPreferenceRequest> preferences
) {}
