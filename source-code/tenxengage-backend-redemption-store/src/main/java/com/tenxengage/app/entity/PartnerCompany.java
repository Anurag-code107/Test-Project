package com.tenxengage.app.entity;

import com.tenxengage.app.entity.enums.GovernmentDealRestrictionMode;
import com.tenxengage.app.entity.enums.PartnerCompanyStatus;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "partner_companies", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"client_id", "name"})
})
@Filter(name = "tenantFilter", condition = "client_id = :clientId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PartnerCompany extends BaseEntity implements TenantAware {

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "external_partner_id", length = 100)
    private String externalPartnerId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", insertable = false, updatable = false)
    private Client client;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PartnerCompanyStatus status = PartnerCompanyStatus.ACTIVE;

    @Column(length = 500)
    private String website;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @OneToMany(mappedBy = "partnerCompany", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PartnerCompanyLocation> locationAssignments = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private String metadata = "{}";

    @Column(name = "anti_bribery_acknowledged_at")
    private Instant antiBriberyAcknowledgedAt;

    @Column(name = "anti_bribery_policy_version", length = 20)
    private String antiBriberyPolicyVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "government_deal_restriction_mode", length = 20)
    @Builder.Default
    private GovernmentDealRestrictionMode governmentDealRestrictionMode = GovernmentDealRestrictionMode.NONE;
}
