package com.tenxengage.app.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Filter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "builder_section_configs")
@Filter(name = "tenantFilter", condition = "client_id = :clientId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"fields"})
@ToString(exclude = {"fields"})
public class BuilderSectionConfig extends BaseEntity implements TenantAware {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "incentive_type", nullable = false, length = 20)
    private String incentiveType;

    @Column(name = "section_key", nullable = false, length = 50)
    private String sectionKey;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(length = 500)
    private String subtitle;

    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "is_locked")
    @Builder.Default
    private boolean isLocked = false;

    @Column(name = "is_visible")
    @Builder.Default
    private boolean isVisible = true;

    @OneToMany(mappedBy = "sectionConfig", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<BuilderFieldConfig> fields = new ArrayList<>();
}
