package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.DataObjectField;
import com.tenxengage.app.entity.enums.FieldDataType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

public record RuleFieldResponse(
    UUID id,
    String name,
    FieldDataType dataType,
    String ruleLabel,
    String ruleWidget,
    List<String> sampleValues,
    UUID dataObjectId,
    String dataObjectName
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static RuleFieldResponse from(DataObjectField field) {
        List<String> samples = null;
        if (field.getSampleValues() != null && !field.getSampleValues().isBlank()) {
            try {
                samples = MAPPER.readValue(field.getSampleValues(), new TypeReference<>() {});
            } catch (Exception ignored) {
            }
        }
        return new RuleFieldResponse(
            field.getId(),
            field.getName(),
            field.getDataType(),
            field.getRuleLabel(),
            field.getRuleWidget(),
            samples,
            field.getDataObject().getId(),
            field.getDataObject().getName()
        );
    }
}
