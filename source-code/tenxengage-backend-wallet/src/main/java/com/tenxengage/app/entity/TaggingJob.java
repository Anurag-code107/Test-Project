package com.tenxengage.app.entity;

import com.tenxengage.app.entity.enums.TaggingJobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.Filter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "tagging_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Filter(name = "tenantFilter", condition = "client_id = :clientId")
public class TaggingJob extends BaseEntity implements TenantAware {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TaggingJobStatus status = TaggingJobStatus.RUNNING;

    @Column(name = "pos_analyzed")
    @Builder.Default
    private int posAnalyzed = 0;

    @Column(name = "eligible_deals")
    @Builder.Default
    private int eligibleDeals = 0;

    @Column(name = "incentives_matched")
    @Builder.Default
    private int incentivesMatched = 0;

    @Column(name = "products_discovered")
    @Builder.Default
    private int productsDiscovered = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
