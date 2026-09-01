package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TrainingCourseRequest(
    @NotBlank String courseId,
    @NotBlank String courseName,
    String courseCategory,
    String courseProvider,
    String courseDuration,
    String courseLevel,
    boolean required
) {
}
