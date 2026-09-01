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

import java.util.UUID;

@Entity
@Table(name = "journey_stages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class JourneyStage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incentive_id", nullable = false)
    private Incentive incentive;

    @Column(name = "linked_incentive_id", nullable = false)
    private UUID linkedIncentiveId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_incentive_id", insertable = false, updatable = false)
    private Incentive linkedIncentive;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
