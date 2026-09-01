package com.tenxengage.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "client_roles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ClientRole extends BaseEntity {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Optional informational field indicating the original system role this was based on.
     * Nullable for custom roles created from scratch. Stored as plain String (not enum).
     */
    @Column(name = "base_role_name", length = 50)
    private String baseRoleName;

    @Column(name = "is_system", nullable = false)
    @Builder.Default
    private boolean system = false;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean defaultRole = false;

    /**
     * INTERNAL = roles for client admin / activity approver users.
     * EXTERNAL = roles for partner admin / partner seller users.
     */
    @Column(name = "role_type", length = 20)
    @Builder.Default
    private String roleType = "INTERNAL";

    /**
     * Optional FK to a home dashboard template. NULL = fall back to default template for this role's roleType.
     */
    @Column(name = "home_dashboard_template_id")
    private UUID homeDashboardTemplateId;
}
