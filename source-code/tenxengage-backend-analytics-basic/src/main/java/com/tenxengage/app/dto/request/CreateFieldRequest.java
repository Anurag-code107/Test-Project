package com.tenxengage.app.dto.request;

import com.tenxengage.app.entity.enums.FieldDataType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateFieldRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 1000) String description,
    @NotNull FieldDataType dataType,
    @Size(max = 255) String ruleLabel,
    Boolean excludeFromRules,
    List<String> sampleValues,
    Boolean visibleOnProfile,
    Boolean editableByUser
) {}
