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
import org.hibernate.annotations.Filter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "po_eligibility_mappings")
@Filter(name = "tenantFilter", condition = "client_id = :clientId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"purchaseOrder", "incentive", "payouts"})
public class PoEligibilityMapping extends BaseEntity implements TenantAware {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "tagging_job_id", nullable = false)
    private UUID taggingJobId;

    @Column(name = "purchase_order_id", nullable = false)
    private UUID purchaseOrderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", insertable = false, updatable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "incentive_id", nullable = false)
    private UUID incentiveId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incentive_id", insertable = false, updatable = false)
    private Incentive incentive;

    @Column(nullable = false)
    private Boolean eligible;

    @Column(name = "ineligibility_reason", columnDefinition = "TEXT")
    private String ineligibilityReason;

    @OneToMany(mappedBy = "eligibilityMapping", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<EligibilityPayout> payouts = new ArrayList<>();
}
