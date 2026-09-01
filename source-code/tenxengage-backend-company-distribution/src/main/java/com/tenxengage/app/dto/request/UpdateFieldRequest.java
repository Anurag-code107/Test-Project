package com.tenxengage.app.dto.request;

import com.tenxengage.app.entity.enums.FieldDataType;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateFieldRequest(
    @Size(max = 255) String name,
    @Size(max = 1000) String description,
    FieldDataType dataType,
    @Size(max = 255) String ruleLabel,
    Boolean excludeFromRules,
    List<String> sampleValues,
    Boolean visibleOnProfile,
    Boolean editableByUser
) {}
