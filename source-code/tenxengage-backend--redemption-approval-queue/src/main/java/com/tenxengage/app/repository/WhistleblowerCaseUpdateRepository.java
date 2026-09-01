package com.tenxengage.app.repository;

import com.tenxengage.app.entity.WhistleblowerCaseUpdate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WhistleblowerCaseUpdateRepository extends JpaRepository<WhistleblowerCaseUpdate, UUID> {

    List<WhistleblowerCaseUpdate> findByReportIdOrderByCreatedAtDesc(UUID reportId);
}
