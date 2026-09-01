package com.tenxengage.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "purchase_orders")
@Filter(name = "tenantFilter", condition = "client_id = :clientId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"lines", "client", "partnerCompany"})
public class PurchaseOrder extends BaseEntity implements TenantAware {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", insertable = false, updatable = false)
    private Client client;

    @Column(name = "partner_company_id", nullable = false)
    private UUID partnerCompanyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_company_id", insertable = false, updatable = false)
    private PartnerCompany partnerCompany;

    @Column(name = "order_number", nullable = false, length = 30)
    private String orderNumber;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "COMPLETED";

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "needs_retagging", nullable = false)
    @Builder.Default
    private Boolean needsRetagging = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    @Builder.Default
    private String metadata = "{}";

    @OneToMany(mappedBy = "purchaseOrder", fetch = FetchType.LAZY)
    @Builder.Default
    private List<PurchaseOrderLine> lines = new ArrayList<>();
}
