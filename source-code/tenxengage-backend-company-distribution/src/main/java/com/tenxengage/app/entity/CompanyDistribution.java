package com.tenxengage.app.entity;

import com.tenxengage.app.entity.enums.DistributionRail;
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

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One distribution a partner admin submitted: the intent and grouping behind N per-recipient
 * {@link CompanyDistributionItem} rows.
 *
 * <p>This table exists because {@code redemption_requests} cannot express a distribution — it has no notion
 * of a batch, a rail, an initiating admin, or a note to recipients. The payout <em>lifecycle</em> still lives
 * on {@code redemption_requests} for the two rails that leave the platform (design §4.1).</p>
 *
 * <p>No soft-delete: a submitted distribution moved money and is an audit record, not user content.</p>
 */
@Entity
@Table(name = "company_distributions")
@Filter(name = "tenantFilter", condition = "client_id = :clientId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CompanyDistribution extends BaseEntity implements TenantAware {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    /** The company whose wallet is being spent. Every read is scoped by this — one company never sees another's. */
    @Column(name = "partner_company_id", nullable = false)
    private UUID partnerCompanyId;

    /** The COMPANY {@link RewardWallet} the money is drawn from. */
    @Column(name = "source_wallet_id", nullable = false)
    private UUID sourceWalletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rail", nullable = false, length = 20)
    private DistributionRail rail;

    /** The gift-card SKU. Required for {@code GIFT_CARD}, null otherwise — {@code chk_distribution_catalog_item}. */
    @Column(name = "catalog_item_id")
    private UUID catalogItemId;

    @Column(name = "currency_id", nullable = false, length = 50)
    private String currencyId;

    /**
     * The partner admin who sent it — the single home for this fact. Distribution History's "Initiated by"
     * and Company Award History's "Awarded by" both read it from here rather than from the payout leg
     * (OQ-16), so it cannot drift.
     */
    @Column(name = "initiated_by_user_id", nullable = false)
    private UUID initiatedByUserId;

    @Column(name = "recipient_count", nullable = false)
    private int recipientCount;

    /**
     * What was <b>requested</b>: {@code amount × recipientCount}. NOT what moved — after a partial failure
     * the settled total is lower. Surfaces must show both, or a partially-failed distribution reads as
     * though it paid out in full (design §4.4).
     */
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    /** The admin's message, shown to recipients on their award. */
    @Column(name = "note", length = 500)
    private String note;

    /** Dedupe point for the whole distribution; a re-POST returns the original rather than sending twice. */
    @Column(name = "client_idempotency_key", length = 255)
    private String clientIdempotencyKey;
}
