package com.tenxengage.app.entity;

import com.tenxengage.app.entity.enums.DataCategory;
import com.tenxengage.app.entity.enums.RetentionActionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.Filter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "retention_policies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Filter(name = "tenantFilter", condition = "client_id = :clientId")
public class RetentionPolicy extends BaseEntity implements TenantAware {

    @Column(name = "client_id")
    private UUID clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_category", nullable = false, length = 50)
    private DataCategory dataCategory;

    @Column(name = "retention_days", nullable = false)
    private int retentionDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    @Builder.Default
    private RetentionActionType actionType = RetentionActionType.ANONYMIZE;
}
