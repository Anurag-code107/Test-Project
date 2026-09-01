package com.tenxengage.app.entity;

import com.tenxengage.app.entity.enums.AuditAction;
import com.tenxengage.app.entity.enums.AuditActorType;
import com.tenxengage.app.entity.enums.AuditResourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 20)
    private AuditActorType actorType;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_email")
    private String actorEmail;

    @Column(name = "actor_name", length = 200)
    private String actorName;

    @Column(name = "user_type", length = 20)
    private String userType;

    @Column(name = "company_name")
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 50)
    private AuditResourceType resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "resource_name", length = 500)
    private String resourceName;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "request_id")
    private UUID requestId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
