package com.tenxengage.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "government_segment_config", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"client_id", "segment_value"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GovernmentSegmentConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "segment_value", nullable = false, length = 100)
    private String segmentValue;

    @Column(name = "is_government", nullable = false)
    @Builder.Default
    private boolean isGovernment = true;
}
