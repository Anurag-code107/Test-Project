package com.tenxengage.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "course_product_mappings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CourseProductMapping extends BaseEntity {

    @Column(name = "course_id", nullable = false)
    private java.util.UUID courseId;

    @Column(name = "product_category", nullable = false, length = 100)
    private String productCategory;

    @Column(name = "relevance_score", nullable = false, precision = 3, scale = 2)
    private BigDecimal relevanceScore;
}
