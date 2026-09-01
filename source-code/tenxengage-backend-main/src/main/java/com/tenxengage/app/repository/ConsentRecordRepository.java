package com.tenxengage.app.repository;

import com.tenxengage.app.entity.ConsentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, UUID> {

    @Query("""
        SELECT cr FROM ConsentRecord cr
        WHERE cr.userId = :userId
          AND cr.recordedAt = (
              SELECT MAX(cr2.recordedAt) FROM ConsentRecord cr2
              WHERE cr2.userId = cr.userId
                AND cr2.consentType = cr.consentType
          )
        """)
    List<ConsentRecord> findLatestByUserId(@Param("userId") UUID userId);

    List<ConsentRecord> findByUserId(UUID userId);
}
