package com.tenxengage.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.dto.request.CreateBuilderFieldRequest;
import com.tenxengage.app.dto.request.UpdateBuilderFieldRequest;
import com.tenxengage.app.dto.request.UpdateSectionRequest;
import com.tenxengage.app.dto.response.BuilderConfigResponse;
import com.tenxengage.app.dto.response.BuilderFieldConfigResponse;
import com.tenxengage.app.dto.response.BuilderSectionConfigResponse;
import com.tenxengage.app.dto.response.FieldValueOption;
import com.tenxengage.app.entity.ActivityCategory;
import com.tenxengage.app.entity.BuilderFieldConfig;
import com.tenxengage.app.entity.BuilderSectionConfig;
import com.tenxengage.app.entity.ClientRole;
import com.tenxengage.app.entity.DataObjectField;
import com.tenxengage.app.entity.LocationLevel;
import com.tenxengage.app.entity.LocationValue;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.ActivityCategoryRepository;
import com.tenxengage.app.repository.BuilderFieldConfigRepository;
import com.tenxengage.app.repository.BuilderSectionConfigRepository;
import com.tenxengage.app.repository.ClientRoleRepository;
import com.tenxengage.app.repository.DataObjectFieldRepository;
import com.tenxengage.app.repository.LocationLevelRepository;
import com.tenxengage.app.repository.LocationValueRepository;
import com.tenxengage.app.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BuilderConfigService {

    private static final Logger log = LoggerFactory.getLogger(BuilderConfigService.class);

    private final BuilderSectionConfigRepository sectionConfigRepository;
    private final BuilderFieldConfigRepository fieldConfigRepository;
    private final DataObjectFieldRepository dataObjectFieldRepository;
    private final LocationValueRepository locationValueRepository;
    private final LocationLevelRepository locationLevelRepository;
    private final ClientRoleRepository clientRoleRepository;
    private final ActivityCategoryRepository activityCategoryRepository;
    private final ObjectMapper objectMapper;

    public BuilderConfigService(BuilderSectionConfigRepository sectionConfigRepository,
                                BuilderFieldConfigRepository fieldConfigRepository,
                                DataObjectFieldRepository dataObjectFieldRepository,
                                LocationValueRepository locationValueRepository,
                                LocationLevelRepository locationLevelRepository,
                                ClientRoleRepository clientRoleRepository,
                                ActivityCategoryRepository activityCategoryRepository,
                                ObjectMapper objectMapper) {
        this.sectionConfigRepository = sectionConfigRepository;
        this.fieldConfigRepository = fieldConfigRepository;
        this.dataObjectFieldRepository = dataObjectFieldRepository;
        this.locationValueRepository = locationValueRepository;
        this.locationLevelRepository = locationLevelRepository;
        this.clientRoleRepository = clientRoleRepository;
        this.activityCategoryRepository = activityCategoryRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public BuilderConfigResponse getBuilderConfig(String incentiveType) {
        UUID clientId = TenantContext.getClientId();
        log.debug("Fetching builder config for client={}, incentiveType={}", clientId, incentiveType);

        List<BuilderSectionConfig> sections = sectionConfigRepository
                .findByClientIdAndIncentiveTypeOrderBySortOrder(clientId, incentiveType);

        List<BuilderSectionConfigResponse> sectionResponses = sections.stream()
                .map(BuilderSectionConfigResponse::from)
                .toList();

        return new BuilderConfigResponse(incentiveType, sectionResponses);
    }

    @Transactional
    public BuilderSectionConfigResponse updateSection(UUID sectionId, UpdateSectionRequest request) {
        UUID clientId = TenantContext.getClientId();

        BuilderSectionConfig section = sectionConfigRepository.findById(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("BuilderSectionConfig", "id", sectionId));
        validateTenantOwnership(section.getClientId(), clientId, "BuilderSectionConfig", sectionId);

        if (request.displayName() != null) {
            section.setDisplayName(request.displayName());
        }
        if (request.subtitle() != null) {
            section.setSubtitle(request.subtitle());
        }

        BuilderSectionConfig saved = sectionConfigRepository.save(section);
        log.info("Updated section '{}' (id={}) for client {}", saved.getSectionKey(), sectionId, clientId);
        return BuilderSectionConfigResponse.from(saved);
    }

    @Transactional
    public BuilderFieldConfigResponse addField(UUID sectionId, CreateBuilderFieldRequest request) {
        UUID clientId = TenantContext.getClientId();

        BuilderSectionConfig section = sectionConfigRepository.findById(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("BuilderSectionConfig", "id", sectionId));
        validateTenantOwnership(section.getClientId(), clientId, "BuilderSectionConfig", sectionId);

        if (section.isLocked()) {
            throw new IllegalStateException(
                    "Cannot add fields to locked section '" + section.getSectionKey() + "'");
        }

        BuilderFieldConfig field = BuilderFieldConfig.builder()
                .fieldKey(request.fieldKey())
                .displayName(request.displayName())
                .fieldType(request.fieldType())
                .helperText(request.helperText())
                .isMandatory(request.isMandatory())
                .isSystem(false)
                .isEligibility(request.isEligibility())
                .valueSource(request.valueSource())
                .valueSourceConfig(request.valueSourceConfig())
                .supportsExcelUpload(request.supportsExcelUpload())
                .sectionConfig(section)
                .build();

        if (request.dataObjectFieldId() != null) {
            DataObjectField dataObjectField = dataObjectFieldRepository.findById(request.dataObjectFieldId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "DataObjectField", "id", request.dataObjectFieldId()));
            field.setDataObjectField(dataObjectField);
        }

        int maxSortOrder = section.getFields().stream()
                .mapToInt(BuilderFieldConfig::getSortOrder)
                .max()
                .orElse(-1);
        field.setSortOrder(maxSortOrder + 1);

        BuilderFieldConfig saved = fieldConfigRepository.save(field);
        log.info("Added field '{}' to section '{}' (id={}) for client {}",
                saved.getFieldKey(), section.getSectionKey(), sectionId, clientId);
        return BuilderFieldConfigResponse.from(saved);
    }

    @Transactional
    public BuilderFieldConfigResponse updateField(UUID fieldId, UpdateBuilderFieldRequest request) {
        UUID clientId = TenantContext.getClientId();

        BuilderFieldConfig field = fieldConfigRepository.findById(fieldId)
                .orElseThrow(() -> new ResourceNotFoundException("BuilderFieldConfig", "id", fieldId));
        validateTenantOwnership(field.getSectionConfig().getClientId(), clientId,
                "BuilderFieldConfig", fieldId);

        if (field.isSystem()) {
            // System fields only allow safe updates: displayName, helperText, isMandatory, isEligibility
            applySystemFieldUpdates(field, request);
        } else {
            applyAllFieldUpdates(field, request);
        }

        BuilderFieldConfig saved = fieldConfigRepository.save(field);
        log.info("Updated field '{}' (id={}) for client {}", saved.getFieldKey(), fieldId, clientId);
        return BuilderFieldConfigResponse.from(saved);
    }

    @Transactional
    public void removeField(UUID fieldId) {
        UUID clientId = TenantContext.getClientId();

        BuilderFieldConfig field = fieldConfigRepository.findById(fieldId)
                .orElseThrow(() -> new ResourceNotFoundException("BuilderFieldConfig", "id", fieldId));
        validateTenantOwnership(field.getSectionConfig().getClientId(), clientId,
                "BuilderFieldConfig", fieldId);

        if (field.isSystem()) {
            throw new IllegalStateException(
                    "Cannot remove system field '" + field.getFieldKey() + "'");
        }

        fieldConfigRepository.delete(field);
        log.info("Removed field '{}' (id={}) for client {}", field.getFieldKey(), fieldId, clientId);
    }

    @Transactional(readOnly = true)
    public List<FieldValueOption> resolveFieldValues(UUID fieldId, Map<String, String[]> context) {
        UUID clientId = TenantContext.getClientId();

        BuilderFieldConfig field = fieldConfigRepository.findById(fieldId)
                .orElseThrow(() -> new ResourceNotFoundException("BuilderFieldConfig", "id", fieldId));
        validateTenantOwnership(field.getSectionConfig().getClientId(), clientId,
                "BuilderFieldConfig", fieldId);

        String valueSource = field.getValueSource();
        if (valueSource == null) {
            return List.of();
        }

        return switch (valueSource) {
            case "LOCATION_HIERARCHY" -> resolveLocationHierarchyValues(field, context, clientId);
            case "CLIENT_ROLES" -> resolveClientRoleValues(field, clientId);
            case "DATA_OBJECT_FIELD" -> resolveDataObjectFieldValues(field);
            case "ACTIVITY_CATEGORIES" -> resolveActivityCategoryValues(clientId);
            case "STATIC" -> resolveStaticValues(field);
            default -> {
                log.warn("Unknown value source '{}' for field {}", valueSource, fieldId);
                yield List.of();
            }
        };
    }

    // ---- Private helpers ----

    private void validateTenantOwnership(UUID entityClientId, UUID currentClientId,
                                         String entityName, UUID entityId) {
        if (!entityClientId.equals(currentClientId)) {
            throw new ResourceNotFoundException(entityName, "id", entityId);
        }
    }

    private void applySystemFieldUpdates(BuilderFieldConfig field, UpdateBuilderFieldRequest request) {
        if (request.displayName() != null) {
            field.setDisplayName(request.displayName());
        }
        if (request.helperText() != null) {
            field.setHelperText(request.helperText());
        }
        if (request.isMandatory() != null) {
            field.setMandatory(request.isMandatory());
        }
        if (request.isEligibility() != null) {
            field.setEligibility(request.isEligibility());
        }
    }

    private void applyAllFieldUpdates(BuilderFieldConfig field, UpdateBuilderFieldRequest request) {
        if (request.displayName() != null) {
            field.setDisplayName(request.displayName());
        }
        if (request.helperText() != null) {
            field.setHelperText(request.helperText());
        }
        if (request.isMandatory() != null) {
            field.setMandatory(request.isMandatory());
        }
        if (request.isEligibility() != null) {
            field.setEligibility(request.isEligibility());
        }
        if (request.dataObjectFieldId() != null) {
            DataObjectField dataObjectField = dataObjectFieldRepository.findById(request.dataObjectFieldId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "DataObjectField", "id", request.dataObjectFieldId()));
            field.setDataObjectField(dataObjectField);
        }
        if (request.valueSource() != null) {
            field.setValueSource(request.valueSource());
        }
        if (request.valueSourceConfig() != null) {
            field.setValueSourceConfig(request.valueSourceConfig());
        }
        if (request.supportsExcelUpload() != null) {
            field.setSupportsExcelUpload(request.supportsExcelUpload());
        }
    }

    private List<FieldValueOption> resolveLocationHierarchyValues(BuilderFieldConfig field,
                                                                  Map<String, String[]> context,
                                                                  UUID clientId) {
        JsonNode config = parseValueSourceConfig(field);
        if (config == null) {
            return List.of();
        }

        if (config.has("parentField")) {
            String parentFieldKey = config.get("parentField").asText();
            String[] parentValues = context != null ? context.get(parentFieldKey) : null;
            if (parentValues == null || parentValues.length == 0) {
                return List.of();
            }

            // Resolve children for each selected parent value
            List<FieldValueOption> options = new ArrayList<>();
            for (String parentIdStr : parentValues) {
                UUID parentId = UUID.fromString(parentIdStr);
                LocationValue parentValue = locationValueRepository.findById(parentId).orElse(null);
                if (parentValue != null) {
                    parentValue.getChildren().forEach(child ->
                            options.add(new FieldValueOption(child.getId().toString(), child.getName())));
                }
            }
            return options;
        }

        if (config.has("depth")) {
            int depth = config.get("depth").asInt();
            List<LocationLevel> levels = locationLevelRepository.findByClientIdOrderByDepthAsc(clientId);
            LocationLevel targetLevel = levels.stream()
                    .filter(l -> l.getDepth() == depth)
                    .findFirst()
                    .orElse(null);
            if (targetLevel == null) {
                log.warn("No location level found at depth {} for client {}", depth, clientId);
                return List.of();
            }

            List<LocationValue> values = locationValueRepository
                    .findByClientIdAndLevelIdAndParentIdIsNullOrderByName(clientId, targetLevel.getId());
            return values.stream()
                    .map(v -> new FieldValueOption(v.getId().toString(), v.getName()))
                    .toList();
        }

        return List.of();
    }

    private List<FieldValueOption> resolveClientRoleValues(BuilderFieldConfig field, UUID clientId) {
        JsonNode config = parseValueSourceConfig(field);

        // TODO: ClientRoleRepository does not yet have findByClientIdAndRoleTypeOrderByNameAsc.
        //  For now, fetch all roles and filter in memory. Add repository method when needed.
        List<ClientRole> roles = clientRoleRepository.findByClientIdOrderByNameAsc(clientId);

        if (config != null && config.has("roleType")) {
            String roleType = config.get("roleType").asText();
            roles = roles.stream()
                    .filter(r -> roleType.equals(r.getRoleType()))
                    .toList();
        }

        return roles.stream()
                .map(role -> new FieldValueOption(role.getName(), role.getName()))
                .toList();
    }

    private List<FieldValueOption> resolveDataObjectFieldValues(BuilderFieldConfig field) {
        if (field.getDataObjectField() == null) {
            return List.of();
        }

        String sampleValuesJson = field.getDataObjectField().getSampleValues();
        if (sampleValuesJson == null || sampleValuesJson.isBlank()) {
            return List.of();
        }

        try {
            List<String> values = objectMapper.readValue(sampleValuesJson, new TypeReference<>() {});
            return values.stream()
                    .map(v -> new FieldValueOption(v, v))
                    .toList();
        } catch (JsonProcessingException e) {
            log.error("Failed to parse sampleValues JSON for DataObjectField {}",
                    field.getDataObjectField().getId(), e);
            return List.of();
        }
    }

    private List<FieldValueOption> resolveActivityCategoryValues(UUID clientId) {
        List<ActivityCategory> categories = activityCategoryRepository
                .findByClientIdOrderBySortOrder(clientId);
        return categories.stream()
                .map(cat -> new FieldValueOption(cat.getName(), cat.getName()))
                .toList();
    }

    private List<FieldValueOption> resolveStaticValues(BuilderFieldConfig field) {
        JsonNode config = parseValueSourceConfig(field);
        if (config == null || !config.has("values")) {
            return List.of();
        }

        JsonNode valuesNode = config.get("values");
        if (!valuesNode.isArray()) {
            return List.of();
        }

        List<FieldValueOption> options = new ArrayList<>();
        for (JsonNode node : valuesNode) {
            if (node.isObject() && node.has("value") && node.has("label")) {
                options.add(new FieldValueOption(
                        node.get("value").asText(),
                        node.get("label").asText()));
            } else if (node.isTextual()) {
                String text = node.asText();
                options.add(new FieldValueOption(text, text));
            }
        }
        return options;
    }

    private JsonNode parseValueSourceConfig(BuilderFieldConfig field) {
        String configJson = field.getValueSourceConfig();
        if (configJson == null || configJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(configJson);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse valueSourceConfig JSON for field {}", field.getId(), e);
            return null;
        }
    }
}
