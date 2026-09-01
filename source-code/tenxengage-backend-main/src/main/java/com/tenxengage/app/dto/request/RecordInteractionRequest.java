package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotEmpty;

public record RecordInteractionRequest(
    @NotEmpty String interactionType
) {}
