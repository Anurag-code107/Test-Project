package com.tenxengage.app.entity;

import com.tenxengage.app.entity.enums.DistributionItemStatus;
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
import java.time.Instant;
import java.util.UUID;

/**
 * One recipient's share of a {@link CompanyDistribution} — <b>the record of who was paid</b>, on every rail.
 *
 * <p>Exactly one of two lifecycle owners, enforced by {@code chk_distribution_item_leg}:</p>
 * <ul>
 *   <li><b>Payout rails</b> ({@code GIFT_CARD}, {@code BANK_TRANSFER}) set {@link #redemptionRequestId} and
 *       leave {@link #status} null. Status is read from the redemption row, so it is never stored twice and
 *       cannot drift from the money.</li>
 *   <li><b>{@code WALLET_CREDIT}</b> has no vendor and no payout leg, so it owns {@link #status} plus its
 *       debit/credit/release ledger ids.</li>
 * </ul>
 */
@Entity
@Table(name = "company_distribution_items")
@Filter(name = "tenantFilter", condition = "client_id = :clientId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CompanyDistributionItem extends BaseEntity implements TenantAware {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "distribution_id", nullable = false)
    private UUID distributionId;

    /** The partner seller who received this share. Unique per distribution — nobody is paid twice. */
    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** Payout rails only: the {@link RedemptionRequest} that owns this item's status. Null for WALLET_CREDIT. */
    @Column(name = "redemption_request_id")
    private UUID redemptionRequestId;

    /** WALLET_CREDIT only: {@code RESERVED → COMPLETED | FAILED}. Null for the payout rails. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private DistributionItemStatus status;

    /** WALLET_CREDIT: the company-wallet debit, stamped on settle. */
    @Column(name = "debit_ledger_entry_id")
    private UUID debitLedgerEntryId;

    /** WALLET_CREDIT: the recipient-wallet credit, stamped on settle in the same transaction as the debit. */
    @Column(name = "credit_ledger_entry_id")
    private UUID creditLedgerEntryId;

    /** WALLET_CREDIT: set when this recipient failed definitively and their share went back to available. */
    @Column(name = "release_ledger_entry_id")
    private UUID releaseLedgerEntryId;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "settled_at")
    private Instant settledAt;

    /** True when this item's money moves through XTRM rather than an internal ledger transfer. */
    public boolean isVendorPayout() {
        return redemptionRequestId != null;
    }
}
