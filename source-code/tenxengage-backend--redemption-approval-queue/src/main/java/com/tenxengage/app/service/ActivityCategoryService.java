package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.CreateActivityCategoryRequest;
import com.tenxengage.app.dto.request.UpdateActivityCategoryRequest;
import com.tenxengage.app.dto.response.ActivityCategoryResponse;
import com.tenxengage.app.entity.ActivityCategory;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.ActivityCategoryRepository;
import com.tenxengage.app.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ActivityCategoryService {

    private static final Logger log = LoggerFactory.getLogger(ActivityCategoryService.class);

    private final ActivityCategoryRepository activityCategoryRepository;

    public ActivityCategoryService(ActivityCategoryRepository activityCategoryRepository) {
        this.activityCategoryRepository = activityCategoryRepository;
    }

    @Transactional(readOnly = true)
    public List<ActivityCategoryResponse> getCategories() {
        UUID clientId = TenantContext.getClientId();
        log.debug("Fetching activity categories for client={}", clientId);
        return activityCategoryRepository.findByClientIdOrderBySortOrder(clientId).stream()
                .map(ActivityCategoryResponse::from)
                .toList();
    }

    @Transactional
    public ActivityCategoryResponse createCategory(CreateActivityCategoryRequest request) {
        UUID clientId = TenantContext.getClientId();

        if (activityCategoryRepository.existsByClientIdAndName(clientId, request.name())) {
            throw new IllegalStateException(
                    "An activity category with name '" + request.name() + "' already exists");
        }

        int sortOrder = activityCategoryRepository.findByClientIdOrderBySortOrder(clientId).size();

        ActivityCategory category = ActivityCategory.builder()
                .clientId(clientId)
                .name(request.name())
                .description(request.description())
                .sortOrder(sortOrder)
                .build();

        category = activityCategoryRepository.save(category);
        log.info("Created activity category '{}' (id={}) for client {}", category.getName(), category.getId(), clientId);
        return ActivityCategoryResponse.from(category);
    }

    @Transactional
    public ActivityCategoryResponse updateCategory(UUID id, UpdateActivityCategoryRequest request) {
        UUID clientId = TenantContext.getClientId();

        ActivityCategory category = activityCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ActivityCategory", "id", id));
        validateTenantOwnership(category.getClientId(), clientId, id);

        if (request.name() != null) {
            category.setName(request.name());
        }
        if (request.description() != null) {
            category.setDescription(request.description());
        }

        category = activityCategoryRepository.save(category);
        log.info("Updated activity category '{}' (id={}) for client {}", category.getName(), id, clientId);
        return ActivityCategoryResponse.from(category);
    }

    @Transactional
    public void deleteCategory(UUID id) {
        UUID clientId = TenantContext.getClientId();

        ActivityCategory category = activityCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ActivityCategory", "id", id));
        validateTenantOwnership(category.getClientId(), clientId, id);

        activityCategoryRepository.delete(category);
        log.info("Deleted activity category '{}' (id={}) for client {}", category.getName(), id, clientId);
    }

    private void validateTenantOwnership(UUID entityClientId, UUID currentClientId, UUID entityId) {
        if (!entityClientId.equals(currentClientId)) {
            throw new ResourceNotFoundException("ActivityCategory", "id", entityId);
        }
    }
}
