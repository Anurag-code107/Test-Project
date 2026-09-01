package com.tenxengage.app.repository;

import com.tenxengage.app.entity.TaggingJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaggingJobRepository extends JpaRepository<TaggingJob, UUID> {

    List<TaggingJob> findByClientIdOrderByCreatedAtDesc(UUID clientId);
}
