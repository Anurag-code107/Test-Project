package com.tenxengage.app.repository;

import com.tenxengage.app.entity.CourseProductMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourseProductMappingRepository extends JpaRepository<CourseProductMapping, UUID> {

    List<CourseProductMapping> findByCourseId(UUID courseId);

    List<CourseProductMapping> findByProductCategory(String productCategory);
}
