package com.tenxengage.app.entity;

import com.tenxengage.app.entity.enums.PayoutType;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "payout_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PayoutConfig extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requirement_id", nullable = false)
    private SalesRequirement requirement;

    @Column(name = "currency_id", nullable = false, length = 50)
    private String currencyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payout_type", nullable = false, length = 20)
    private PayoutType payoutType;

    @Column(length = 30)
    private String against;

    @Column(name = "max_per_deal", precision = 15, scale = 2)
    private BigDecimal maxPerDeal;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @OneToMany(mappedBy = "payoutConfig", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PayoutBand> bands = new ArrayList<>();
}
