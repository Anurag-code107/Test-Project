package com.tenxengage.app.dto.response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.entity.DataObjectField;
import com.tenxengage.app.entity.enums.FieldDataType;

import java.util.List;
import java.util.UUID;

public record DataObjectFieldResponse(
    UUID id,
    String name,
    String description,
    FieldDataType dataType,
    String ruleLabel,
    boolean excludeFromRules,
    List<String> sampleValues,
    boolean mandatory,
    int sortOrder,
    boolean visibleOnProfile,
    boolean editableByUser,
    boolean isLocationHierarchyField
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static DataObjectFieldResponse from(DataObjectField field) {
        List<String> samples = null;
        if (field.getSampleValues() != null && !field.getSampleValues().isBlank()) {
            try {
                samples = MAPPER.readValue(field.getSampleValues(), new TypeReference<>() {});
            } catch (Exception ignored) {
                // Invalid JSON — return null
            }
        }
        return new DataObjectFieldResponse(
            field.getId(),
            field.getName(),
            field.getDescription(),
            field.getDataType(),
            field.getRuleLabel(),
            field.isExcludeFromRules(),
            samples,
            field.isMandatory(),
            field.getSortOrder(),
            field.isVisibleOnProfile(),
            field.isEditableByUser(),
            false
        );
    }

    /**
     * Creates a virtual field representing a location hierarchy level.
     * These are not stored in the database but synthesized at query time for Partner Data.
     */
    public static DataObjectFieldResponse locationHierarchyField(
            String levelName, List<String> valueNames, int sortOrder) {
        return new DataObjectFieldResponse(
            null,
            levelName,
            "Geographic " + levelName.toLowerCase() + " of the partner company",
            FieldDataType.LIST,
            null,
            true,
            valueNames,
            true,
            sortOrder,
            false,
            false,
            true
        );
    }
}
