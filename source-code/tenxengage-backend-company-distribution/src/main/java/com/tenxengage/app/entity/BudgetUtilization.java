package com.tenxengage.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "budget_utilizations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BudgetUtilization extends BaseEntity {

    @Column(name = "incentive_id", nullable = false)
    private UUID incentiveId;

    @Column(name = "currency_id", nullable = false, length = 50)
    private String currencyId;

    @Column(name = "location_value_id")
    private UUID locationValueId;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal utilized = BigDecimal.ZERO;
}
