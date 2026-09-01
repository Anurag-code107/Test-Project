package com.tenxengage.app.repository;

import com.tenxengage.app.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findByClientIdOrderByCategoryAscNameAsc(UUID clientId);

    List<Product> findByClientIdAndCategoryOrderByName(UUID clientId, String category);

    @Query("SELECT p FROM Product p WHERE p.clientId = :clientId " +
           "AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY p.category, p.name")
    List<Product> searchByClientId(UUID clientId, String search);

    @Query("SELECT p FROM Product p WHERE p.clientId = :clientId AND p.category = :category " +
           "AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%'))) ORDER BY p.name")
    List<Product> searchByClientIdAndCategory(UUID clientId, String category, String search);

    @Query("SELECT DISTINCT p.category FROM Product p WHERE p.clientId = :clientId ORDER BY p.category")
    List<String> findDistinctCategoriesByClientId(UUID clientId);

    long countByClientIdAndCategory(UUID clientId, String category);

    boolean existsByClientIdAndName(UUID clientId, String name);

    @Query("SELECT p.name FROM Product p WHERE p.clientId = :clientId")
    Set<String> findAllNamesByClientId(UUID clientId);
}
