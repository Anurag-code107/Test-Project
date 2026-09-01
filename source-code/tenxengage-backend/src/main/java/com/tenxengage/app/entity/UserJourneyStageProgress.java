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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_journey_stage_progress")
@Filter(name = "tenantFilter", condition = "client_id = :clientId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserJourneyStageProgress extends BaseEntity implements TenantAware {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "journey_id", nullable = false)
    private UUID journeyId;

    @Column(name = "stage_id", nullable = false)
    private UUID stageId;

    @Column(name = "linked_incentive_id", nullable = false)
    private UUID linkedIncentiveId;

    @Column(name = "completed", nullable = false)
    @Builder.Default
    private boolean completed = false;

    @Column(name = "completed_at")
    private Instant completedAt;
}
