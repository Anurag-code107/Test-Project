package com.tenxengage.app.entity.xtrm;

import com.tenxengage.app.entity.BaseEntity;
import com.tenxengage.app.entity.TenantAware;
import com.tenxengage.app.entity.enums.xtrm.RedemptionPayoutMethod;
import com.tenxengage.app.entity.enums.xtrm.XtrmEnrollmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-user XTRM payout profile: maps a platform user to their XTRM recipient id ({@code PAT}) and payout config.
 * One row per user. Stores only XTRM reference ids + the address XTRM {@code CreateUser} requires — never
 * raw bank/card numbers (those are pass-through to XTRM). No soft-delete (1:1 system record).
 */
@Entity
@Table(name = "partner_redemption")
@Filter(name = "tenantFilter", condition = "client_id = :clientId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PartnerRedemption extends BaseEntity implements TenantAware {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    /** XTRM PAT account id returned by CreateUser; the payout RecipientUserID. Null until enrolled. */
    @Column(name = "recipient_user_id", length = 50)
    private String recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "enrollment_status", nullable = false, length = 30)
    @Builder.Default
    private XtrmEnrollmentStatus enrollmentStatus = XtrmEnrollmentStatus.NOT_ENROLLED;

    /** Sanitized last enrollment error (no PII) for retry diagnostics. */
    @Column(name = "enrollment_error", length = 500)
    private String enrollmentError;

    /** XTRM AccountIdentityLevel (e.g. "Standard") — informational for limit UX. */
    @Column(name = "identity_level", length = 30)
    private String identityLevel;

    // --- Address (PII) — required by XTRM CreateUser; reused for bank linking ---
    /** Grouped address value object mapped to the same 6 address columns. */
    @Embedded
    private PartnerAddress address;

    @Enumerated(EnumType.STRING)
    @Column(name = "payout_method", nullable = false, length = 30)
    @Builder.Default
    private RedemptionPayoutMethod payoutMethod = RedemptionPayoutMethod.ANYPAY;

    /** XTRM BeneficiaryId from LinkBankBeneficiary — a reference, NOT the bank account number. */
    @Column(name = "partner_linked_bank_id", length = 100)
    private String partnerLinkedBankId;

    /** Masked display-only label for the linked bank (e.g. "Wells Fargo ••1898"). */
    @Column(name = "linked_bank_label", length = 100)
    private String linkedBankLabel;

    /** Default card's XTRM CardToken from LinkCard — a reference for the CARD payout rail, NOT the card number. */
    @Column(name = "partner_linked_card_id", length = 100)
    private String partnerLinkedCardId;

    /** Masked display-only label for the default linked card (e.g. "Visa ••1111"). */
    @Column(name = "linked_card_label", length = 100)
    private String linkedCardLabel;

    @Column(name = "enrolled_at")
    private Instant enrolledAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
