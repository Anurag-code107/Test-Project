package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CreateBuilderFieldRequest(
    @NotBlank String fieldKey,
    @NotBlank String displayName,
    @NotBlank String fieldType,
    String helperText,
    boolean isMandatory,
    boolean isEligibility,
    UUID dataObjectFieldId,
    String valueSource,
    String valueSourceConfig,
    boolean supportsExcelUpload
) {}
