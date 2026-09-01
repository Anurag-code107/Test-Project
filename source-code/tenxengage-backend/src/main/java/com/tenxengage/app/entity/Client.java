package com.tenxengage.app.entity;

import com.tenxengage.app.entity.enums.ClientStatus;
import com.tenxengage.app.entity.enums.SubscriptionTier;
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

import java.time.Instant;

@Entity
@Table(name = "clients")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Client extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, unique = true, length = 63)
    private String subdomain;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ClientStatus status = ClientStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_tier", nullable = false, length = 20)
    private SubscriptionTier subscriptionTier;

    @Column(name = "notification_retention_days", nullable = false)
    @Builder.Default
    private Integer notificationRetentionDays = 90;

    @Column(name = "dpa_signed_at")
    private Instant dpaSignedAt;

    @Column(name = "dpa_version", length = 20)
    private String dpaVersion;

    @Column(name = "dpa_signed_by", length = 255)
    private String dpaSignedBy;

    @Column(name = "data_region", length = 10)
    private String dataRegion;
}
