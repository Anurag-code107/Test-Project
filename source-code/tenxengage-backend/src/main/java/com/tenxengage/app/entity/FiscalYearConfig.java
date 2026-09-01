package com.tenxengage.app.entity;

import com.tenxengage.app.entity.enums.QuarterMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "fiscal_year_configs")
@Filter(name = "tenantFilter", condition = "client_id = :clientId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class FiscalYearConfig extends BaseEntity implements TenantAware {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", insertable = false, updatable = false)
    private Client client;

    @Column(nullable = false, length = 20)
    private String label;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "quarter_method", nullable = false, length = 10)
    private QuarterMethod quarterMethod;

    @Column(name = "quarter_size")
    private Integer quarterSize;

    @Column(name = "q1_start_date", nullable = false)
    private LocalDate q1StartDate;

    @Column(name = "q1_end_date", nullable = false)
    private LocalDate q1EndDate;

    @Column(name = "q2_start_date", nullable = false)
    private LocalDate q2StartDate;

    @Column(name = "q2_end_date", nullable = false)
    private LocalDate q2EndDate;

    @Column(name = "q3_start_date", nullable = false)
    private LocalDate q3StartDate;

    @Column(name = "q3_end_date", nullable = false)
    private LocalDate q3EndDate;

    @Column(name = "q4_start_date", nullable = false)
    private LocalDate q4StartDate;

    @Column(name = "q4_end_date", nullable = false)
    private LocalDate q4EndDate;
}
