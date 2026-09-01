package com.tenxengage.app.repository;

import com.tenxengage.app.entity.LmsCourse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LmsCourseRepository extends JpaRepository<LmsCourse, UUID> {

    List<LmsCourse> findByCategoryOrderByName(String category);

    @Query("SELECT c FROM LmsCourse c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(c.description) LIKE LOWER(CONCAT('%', :search, '%')) ORDER BY c.category, c.name")
    List<LmsCourse> search(String search);

    @Query("SELECT c FROM LmsCourse c WHERE c.category = :category " +
           "AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(c.description) LIKE LOWER(CONCAT('%', :search, '%'))) ORDER BY c.name")
    List<LmsCourse> searchByCategory(String category, String search);

    @Query("SELECT DISTINCT c.category FROM LmsCourse c ORDER BY c.category")
    List<String> findDistinctCategories();
}
