package com.tenxengage.app.entity;

import com.tenxengage.app.entity.enums.ComplianceRiskLevel;
import com.tenxengage.app.entity.enums.IncentiveStatus;
import com.tenxengage.app.entity.enums.IncentiveType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "incentives")
@Filter(name = "tenantFilter", condition = "client_id = :clientId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"budgets", "audienceRules", "salesRequirements",
    "trainingCourses", "activityDefinitions", "journeyStages", "documents", "approvers",
    "client", "createdByUser"})
public class Incentive extends BaseEntity implements TenantAware {

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "incentive_type", nullable = false, length = 20)
    private IncentiveType incentiveType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private IncentiveStatus status = IncentiveStatus.DRAFT;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", insertable = false, updatable = false)
    private Client client;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", insertable = false, updatable = false)
    private User createdByUser;

    @Column(name = "start_date")
    private Instant startDate;

    @Column(name = "end_date")
    private Instant endDate;

    @Column(length = 50)
    private String timezone;

    @Column(name = "reward_currencies", length = 500)
    private String rewardCurrencies;

    @Column(name = "reward_message", length = 500)
    private String rewardMessage;

    @Column(name = "reward_amounts", columnDefinition = "TEXT")
    private String rewardAmounts;

    @Column(name = "journey_sequential")
    @Builder.Default
    private Boolean journeySequential = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    @OneToMany(mappedBy = "incentive", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<IncentiveBudget> budgets = new ArrayList<>();

    @OneToMany(mappedBy = "incentive", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<IncentiveAudienceRule> audienceRules = new ArrayList<>();

    @OneToMany(mappedBy = "incentive", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SalesRequirement> salesRequirements = new ArrayList<>();

    @OneToMany(mappedBy = "incentive", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TrainingCourseAssignment> trainingCourses = new ArrayList<>();

    @OneToMany(mappedBy = "incentive", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ActivityDefinition> activityDefinitions = new ArrayList<>();

    @OneToMany(mappedBy = "incentive", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<JourneyStage> journeyStages = new ArrayList<>();

    @OneToMany(mappedBy = "incentive", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<IncentiveDocument> documents = new ArrayList<>();

    @OneToMany(mappedBy = "incentive", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<IncentiveApprover> approvers = new ArrayList<>();

    @Column(name = "requires_approval")
    @Builder.Default
    private Boolean requiresApproval = false;

    @Column(name = "required_approvals")
    @Builder.Default
    private Integer requiredApprovals = 0;

    @Column(name = "fiscal_years", length = 500)
    private String fiscalYears;

    @Column(name = "fiscal_quarters", length = 500)
    private String fiscalQuarters;

    @Column(name = "training_required_count")
    private Integer trainingRequiredCount;

    @Column(name = "countries_text", columnDefinition = "TEXT")
    private String countriesText;

    @Column(name = "specific_partners", columnDefinition = "TEXT")
    private String specificPartners;

    @Column(name = "approval_round", nullable = false)
    @Builder.Default
    private Integer approvalRound = 1;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Column(name = "max_per_partner", precision = 15, scale = 2)
    private BigDecimal maxPerPartner;

    @Column(name = "max_per_user", precision = 15, scale = 2)
    private BigDecimal maxPerUser;

    @Column(name = "max_claimers_per_deal", nullable = false)
    @Builder.Default
    private Integer maxClaimersPerDeal = 1;

    @Column(name = "business_objective", columnDefinition = "TEXT")
    private String businessObjective;

    @Enumerated(EnumType.STRING)
    @Column(name = "compliance_risk_level", length = 20)
    private ComplianceRiskLevel complianceRiskLevel;

    @Column(name = "compliance_approved_at")
    private Instant complianceApprovedAt;

    @Column(name = "compliance_approved_by")
    private UUID complianceApprovedBy;

    @Column(name = "max_per_partner_by_currency", columnDefinition = "TEXT")
    private String maxPerPartnerByCurrency;

    @Column(name = "max_per_user_by_currency", columnDefinition = "TEXT")
    private String maxPerUserByCurrency;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_field_values", columnDefinition = "jsonb")
    private String customFieldValues;
}
