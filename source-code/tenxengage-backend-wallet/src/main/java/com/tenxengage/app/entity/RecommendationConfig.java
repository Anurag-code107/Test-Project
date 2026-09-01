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
@Table(name = "recommendation_configs")
@Filter(name = "tenantFilter", condition = "client_id = :clientId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RecommendationConfig extends BaseEntity implements TenantAware {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "training_enabled", nullable = false)
    @Builder.Default
    private boolean trainingEnabled = true;

    @Column(name = "incentive_enabled", nullable = false)
    @Builder.Default
    private boolean incentiveEnabled = true;

    @Column(name = "max_training_recommendations", nullable = false)
    @Builder.Default
    private int maxTrainingRecommendations = 5;

    @Column(name = "max_incentive_recommendations", nullable = false)
    @Builder.Default
    private int maxIncentiveRecommendations = 5;

    @Column(name = "reward_currency_id", length = 50)
    private String rewardCurrencyId;

    @Column(name = "training_completion_reward", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal trainingCompletionReward = BigDecimal.ZERO;

    @Column(name = "incentive_completion_reward", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal incentiveCompletionReward = BigDecimal.ZERO;
}
