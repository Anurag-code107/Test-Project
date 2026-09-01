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

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A completed wallet cash-out ({@code UserWithdrawFund}) — history/audit only (F-03 enhancement). One row
 * per completed withdrawal (bank or card). Does NOT touch {@code reward_wallets}/the ledger (those were
 * debited at redemption; the XTRM wallet is XTRM-side). Holds no card/PAN/CVV/PAT — only a masked label.
 */
@Entity
@Table(name = "partner_withdrawal")
@Filter(name = "tenantFilter", condition = "client_id = :clientId")
@SQLRestriction("deleted = false")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PartnerWithdrawal extends BaseEntity implements TenantAware {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Gross debited from the wallet (XTRM {@code TotalAmount}). */
    @Column(name = "amount_gross", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountGross;

    /** XTRM withdrawal fee. */
    @Column(name = "fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal fee;

    /** Net delivered = gross − fee (XTRM {@code Amount}). */
    @Column(name = "amount_net", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountNet;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** BANK | CARD. */
    @Column(name = "destination_type", nullable = false, length = 10)
    private String destinationType;

    /** Masked display label, e.g. "Wells Fargo ••1898" / "Visa ••1111". */
    @Column(name = "destination_label", length = 100)
    private String destinationLabel;

    /** Our {@code partner_linked_bank} / {@code partner_linked_card} row id. */
    @Column(name = "destination_ref")
    private UUID destinationRef;

    /** XTRM {@code PaymentTransactionId}. */
    @Column(name = "xtrm_payment_transaction_id", length = 100)
    private String xtrmPaymentTransactionId;

    /** COMPLETED | FAILED. */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
