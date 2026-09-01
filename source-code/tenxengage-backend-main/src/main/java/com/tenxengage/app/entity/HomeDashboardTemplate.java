package com.tenxengage.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "home_dashboard_templates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class HomeDashboardTemplate extends BaseEntity {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * INTERNAL = assignable to internal roles (client admin, activity approver).
     * EXTERNAL = assignable to external roles (partner admin, partner seller).
     */
    @Column(name = "role_type", nullable = false, length = 20)
    private String roleType;

    /**
     * Rows-and-slots layout. Shape:
     * {"rows": [{"layout": "full"|"half-half", "slots": [{"widgetKey": "..."}, ...]}, ...]}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private String layout = "{\"rows\": []}";

    @Column(name = "is_system", nullable = false)
    @Builder.Default
    private boolean system = false;
}
