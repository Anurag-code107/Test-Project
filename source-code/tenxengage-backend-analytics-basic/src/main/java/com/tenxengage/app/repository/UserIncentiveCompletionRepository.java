package com.tenxengage.app.repository;

import com.tenxengage.app.entity.UserIncentiveCompletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserIncentiveCompletionRepository extends JpaRepository<UserIncentiveCompletion, UUID> {

    boolean existsByClientIdAndIncentiveIdAndUserId(UUID clientId, UUID incentiveId, UUID userId);

    List<UserIncentiveCompletion> findByClientIdAndUserId(UUID clientId, UUID userId);
}
