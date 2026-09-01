package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.TrainingCourseAssignment;

import java.util.UUID;

public record TrainingCourseResponse(
    UUID id,
    String courseId,
    String courseName,
    String courseCategory,
    String courseProvider,
    String courseDuration,
    String courseLevel,
    boolean required
) {

    public static TrainingCourseResponse from(TrainingCourseAssignment course) {
        return new TrainingCourseResponse(
            course.getId(),
            course.getCourseId(),
            course.getCourseName(),
            course.getCourseCategory(),
            course.getCourseProvider(),
            course.getCourseDuration(),
            course.getCourseLevel(),
            Boolean.TRUE.equals(course.getRequired())
        );
    }
}
