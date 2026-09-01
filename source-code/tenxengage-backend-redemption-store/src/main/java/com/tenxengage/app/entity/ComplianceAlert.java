package com.tenxengage.app.entity;

import com.tenxengage.app.entity.enums.ComplianceAlertStatus;
import com.tenxengage.app.entity.enums.ComplianceAlertType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "compliance_alerts")
@Filter(name = "tenantFilter", condition = "client_id = :clientId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ComplianceAlert extends BaseEntity implements TenantAware {

    @Column(name = "client_id")
    private UUID clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 50)
    private ComplianceAlertType alertType;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "partner_company_id")
    private UUID partnerCompanyId;

    @Column(name = "incentive_id")
    private UUID incentiveId;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ComplianceAlertStatus status = ComplianceAlertStatus.NEW;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;
}
