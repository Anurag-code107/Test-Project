package com.tenxengage.app.entity;

import com.tenxengage.app.entity.enums.DpaStatus;
import com.tenxengage.app.entity.enums.SccStatus;
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
@Table(name = "sub_processors")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubProcessor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 500)
    private String purpose;

    @Column(name = "data_processed", nullable = false, length = 500)
    private String dataProcessed;

    @Column(nullable = false, length = 100)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "dpa_status", nullable = false, length = 20)
    @Builder.Default
    private DpaStatus dpaStatus = DpaStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "scc_status", length = 20)
    @Builder.Default
    private SccStatus sccStatus = SccStatus.NOT_REQUIRED;

    @Column(name = "added_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant addedAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
