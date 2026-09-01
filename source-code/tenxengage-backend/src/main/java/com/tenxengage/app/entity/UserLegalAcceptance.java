package com.tenxengage.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "user_legal_acceptances")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLegalAcceptance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(name = "accepted_at", nullable = false)
    @Builder.Default
    private Instant acceptedAt = Instant.now();

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;
}
