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

    // --- Default company admin (D-16 supersedes D-1) ---------------------------------------------------
    //
    // This person IS a platform user: a PARTNER_ADMIN login is created with the company. The first five
    // fields are supplied by the client admin at creation — exactly what a login needs — and the address is
    // supplied by the admin themselves once they sign in, which is what provisions the payout account.
    //
    // The split exists because XTRM refuses to reuse an email address. A client admin's typo in admin_email
    // burns it permanently, so the person who owns the address is the one who should type it.
    //
    // Columns rather than metadata JSONB on purpose. partnerType and contactEmail already live in that blob,
    // which is why neither can be indexed, constrained, or found by anyone reading the schema. These feed a
    // vendor integration — a typo in admin_country_iso2 fails a payout — so the schema should be able to
    // describe them.
    //
    // All nullable: every company that predates this feature has none, and a company can legitimately exist
    // with no payout intent. The all-or-nothing rule is enforced in the service, because bean validation
    // cannot express "all present or all absent" without a custom annotation.

    @Column(name = "admin_first_name", length = 100)
    private String adminFirstName;

    @Column(name = "admin_last_name", length = 100)
    private String adminLastName;

    @Column(name = "admin_email", length = 255)
    private String adminEmail;

    @Column(name = "admin_mobile_number", length = 20)
    private String adminMobileNumber;

    @Column(name = "admin_city", length = 100)
    private String adminCity;

    @Column(name = "admin_region", length = 100)
    private String adminRegion;

    @Column(name = "admin_postal_code", length = 20)
    private String adminPostalCode;

    @Column(name = "admin_country_iso2", length = 2)
    private String adminCountryIso2;

    /**
     * True when the admin can be given a login — the five fields {@code CreateUserRequest} needs.
     *
     * <p>Distinct from {@link #hasCompleteAdminDetails()} on purpose: a company can have an admin who can
     * sign in long before that admin has supplied the address XTRM requires. Provisioning waits for the
     * second, which is what stops a client admin's guess at someone else's address reaching the vendor.</p>
     */
    public boolean hasAdminIdentity() {
        return notBlank(adminFirstName) && notBlank(adminLastName) && notBlank(adminEmail)
                && notBlank(adminMobileNumber) && notBlank(adminCountryIso2);
    }

    /** True when every admin field is present — the all-or-nothing group XTRM needs to create a beneficiary. */
    public boolean hasCompleteAdminDetails() {
        return notBlank(adminFirstName) && notBlank(adminLastName) && notBlank(adminEmail)
                && notBlank(adminMobileNumber) && notBlank(adminCity) && notBlank(adminRegion)
                && notBlank(adminPostalCode) && notBlank(adminCountryIso2);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
