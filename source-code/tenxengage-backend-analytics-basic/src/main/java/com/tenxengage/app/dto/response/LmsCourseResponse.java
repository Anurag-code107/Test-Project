package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.LmsCourse;

import java.util.UUID;

public record LmsCourseResponse(
    UUID id,
    String externalCourseId,
    String name,
    String description,
    String category
) {
    public static LmsCourseResponse from(LmsCourse course) {
        return new LmsCourseResponse(
            course.getId(),
            course.getExternalCourseId(),
            course.getName(),
            course.getDescription(),
            course.getCategory()
        );
    }
}
