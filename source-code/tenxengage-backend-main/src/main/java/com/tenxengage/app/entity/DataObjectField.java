package com.tenxengage.app.entity;

import com.tenxengage.app.entity.enums.FieldDataType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "data_object_fields")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"dataObject"})
@ToString(exclude = {"dataObject"})
public class DataObjectField extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "data_object_id", nullable = false)
    private DataObject dataObject;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 20)
    private FieldDataType dataType;

    @Column(name = "rule_label")
    private String ruleLabel;

    @Column(name = "exclude_from_rules", nullable = false)
    @Builder.Default
    private boolean excludeFromRules = false;

    @Column(name = "sample_values", columnDefinition = "TEXT")
    private String sampleValues;

    @Column(nullable = false)
    @Builder.Default
    private boolean mandatory = false;

    @Column(name = "rule_widget", length = 30)
    private String ruleWidget;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "visible_on_profile", nullable = false)
    @Builder.Default
    private boolean visibleOnProfile = false;

    @Column(name = "editable_by_user", nullable = false)
    @Builder.Default
    private boolean editableByUser = false;
}
