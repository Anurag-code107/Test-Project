package com.tenxengage.app.entity;

import com.tenxengage.app.entity.enums.EligibilityRuleType;
import com.tenxengage.app.entity.enums.RuleOperator;
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

@Entity
@Table(name = "eligibility_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EligibilityRule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_group_id", nullable = false)
    private EligibilityRuleGroup ruleGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 20)
    private EligibilityRuleType ruleType;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private RuleOperator operator;

    @Column(length = 500)
    private String value;

    @Column(name = "value_max", length = 255)
    private String valueMax;

    @Column(name = "selected_products", columnDefinition = "TEXT")
    private String selectedProducts;

    @Column(name = "field_id")
    private java.util.UUID fieldId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
