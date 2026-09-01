package com.tenxengage.app.repository;

import com.tenxengage.app.entity.SubProcessor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SubProcessorRepository extends JpaRepository<SubProcessor, UUID> {
}
