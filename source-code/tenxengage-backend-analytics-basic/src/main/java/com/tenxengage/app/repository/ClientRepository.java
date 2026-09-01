package com.tenxengage.app.repository;

import com.tenxengage.app.entity.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientRepository extends JpaRepository<Client, UUID> {

    Optional<Client> findBySubdomain(String subdomain);

    boolean existsBySubdomain(String subdomain);

    @Query("""
        SELECT c FROM Client c
        WHERE (:search IS NULL OR :search = ''
            OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(c.subdomain) LIKE LOWER(CONCAT('%', :search, '%')))
        """)
    Page<Client> searchClients(@Param("search") String search, Pageable pageable);

    @Query("SELECT c.status, COUNT(c) FROM Client c GROUP BY c.status")
    List<Object[]> countByStatusGrouped();

    @Query("SELECT c.subscriptionTier, COUNT(c) FROM Client c GROUP BY c.subscriptionTier")
    List<Object[]> countByTierGrouped();
}
