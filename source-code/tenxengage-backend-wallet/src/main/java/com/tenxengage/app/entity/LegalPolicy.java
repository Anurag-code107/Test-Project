package com.tenxengage.app.entity;

import com.tenxengage.app.entity.enums.PolicyType;
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
@Table(name = "legal_policies")
@Filter(name = "tenantFilter", condition = "client_id = :clientId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LegalPolicy extends BaseEntity implements TenantAware {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", nullable = false, length = 50)
    private PolicyType policyType;

    @Column(nullable = false, length = 20)
    private String version;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "content_url", length = 500)
    private String contentUrl;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "effective_date", nullable = false)
    @Builder.Default
    private Instant effectiveDate = Instant.now();

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
