package com.tenxengage.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "feature_flags")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class FeatureFlag extends BaseEntity {

    @Column(name = "feature_key", nullable = false, unique = true, length = 100)
    private String featureKey;

    @Column(length = 500)
    private String description;

    @Column(name = "starter_enabled", nullable = false)
    @Builder.Default
    private boolean starterEnabled = false;

    @Column(name = "professional_enabled", nullable = false)
    @Builder.Default
    private boolean professionalEnabled = false;

    @Column(name = "enterprise_enabled", nullable = false)
    @Builder.Default
    private boolean enterpriseEnabled = true;

    @Column(length = 50, nullable = false)
    @Builder.Default
    private String category = "general";
}
