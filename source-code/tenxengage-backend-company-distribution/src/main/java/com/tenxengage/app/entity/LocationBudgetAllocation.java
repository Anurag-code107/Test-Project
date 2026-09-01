package com.tenxengage.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "location_budget_allocations", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"budget_id", "location_value_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LocationBudgetAllocation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_id", nullable = false)
    private IncentiveBudget budget;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_value_id", nullable = false)
    private LocationValue locationValue;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
}
