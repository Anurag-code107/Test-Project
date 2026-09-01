package com.tenxengage.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "partner_program_acknowledgments", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"partner_company_id", "incentive_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerProgramAcknowledgment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "partner_company_id", nullable = false)
    private UUID partnerCompanyId;

    @Column(name = "incentive_id", nullable = false)
    private UUID incentiveId;

    @Column(name = "acknowledged_by", nullable = false)
    private UUID acknowledgedBy;

    @Column(name = "acknowledged_at", nullable = false)
    @Builder.Default
    private Instant acknowledgedAt = Instant.now();

    @Column(name = "policy_version", length = 20)
    private String policyVersion;
}
