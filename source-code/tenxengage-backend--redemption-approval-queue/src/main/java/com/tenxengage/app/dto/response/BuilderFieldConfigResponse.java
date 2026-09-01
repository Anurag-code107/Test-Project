package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.BuilderFieldConfig;
import com.tenxengage.app.entity.DataObjectField;

import java.util.UUID;

public record BuilderFieldConfigResponse(
    UUID id,
    String fieldKey,
    String displayName,
    String fieldType,
    String helperText,
    boolean isMandatory,
    boolean isSystem,
    boolean isEligibility,
    UUID dataObjectFieldId,
    String dataObjectFieldName,
    String dataObjectName,
    String valueSource,
    String valueSourceConfig,
    boolean supportsExcelUpload,
    int sortOrder
) {
    public static BuilderFieldConfigResponse from(BuilderFieldConfig field) {
        DataObjectField dof = field.getDataObjectField();
        return new BuilderFieldConfigResponse(
            field.getId(),
            field.getFieldKey(),
            field.getDisplayName(),
            field.getFieldType(),
            field.getHelperText(),
            field.isMandatory(),
            field.isSystem(),
            field.isEligibility(),
            dof != null ? dof.getId() : null,
            dof != null ? dof.getName() : null,
            dof != null && dof.getDataObject() != null ? dof.getDataObject().getName() : null,
            field.getValueSource(),
            field.getValueSourceConfig(),
            field.isSupportsExcelUpload(),
            field.getSortOrder()
        );
    }
}
