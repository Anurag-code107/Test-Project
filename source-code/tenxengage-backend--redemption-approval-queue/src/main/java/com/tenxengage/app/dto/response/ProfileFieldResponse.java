package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.enums.FieldDataType;

import java.util.List;
import java.util.UUID;

public record ProfileFieldResponse(
    UUID fieldId,
    String fieldName,
    FieldDataType dataType,
    String value,
    boolean editable,
    int sortOrder,
    List<String> sampleValues
) {}
