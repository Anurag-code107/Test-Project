package com.tenxengage.app.entity;

import com.tenxengage.app.entity.enums.BreachSeverity;
import com.tenxengage.app.entity.enums.BreachStatus;
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
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "breach_incidents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BreachIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BreachSeverity severity;

    @Column(name = "data_affected", columnDefinition = "TEXT")
    private String dataAffected;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "reported_to_authority_at")
    private Instant reportedToAuthorityAt;

    @Column(name = "individuals_notified_at")
    private Instant individualsNotifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BreachStatus status = BreachStatus.DETECTED;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
