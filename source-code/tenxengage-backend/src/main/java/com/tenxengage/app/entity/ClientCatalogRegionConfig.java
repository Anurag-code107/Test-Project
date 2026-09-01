package com.tenxengage.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

import java.util.UUID;

@Entity
@Table(name = "client_catalog_region_configs")
@Filter(name = "tenantFilter", condition = "client_id = :clientId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ClientCatalogRegionConfig extends BaseEntity implements TenantAware {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "redemption_catalog_item_id", nullable = false)
    private UUID redemptionCatalogItemId;

    @Column(name = "region_code", nullable = false, length = 10)
    private String regionCode;

    @Column(nullable = false)
    private boolean enabled;
}
