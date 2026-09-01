package com.tenxengage.app.entity;

import com.tenxengage.app.entity.enums.DataCategory;
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

import java.util.UUID;

@Entity
@Table(name = "retention_policy_bounds")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetentionPolicyBound {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_category", nullable = false, unique = true, length = 50)
    private DataCategory dataCategory;

    @Column(name = "min_days", nullable = false)
    private int minDays;

    @Column(name = "max_days", nullable = false)
    private int maxDays;
}
