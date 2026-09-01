package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.CreateLocationLevelRequest;
import com.tenxengage.app.dto.request.CreateLocationValueRequest;
import com.tenxengage.app.dto.request.UpdateLocationLevelRequest;
import com.tenxengage.app.dto.request.UpdateLocationLevelSettingsRequest;
import com.tenxengage.app.dto.request.UpdateLocationValueRequest;
import com.tenxengage.app.dto.response.LocationFilterOptionsResponse;
import com.tenxengage.app.dto.response.LocationHierarchyResponse;
import com.tenxengage.app.dto.response.LocationLevelResponse;
import com.tenxengage.app.dto.response.LocationValueResponse;
import com.tenxengage.app.entity.LocationLevel;
import com.tenxengage.app.entity.LocationValue;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.LocationLevelRepository;
import com.tenxengage.app.repository.LocationValueRepository;
import com.tenxengage.app.security.TenantValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class LocationService {

    private static final Logger log = LoggerFactory.getLogger(LocationService.class);

    private final LocationLevelRepository levelRepository;
    private final LocationValueRepository valueRepository;
    private final TenantValidator tenantValidator;

    public LocationService(LocationLevelRepository levelRepository,
                           LocationValueRepository valueRepository,
                           TenantValidator tenantValidator) {
        this.levelRepository = levelRepository;
        this.valueRepository = valueRepository;
        this.tenantValidator = tenantValidator;
    }

    @Transactional(readOnly = true)
    public LocationHierarchyResponse getHierarchy() {
        UUID clientId = tenantValidator.getCurrentClientId();

        List<LocationLevel> levels = levelRepository.findByClientIdOrderByDepthAsc(clientId);

        List<LocationLevelResponse> levelResponses = levels.stream()
            .map(LocationLevelResponse::from)
            .toList();

        // Build tree from root values (depth 0 level, parent is null)
        List<LocationValueResponse> tree = List.of();
        if (!levels.isEmpty()) {
            LocationLevel rootLevel = levels.get(0);
            List<LocationValue> rootValues = valueRepository
                .findByClientIdAndLevelIdAndParentIdIsNullOrderByName(clientId, rootLevel.getId());
            tree = rootValues.stream()
                .map(LocationValueResponse::from)
                .toList();
        }

        return new LocationHierarchyResponse(levelResponses, tree);
    }

    @Transactional
    public LocationLevelResponse createLevel(CreateLocationLevelRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();

        if (levelRepository.existsByClientIdAndName(clientId, request.name().trim())) {
            throw new IllegalArgumentException("A level with name '" + request.name().trim() + "' already exists");
        }

        List<LocationLevel> existing = levelRepository.findByClientIdOrderByDepthAsc(clientId);
        int nextDepth = existing.isEmpty() ? 0 : existing.get(existing.size() - 1).getDepth() + 1;

        LocationLevel level = LocationLevel.builder()
            .clientId(clientId)
            .name(request.name().trim())
            .depth(nextDepth)
            .isRequired(nextDepth == 0)
            .build();

        level = levelRepository.save(level);
        log.info("Created location level '{}' at depth {} for client {}", level.getName(), level.getDepth(), clientId);

        return LocationLevelResponse.from(level);
    }

    @Transactional
    public LocationLevelResponse updateLevel(UUID levelId, UpdateLocationLevelRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();

        LocationLevel level = levelRepository.findById(levelId)
            .orElseThrow(() -> new ResourceNotFoundException("LocationLevel", "id", levelId));
        tenantValidator.validateClientAccess(level.getClientId());

        String newName = request.name().trim();
        if (!level.getName().equals(newName) && levelRepository.existsByClientIdAndName(clientId, newName)) {
            throw new IllegalArgumentException("A level with name '" + newName + "' already exists");
        }

        level.setName(newName);
        level = levelRepository.save(level);
        log.info("Updated location level {} to name '{}' for client {}", levelId, level.getName(), clientId);

        return LocationLevelResponse.from(level);
    }

    @Transactional
    public void deleteLevel(UUID levelId) {
        UUID clientId = tenantValidator.getCurrentClientId();

        LocationLevel level = levelRepository.findById(levelId)
            .orElseThrow(() -> new ResourceNotFoundException("LocationLevel", "id", levelId));
        tenantValidator.validateClientAccess(level.getClientId());

        List<LocationLevel> ordered = levelRepository.findByClientIdOrderByDepthAsc(clientId);
        int maxDepth = ordered.get(ordered.size() - 1).getDepth();
        if (level.getDepth() < maxDepth) {
            throw new IllegalArgumentException(
                "Delete the level below this one first; levels must be removed from the bottom up");
        }

        int deletedDepth = level.getDepth();
        levelRepository.delete(level);

        // Reindex depths for levels after the deleted one
        List<LocationLevel> remaining = levelRepository.findByClientIdOrderByDepthAsc(clientId);
        int depth = 0;
        for (LocationLevel l : remaining) {
            if (l.getDepth() != depth) {
                l.setDepth(depth);
                levelRepository.save(l);
            }
            depth++;
        }

        log.info("Deleted location level {} (depth {}) and reindexed for client {}", levelId, deletedDepth, clientId);
    }

    @Transactional
    public LocationValueResponse createValue(CreateLocationValueRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();

        LocationLevel level = levelRepository.findById(request.levelId())
            .orElseThrow(() -> new ResourceNotFoundException("LocationLevel", "id", request.levelId()));
        tenantValidator.validateClientAccess(level.getClientId());

        LocationValue parent = null;
        if (request.parentId() != null) {
            parent = valueRepository.findById(request.parentId())
                .orElseThrow(() -> new ResourceNotFoundException("LocationValue", "id", request.parentId()));
            tenantValidator.validateClientAccess(parent.getClientId());
        }

        // Duplicate check
        if (valueRepository.existsByClientIdAndLevelIdAndParentIdAndName(
                clientId, request.levelId(), request.parentId(), request.name().trim())) {
            throw new IllegalArgumentException(
                "A value with name '" + request.name().trim() + "' already exists at this level and parent");
        }

        LocationValue value = LocationValue.builder()
            .clientId(clientId)
            .level(level)
            .parent(parent)
            .name(request.name().trim())
            .code(request.code() != null ? request.code().trim() : null)
            .build();

        value = valueRepository.save(value);
        log.info("Created location value '{}' under level '{}' for client {}",
            value.getName(), level.getName(), clientId);

        return LocationValueResponse.from(value);
    }

    @Transactional
    public LocationValueResponse updateValue(UUID valueId, UpdateLocationValueRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();

        LocationValue value = valueRepository.findById(valueId)
            .orElseThrow(() -> new ResourceNotFoundException("LocationValue", "id", valueId));
        tenantValidator.validateClientAccess(value.getClientId());

        value.setName(request.name().trim());
        value.setCode(request.code() != null ? request.code().trim() : null);

        value = valueRepository.save(value);
        log.info("Updated location value {} to name '{}' for client {}", valueId, value.getName(), clientId);

        return LocationValueResponse.from(value);
    }

    @Transactional
    public void deleteValue(UUID valueId) {
        LocationValue value = valueRepository.findById(valueId)
            .orElseThrow(() -> new ResourceNotFoundException("LocationValue", "id", valueId));
        tenantValidator.validateClientAccess(value.getClientId());

        valueRepository.delete(value);
        log.info("Deleted location value {} ('{}') for client {}", valueId, value.getName(), value.getClientId());
    }

    @Transactional
    public LocationLevelResponse updateLevelSettings(UUID levelId, UpdateLocationLevelSettingsRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();

        LocationLevel level = levelRepository.findById(levelId)
            .orElseThrow(() -> new ResourceNotFoundException("LocationLevel", "id", levelId));
        tenantValidator.validateClientAccess(level.getClientId());

        if (request.useInBuilder() != null) {
            level.setUseInBuilder(request.useInBuilder());
        }
        if (request.useInFilters() != null) {
            level.setUseInFilters(request.useInFilters());
        }
        if (request.isRequired() != null) {
            if (!request.isRequired() && level.getDepth() == 0) {
                throw new IllegalArgumentException("Top-level location cannot be optional");
            }
            level.setRequired(request.isRequired());
        }

        level = levelRepository.save(level);
        log.info("Updated location level {} settings (builder={}, filters={}, required={}) for client {}",
            levelId, level.isUseInBuilder(), level.isUseInFilters(), level.isRequired(), clientId);

        return LocationLevelResponse.from(level);
    }

    @Transactional(readOnly = true)
    public List<LocationLevelResponse> getBuilderEnabledLevels() {
        UUID clientId = tenantValidator.getCurrentClientId();
        return levelRepository.findByClientIdAndUseInBuilderTrueOrderByDepthAsc(clientId).stream()
            .map(LocationLevelResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public LocationFilterOptionsResponse getFilterOptions() {
        UUID clientId = tenantValidator.getCurrentClientId();
        List<LocationLevel> levels = levelRepository.findByClientIdAndUseInFiltersTrueOrderByDepthAsc(clientId);
        return buildOptionsResponse(clientId, levels);
    }

    /**
     * Returns the same levels-with-nested-values shape as {@link #getFilterOptions()}
     * but filtered by {@code useInBuilder=true}. Distinct from
     * {@link #getBuilderEnabledLevels()} which returns level metadata only.
     *
     * Used by the AI copilot's system-prompt assembly so the injected LOCATION
     * HIERARCHY block matches exactly which levels Step 2 Participant Eligibility
     * actually renders — a level configured {@code useInBuilder=true,
     * useInFilters=false} was previously invisible to the AI because the prompt
     * read from {@link #getFilterOptions()}.
     */
    @Transactional(readOnly = true)
    public LocationFilterOptionsResponse getBuilderOptions() {
        UUID clientId = tenantValidator.getCurrentClientId();
        List<LocationLevel> levels = levelRepository.findByClientIdAndUseInBuilderTrueOrderByDepthAsc(clientId);
        return buildOptionsResponse(clientId, levels);
    }

    private LocationFilterOptionsResponse buildOptionsResponse(UUID clientId, List<LocationLevel> levels) {
        List<LocationFilterOptionsResponse.LocationFilterLevel> filterLevels = levels.stream()
            .map(level -> {
                List<LocationFilterOptionsResponse.LocationFilterValue> values =
                    valueRepository.findByClientIdAndLevelId(clientId, level.getId()).stream()
                        .map(v -> new LocationFilterOptionsResponse.LocationFilterValue(
                            v.getId(),
                            v.getName(),
                            v.getCode(),
                            v.getParent() != null ? v.getParent().getId() : null
                        ))
                        .toList();
                return new LocationFilterOptionsResponse.LocationFilterLevel(
                    level.getId(), level.getName(), level.getDepth(), values);
            })
            .toList();

        return new LocationFilterOptionsResponse(filterLevels);
    }
}
