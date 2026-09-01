package com.tenxengage.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

import java.util.UUID;

@Entity
@Table(name = "client_branding")
@Filter(name = "tenantFilter", condition = "client_id = :clientId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ClientBranding extends BaseEntity implements TenantAware {

    @Column(name = "client_id", nullable = false, unique = true)
    private UUID clientId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", insertable = false, updatable = false)
    private Client client;

    @Column(name = "primary_hsl", nullable = false, length = 32)
    private String primaryHsl;

    @Column(name = "primary_light_hsl", nullable = false, length = 32)
    private String primaryLightHsl;

    @Column(name = "secondary_hsl", nullable = false, length = 32)
    private String secondaryHsl;

    @Column(name = "accent_hsl", nullable = false, length = 32)
    private String accentHsl;

    @Column(name = "success_hsl", nullable = false, length = 32)
    private String successHsl;

    @Column(name = "warning_hsl", nullable = false, length = 32)
    private String warningHsl;

    @Column(name = "destructive_hsl", nullable = false, length = 32)
    private String destructiveHsl;

    @Column(name = "background_hsl", nullable = false, length = 32)
    private String backgroundHsl;

    @Column(name = "foreground_hsl", nullable = false, length = 32)
    private String foregroundHsl;

    @Column(name = "muted_hsl", nullable = false, length = 32)
    private String mutedHsl;

    @Column(name = "muted_foreground_hsl", nullable = false, length = 32)
    private String mutedForegroundHsl;

    @Column(name = "card_hsl", nullable = false, length = 32)
    private String cardHsl;

    @Column(name = "card_foreground_hsl", nullable = false, length = 32)
    private String cardForegroundHsl;

    @Column(name = "border_hsl", nullable = false, length = 32)
    private String borderHsl;

    @Column(name = "heading_font", nullable = false, length = 64)
    private String headingFont;

    @Column(name = "body_font", nullable = false, length = 64)
    private String bodyFont;

    @Column(name = "logo_url", length = 512)
    private String logoUrl;

    @Column(name = "logo_object_key", length = 512)
    private String logoObjectKey;
}
