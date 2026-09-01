package com.tenxengage.app.dto.request;

public record UpdateLocationLevelSettingsRequest(
    Boolean useInBuilder,
    Boolean useInFilters,
    Boolean isRequired
) {}
