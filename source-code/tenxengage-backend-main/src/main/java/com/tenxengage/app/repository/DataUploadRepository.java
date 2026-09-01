package com.tenxengage.app.repository;

import com.tenxengage.app.entity.DataUpload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DataUploadRepository extends JpaRepository<DataUpload, UUID> {

    List<DataUpload> findByClientIdAndDataObjectIdOrderByCreatedAtDesc(UUID clientId, UUID dataObjectId);
}
