package com.tenxengage.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "training_course_assignments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TrainingCourseAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incentive_id", nullable = false)
    private Incentive incentive;

    @Column(name = "course_id", nullable = false, length = 255)
    private String courseId;

    @Column(name = "course_name", nullable = false, length = 255)
    private String courseName;

    @Column(name = "course_category", length = 255)
    private String courseCategory;

    @Column(name = "course_provider", length = 255)
    private String courseProvider;

    @Column(name = "course_duration", length = 50)
    private String courseDuration;

    @Column(name = "course_level", length = 20)
    private String courseLevel;

    @Column(nullable = false)
    @Builder.Default
    private Boolean required = true;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
