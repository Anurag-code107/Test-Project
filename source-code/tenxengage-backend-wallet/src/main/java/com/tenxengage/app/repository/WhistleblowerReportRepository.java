package com.tenxengage.app.repository;

import com.tenxengage.app.entity.WhistleblowerReport;
import com.tenxengage.app.entity.enums.WhistleblowerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WhistleblowerReportRepository extends JpaRepository<WhistleblowerReport, UUID> {

    Optional<WhistleblowerReport> findByTrackingNumber(String trackingNumber);

    List<WhistleblowerReport> findByStatus(WhistleblowerStatus status);

    List<WhistleblowerReport> findByClientId(UUID clientId);

    List<WhistleblowerReport> findByStatusIn(List<WhistleblowerStatus> statuses);
}
