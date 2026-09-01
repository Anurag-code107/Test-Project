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
 * One XTRM card a user has linked (multi-card, F-03 enhancement). A card is a DUAL-PURPOSE instrument:
 * a payout rail ({@code TransferFund} + {@code CardToken}) AND a withdrawal destination. Mirrors
 * {@link PartnerLinkedBank}; the default card for the CARD payout rail is pointed to by
 * {@code partner_redemption.partner_linked_card_id}.
 *
 * <p>⚠️ <b>PCI:</b> stores ONLY the XTRM {@code CardToken} + masked last-4 + type/status — <b>NEVER</b> the
 * card number (PAN), CVV, or full expiry (those are transient in the {@code LinkCard} call, never persisted).</p>
 */
@Entity
@Table(name = "partner_linked_card")
@Filter(name = "tenantFilter", condition = "client_id = :clientId")
@SQLRestriction("deleted = false")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PartnerLinkedCard extends BaseEntity implements TenantAware {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** XTRM CardToken from LinkCard — a reference, NOT the card number. */
    @Column(name = "card_token", nullable = false, length = 100)
    private String cardToken;

    /** Last 4 digits only (PCI-allowed) — for the masked display "Visa ••1111". */
    @Column(name = "masked_last4", length = 4)
    private String maskedLast4;

    @Column(name = "card_type", length = 30)
    private String cardType;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
