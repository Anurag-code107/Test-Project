package com.tenxengage.app.repository;

import com.tenxengage.app.entity.AuditLog;
import com.tenxengage.app.entity.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.clientId = :clientId
          AND (:userType IS NULL OR a.userType = :userType)
          AND (CAST(:action AS STRING) IS NULL OR a.action = :action)
          AND (CAST(:dateFrom AS TIMESTAMP) IS NULL OR a.createdAt >= :dateFrom)
          AND (CAST(:dateTo AS TIMESTAMP) IS NULL OR a.createdAt <= :dateTo)
          AND (:search IS NULL OR :search = ''
               OR LOWER(a.actorName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(a.actorEmail) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(a.companyName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(a.resourceName) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY a.createdAt DESC
        """)
    Page<AuditLog> findFiltered(
            @Param("clientId") UUID clientId,
            @Param("userType") String userType,
            @Param("action") AuditAction action,
            @Param("dateFrom") Instant dateFrom,
            @Param("dateTo") Instant dateTo,
            @Param("search") String search,
            Pageable pageable
    );

    @Modifying
    @Query("UPDATE AuditLog a SET a.actorEmail = '[anonymized]', a.actorName = '[anonymized]', " +
           "a.ipAddress = null WHERE a.actorId = :actorId")
    int anonymizeByActorId(@Param("actorId") UUID actorId);
}
