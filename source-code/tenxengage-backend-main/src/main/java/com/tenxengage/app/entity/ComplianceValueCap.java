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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "compliance_value_caps", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"country_code", "client_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceValueCap {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "country_code", nullable = false, length = 10)
    private String countryCode;

    @Column(name = "annual_cap_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal annualCapAmount;

    @Column(name = "annual_cap_currency", nullable = false, length = 10)
    @Builder.Default
    private String annualCapCurrency = "USD";

    @Column(name = "enhanced_approval_threshold", nullable = false, precision = 15, scale = 2)
    private BigDecimal enhancedApprovalThreshold;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
