package com.tenxengage.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "reward_transactions")
@Filter(name = "tenantFilter", condition = "client_id = :clientId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RewardTransaction extends BaseEntity implements TenantAware {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "claim_action_id")
    private UUID claimActionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "incentive_id", nullable = false)
    private UUID incentiveId;

    @Column(name = "currency_id", nullable = false, length = 50)
    private String currencyId;

    @Column(name = "amount_potential", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountPotential;

    @Column(name = "amount_awarded", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountAwarded;

    @Column(name = "budget_capped", nullable = false)
    @Builder.Default
    private boolean budgetCapped = false;

    @Column(name = "completion_id")
    private UUID completionId;
}
