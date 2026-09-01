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
@Table(name = "sales_requirements")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SalesRequirement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incentive_id", nullable = false)
    private Incentive incentive;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @OneToMany(mappedBy = "requirement", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<EligibilityRuleGroup> eligibilityGroups = new ArrayList<>();

    @OneToMany(mappedBy = "requirement", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PayoutConfig> payouts = new ArrayList<>();
}
