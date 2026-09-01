package com.tenxengage.app.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "eligibility_rule_groups")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EligibilityRuleGroup extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requirement_id", nullable = false)
    private SalesRequirement requirement;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @OneToMany(mappedBy = "ruleGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<EligibilityRule> rules = new ArrayList<>();
}
