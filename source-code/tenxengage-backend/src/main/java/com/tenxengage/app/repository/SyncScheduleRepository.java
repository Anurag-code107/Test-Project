package com.tenxengage.app.repository;

import com.tenxengage.app.entity.SyncSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SyncScheduleRepository extends JpaRepository<SyncSchedule, UUID> {

    Optional<SyncSchedule> findByClientIdAndDataObjectId(UUID clientId, UUID dataObjectId);
}
