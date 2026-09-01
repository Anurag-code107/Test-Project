package com.tenxengage.app.dto.request;

import java.util.UUID;

public record UpdateBuilderFieldRequest(
    String displayName,
    String helperText,
    Boolean isMandatory,
    Boolean isEligibility,
    UUID dataObjectFieldId,
    String valueSource,
    String valueSourceConfig,
    Boolean supportsExcelUpload
) {}
