package com.tenxengage.app.entity;

import com.tenxengage.app.entity.enums.ConsentType;
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
@Table(name = "consent_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 50)
    private ConsentType consentType;

    @Column(nullable = false)
    private boolean granted;

    @Column(name = "recorded_at", nullable = false)
    @Builder.Default
    private Instant recordedAt = Instant.now();

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "consent_version", length = 20)
    private String consentVersion;
}
