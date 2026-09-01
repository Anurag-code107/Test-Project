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

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "eligibility_payouts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"eligibilityMapping"})
public class EligibilityPayout extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eligibility_mapping_id", nullable = false)
    private PoEligibilityMapping eligibilityMapping;

    @Column(name = "eligibility_mapping_id", insertable = false, updatable = false)
    private UUID eligibilityMappingId;

    @Column(name = "requirement_id", nullable = false)
    private UUID requirementId;

    @Column(name = "currency_id", nullable = false, length = 50)
    private String currencyId;

    @Column(name = "payout_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal payoutAmount;
}
