package com.tenxengage.app.entity;

import com.tenxengage.app.entity.enums.ExpiryNoticeStatus;
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
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "balance_expiry_notices")
@Filter(name = "tenantFilter", condition = "client_id = :clientId")
@SQLRestriction("deleted = false")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BalanceExpiryNotice extends BaseEntity implements TenantAware {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", insertable = false, updatable = false)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private RewardWallet wallet;

    @Column(name = "currency_id", nullable = false, length = 50)
    private String currencyId;

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", insertable = false, updatable = false)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private BalanceExpirationPolicy policy;

    @Column(name = "scheduled_expiry_date", nullable = false)
    private LocalDate scheduledExpiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ExpiryNoticeStatus status = ExpiryNoticeStatus.SCHEDULED;

    @Column(name = "notified_at")
    private Instant notifiedAt;

    @Column(name = "notified_amount", precision = 18, scale = 2)
    private BigDecimal notifiedAmount;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Column(name = "expired_amount", precision = 18, scale = 2)
    private BigDecimal expiredAmount;

    @Column(name = "ledger_entry_id")
    private UUID ledgerEntryId;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
