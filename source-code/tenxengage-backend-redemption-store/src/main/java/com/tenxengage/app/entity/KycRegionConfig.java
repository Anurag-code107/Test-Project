package com.tenxengage.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "kyc_region_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class KycRegionConfig extends BaseEntity {

    @Column(name = "region_code", nullable = false, unique = true, length = 20)
    private String regionCode;

    @Column(name = "tier1_required", nullable = false)
    @Builder.Default
    private boolean tier1Required = false;

    @Column(name = "tier2_required", nullable = false)
    @Builder.Default
    private boolean tier2Required = false;
}
