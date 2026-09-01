package com.tenxengage.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "builder_field_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"sectionConfig", "dataObjectField"})
@ToString(exclude = {"sectionConfig", "dataObjectField"})
public class BuilderFieldConfig extends BaseEntity {

    @Column(name = "field_key", nullable = false, length = 100)
    private String fieldKey;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "field_type", nullable = false, length = 30)
    private String fieldType;

    @Column(name = "helper_text", length = 500)
    private String helperText;

    @Column(name = "is_mandatory")
    @Builder.Default
    private boolean isMandatory = false;

    @Column(name = "is_system")
    @Builder.Default
    private boolean isSystem = false;

    @Column(name = "is_eligibility")
    @Builder.Default
    private boolean isEligibility = false;

    @Column(name = "value_source", length = 50)
    private String valueSource;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "value_source_config", columnDefinition = "jsonb")
    private String valueSourceConfig;

    @Column(name = "supports_excel_upload")
    @Builder.Default
    private boolean supportsExcelUpload = false;

    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_config_id", nullable = false)
    private BuilderSectionConfig sectionConfig;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "data_object_field_id")
    private DataObjectField dataObjectField;
}
