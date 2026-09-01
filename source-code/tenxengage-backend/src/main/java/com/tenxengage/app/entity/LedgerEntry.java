package com.tenxengage.app.entity;

import com.tenxengage.app.entity.enums.LedgerEntryType;
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

@Entity
@Table(name = "ledger_entries")
@Filter(name = "tenantFilter", condition = "client_id = :clientId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LedgerEntry extends BaseEntity implements TenantAware {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "reward_wallet_id", nullable = false)
    private UUID rewardWalletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 30)
    private LedgerEntryType entryType;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency_id", nullable = false, length = 50)
    private String currencyId;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "available_balance_before", nullable = false, precision = 18, scale = 2)
    private BigDecimal availableBalanceBefore;

    @Column(name = "available_balance_after", nullable = false, precision = 18, scale = 2)
    private BigDecimal availableBalanceAfter;

    @Column(name = "reserved_balance_before", nullable = false, precision = 18, scale = 2)
    private BigDecimal reservedBalanceBefore;

    @Column(name = "reserved_balance_after", nullable = false, precision = 18, scale = 2)
    private BigDecimal reservedBalanceAfter;
}
