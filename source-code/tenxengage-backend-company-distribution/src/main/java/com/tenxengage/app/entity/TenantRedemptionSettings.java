package com.tenxengage.app.entity;

import com.tenxengage.app.entity.enums.BatchCadence;
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

import java.util.UUID;

@Entity
@Table(name = "tenant_redemption_settings")
@Filter(name = "tenantFilter", condition = "client_id = :clientId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TenantRedemptionSettings extends BaseEntity implements TenantAware {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "batch_cadence", nullable = false, length = 20)
    @Builder.Default
    private BatchCadence batchCadence = BatchCadence.DAILY;

    @Column(name = "max_in_flight_redemptions", nullable = false)
    @Builder.Default
    private int maxInFlightRedemptions = 10;
}
