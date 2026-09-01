package com.tenxengage.app.entity;

import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionOrigin;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.entity.enums.xtrm.RedemptionPayoutMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "redemption_requests")
@Filter(name = "tenantFilter", condition = "client_id = :clientId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RedemptionRequest extends BaseEntity implements TenantAware {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    /**
     * The payee. For {@code origin = SELF} this is the user who redeemed their own wallet; for
     * {@code origin = COMPANY_DISTRIBUTION} it is the <b>recipient</b> the partner admin sent money to,
     * NOT the admin. Dispatch, settlement and reconciliation all resolve the payee from here, which is
     * exactly why the distribution flow stores the recipient in this column.
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Which store created this payout leg (V51). {@code SELF} = redemption store,
     * {@code COMPANY_DISTRIBUTION} = distribution store.
     *
     * <p><b>{@code @Builder.Default} is load-bearing.</b> Hibernate always includes this column in its
     * INSERT, so without a Java-side default it would send an explicit {@code NULL} and the
     * {@code NOT NULL} constraint would reject <em>every</em> personal redemption. The DB-side
     * {@code DEFAULT 'SELF'} does not help — a column default only applies when the column is omitted.</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 30)
    @Builder.Default
    private RedemptionOrigin origin = RedemptionOrigin.SELF;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private User user;

    @Column(name = "catalog_item_id", nullable = false)
    private UUID catalogItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_item_id", insertable = false, updatable = false)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private RedemptionCatalogItem catalogItem;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_id", nullable = false, length = 50)
    private String currencyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "wallet_type", nullable = false, length = 20)
    private WalletType walletType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RedemptionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_mode", nullable = false, length = 30)
    private RedemptionProcessingMode processingMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private RedemptionCategory category;

    @Column(name = "vendor_reference_id", length = 255)
    private String vendorReferenceId;

    /** XTRM {@code CustomerBatchId} we generated for a real BatchTransfer (BATCH mode); reconciled via the batch
     *  status API. Null for single-dispatch (INSTANT/APPROVAL) items. */
    @Column(name = "customer_batch_id", length = 100)
    private String customerBatchId;

    /** Compact per-item id we send as XTRM {@code CustomerTransactionId} in a BatchTransfer, matched back when
     *  reconciling the batch. Null for single-dispatch items. */
    @Column(name = "customer_transaction_id", length = 50)
    private String customerTransactionId;

    /** Beneficiary-side transaction id from the XTRM {@code TransferFund} response ({@code BeneficiaryTransactionId}).
     *  Reconciliation polls {@code GetUserWalletTransactionDetails} by THIS id (the wallet API is keyed on the
     *  beneficiary transaction, not the payment-side id in {@link #vendorReferenceId}). Null for BATCH items. */
    @Column(name = "beneficiary_transaction_id", length = 50)
    private String beneficiaryTransactionId;

    /** Snapshot of the payout rail used for this redemption, captured at dispatch from the user's
     *  {@link com.tenxengage.app.entity.xtrm.PartnerRedemption} profile — durable even if the user later changes
     *  their default method. Stored as the enum name (EnumType.STRING) so future rails need no migration. */
    @Enumerated(EnumType.STRING)
    @Column(name = "payout_method", length = 30)
    private RedemptionPayoutMethod payoutMethod;

    /** Masked snapshot of the destination the payout went to (e.g. {@code Visa ••1111}, {@code KOTAK ••8943},
     *  or {@code AnyPay wallet}), captured at dispatch. Never holds a full PAN / account number. */
    @Column(name = "payout_destination_label", length = 100)
    private String payoutDestinationLabel;

    /** XTRM beneficiary id of the bank chosen at submit for a bank-transfer redemption (multi-bank support).
     *  Set from the selected {@link com.tenxengage.app.entity.xtrm.PartnerLinkedBank}; NULL → the
     *  after-commit dispatch falls back to the user's default bank. A reference only, never the account number. */
    @Column(name = "payout_beneficiary_id", length = 100)
    private String payoutBeneficiaryId;

    @Column(name = "reserve_ledger_entry_id")
    private UUID reserveLedgerEntryId;

    @Column(name = "debit_ledger_entry_id")
    private UUID debitLedgerEntryId;

    @Column(name = "release_ledger_entry_id")
    private UUID releaseLedgerEntryId;

    @Column(name = "scheduled_batch_date")
    private LocalDate scheduledBatchDate;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "client_idempotency_key", length = 255)
    private String clientIdempotencyKey;

    @Column(name = "dispatch_attempted_at")
    private Instant dispatchAttemptedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;
}
