package com.tenxengage.app.entity.xtrm;

import com.tenxengage.app.entity.BaseEntity;
import com.tenxengage.app.entity.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

/**
 * One XTRM bank/ACH beneficiary linked by a user (F-03 multi-bank enhancement). Cached locally so the
 * Payout tab can list a user's banks with a fast DB read instead of an XTRM {@code GetLinkedBankAccounts}
 * round-trip on every view. XTRM still owns the beneficiary — add/remove go to XTRM too and this table is
 * kept in sync on those writes.
 *
 * <p>Stores only the XTRM {@code BeneficiaryId} reference + a masked display label — never the raw
 * account/routing number (those are pass-through to XTRM). The <b>default</b> bank is deliberately NOT a
 * column here: it stays on {@code partner_redemption.partner_linked_bank_id} so the payout path is
 * unchanged and "one default" is guaranteed by a single value. Removal is a soft-delete (XTRM holds the
 * hard delete); the unique index is partial ({@code WHERE deleted = false}) so a re-add can't collide.</p>
 */
@Entity
@Table(name = "partner_linked_bank")
@Filter(name = "tenantFilter", condition = "client_id = :clientId")
@SQLRestriction("deleted = false")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PartnerLinkedBank extends BaseEntity implements TenantAware {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** XTRM BeneficiaryId from LinkBankBeneficiary — a reference, NOT the bank account number. */
    @Column(name = "xtrm_beneficiary_id", nullable = false, length = 100)
    private String xtrmBeneficiaryId;

    /** Masked display-only label (e.g. "Wells Fargo ••1898"); never contains the full number. */
    @Column(name = "masked_label", nullable = false, length = 100)
    private String maskedLabel;

    /** Payout currency of this bank. v1 is USD-only (ACH); stored for forward-compat. */
    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    /** Beneficiary country (2-letter ISO). v1 is US-only; stored for forward-compat. */
    @Column(name = "country_iso2", nullable = false, length = 2)
    @Builder.Default
    private String countryIso2 = "US";

    /** XTRM WithdrawType (ACH for US low-value; WIRE for international — deferred). */
    @Column(name = "withdraw_type", nullable = false, length = 20)
    @Builder.Default
    private String withdrawType = "ACH";

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
