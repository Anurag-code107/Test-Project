package com.tenxengage.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.dto.request.ConnectorMappingRequest;
import com.tenxengage.app.dto.request.CreateDataObjectRequest;
import com.tenxengage.app.dto.request.CreateFieldRequest;
import com.tenxengage.app.dto.request.UpdateDataObjectRequest;
import com.tenxengage.app.dto.request.UpdateFieldRequest;
import com.tenxengage.app.dto.response.ConnectorMappingDetailResponse;
import com.tenxengage.app.dto.response.DataObjectDetailResponse;
import com.tenxengage.app.dto.response.DataObjectFieldResponse;
import com.tenxengage.app.dto.response.DataObjectResponse;
import com.tenxengage.app.dto.response.RuleFieldResponse;
import com.tenxengage.app.entity.Connector;
import com.tenxengage.app.entity.ConnectorFieldMapping;
import com.tenxengage.app.entity.DataObject;
import com.tenxengage.app.entity.DataObjectField;
import com.tenxengage.app.entity.LocationLevel;
import com.tenxengage.app.entity.LocationValue;
import com.tenxengage.app.repository.ConnectorFieldMappingRepository;
import com.tenxengage.app.repository.ConnectorRepository;
import com.tenxengage.app.repository.DataObjectFieldRepository;
import com.tenxengage.app.repository.DataObjectRepository;
import com.tenxengage.app.repository.LocationLevelRepository;
import com.tenxengage.app.repository.LocationValueRepository;
import com.tenxengage.app.security.TenantValidator;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DataObjectService {

    private final DataObjectRepository dataObjectRepository;
    private final DataObjectFieldRepository fieldRepository;
    private final ConnectorFieldMappingRepository mappingRepository;
    private final ConnectorRepository connectorRepository;
    private final LocationLevelRepository locationLevelRepository;
    private final LocationValueRepository locationValueRepository;
    private final ObjectMapper objectMapper;
    private final TenantValidator tenantValidator;

    public DataObjectService(DataObjectRepository dataObjectRepository,
                            DataObjectFieldRepository fieldRepository,
                            ConnectorFieldMappingRepository mappingRepository,
                            ConnectorRepository connectorRepository,
                            LocationLevelRepository locationLevelRepository,
                            LocationValueRepository locationValueRepository,
                            ObjectMapper objectMapper,
                            TenantValidator tenantValidator) {
        this.dataObjectRepository = dataObjectRepository;
        this.fieldRepository = fieldRepository;
        this.mappingRepository = mappingRepository;
        this.connectorRepository = connectorRepository;
        this.locationLevelRepository = locationLevelRepository;
        this.locationValueRepository = locationValueRepository;
        this.objectMapper = objectMapper;
        this.tenantValidator = tenantValidator;
    }

    // --- Data Object CRUD ---

    @Transactional(readOnly = true)
    public List<DataObjectResponse> getDataObjects() {
        UUID clientId = tenantValidator.getCurrentClientId();
        List<DataObject> objects = dataObjectRepository.findByClientIdOrderBySortOrder(clientId);
        return objects.stream().map(obj -> {
            String connectorName = resolveConnectorName(obj);
            return DataObjectResponse.from(obj, connectorName);
        }).toList();
    }

    @Transactional(readOnly = true)
    public DataObjectDetailResponse getDataObject(UUID id) {
        DataObject obj = findObjectAndValidate(id);
        List<DataObjectFieldResponse> fields = obj.getFields().stream()
                .map(DataObjectFieldResponse::from)
                .toList();

        // For Partner Data, inject virtual fields from the location hierarchy
        if ("Partner Data".equals(obj.getName())) {
            fields = injectLocationHierarchyFields(fields);
        }

        ConnectorMappingDetailResponse mapping = buildMappingResponse(obj);
        return DataObjectDetailResponse.from(obj, fields, mapping);
    }

    @Transactional
    public DataObjectDetailResponse createDataObject(CreateDataObjectRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();
        if (dataObjectRepository.existsByClientIdAndName(clientId, request.name())) {
            throw new IllegalArgumentException("A data object with name '" + request.name() + "' already exists");
        }

        DataObject obj = DataObject.builder()
                .clientId(clientId)
                .name(request.name())
                .description(request.description())
                .build();
        obj = dataObjectRepository.save(obj);
        return DataObjectDetailResponse.from(obj, List.of(), null);
    }

    @Transactional
    public DataObjectDetailResponse updateDataObject(UUID id, UpdateDataObjectRequest request) {
        DataObject obj = findObjectAndValidate(id);
        if (request.name() != null) {
            obj.setName(request.name());
        }
        if (request.description() != null) {
            obj.setDescription(request.description());
        }
        obj = dataObjectRepository.save(obj);
        List<DataObjectFieldResponse> fields = obj.getFields().stream()
                .map(DataObjectFieldResponse::from)
                .toList();
        return DataObjectDetailResponse.from(obj, fields, buildMappingResponse(obj));
    }

    @Transactional
    public void deleteDataObject(UUID id) {
        DataObject obj = findObjectAndValidate(id);
        if (obj.isDefault()) {
            throw new IllegalArgumentException("Cannot delete a default data object");
        }
        dataObjectRepository.delete(obj);
    }

    // --- Field CRUD ---

    @Transactional
    public DataObjectFieldResponse addField(UUID dataObjectId, CreateFieldRequest request) {
        DataObject obj = findObjectAndValidate(dataObjectId);
        if (fieldRepository.existsByDataObjectIdAndName(dataObjectId, request.name())) {
            throw new IllegalArgumentException("A field with name '" + request.name() + "' already exists");
        }

        int nextSort = obj.getFields().size();
        DataObjectField field = DataObjectField.builder()
                .dataObject(obj)
                .name(request.name())
                .description(request.description())
                .dataType(request.dataType())
                .ruleLabel(request.ruleLabel())
                .excludeFromRules(request.excludeFromRules() != null ? request.excludeFromRules() : false)
                .sampleValues(serializeSampleValues(request.sampleValues()))
                .visibleOnProfile(request.visibleOnProfile() != null ? request.visibleOnProfile() : false)
                .editableByUser(request.editableByUser() != null ? request.editableByUser() : false)
                .sortOrder(nextSort)
                .build();
        field = fieldRepository.save(field);
        return DataObjectFieldResponse.from(field);
    }

    @Transactional
    public DataObjectFieldResponse updateField(UUID dataObjectId, UUID fieldId, UpdateFieldRequest request) {
        findObjectAndValidate(dataObjectId);
        DataObjectField field = fieldRepository.findById(fieldId)
                .orElseThrow(() -> new EntityNotFoundException("Field not found: " + fieldId));
        if (!field.getDataObject().getId().equals(dataObjectId)) {
            throw new EntityNotFoundException("Field not found: " + fieldId);
        }

        if (request.name() != null) field.setName(request.name());
        if (request.description() != null) field.setDescription(request.description());
        if (request.dataType() != null) field.setDataType(request.dataType());
        if (request.ruleLabel() != null) field.setRuleLabel(request.ruleLabel());
        if (request.excludeFromRules() != null) field.setExcludeFromRules(request.excludeFromRules());
        if (request.sampleValues() != null) field.setSampleValues(serializeSampleValues(request.sampleValues()));
        if (request.visibleOnProfile() != null) field.setVisibleOnProfile(request.visibleOnProfile());
        if (request.editableByUser() != null) field.setEditableByUser(request.editableByUser());

        field = fieldRepository.save(field);
        return DataObjectFieldResponse.from(field);
    }

    @Transactional
    public void deleteField(UUID dataObjectId, UUID fieldId) {
        findObjectAndValidate(dataObjectId);
        DataObjectField field = fieldRepository.findById(fieldId)
                .orElseThrow(() -> new EntityNotFoundException("Field not found: " + fieldId));
        if (!field.getDataObject().getId().equals(dataObjectId)) {
            throw new EntityNotFoundException("Field not found: " + fieldId);
        }
        if (field.isMandatory()) {
            throw new IllegalStateException("Cannot delete mandatory field: " + field.getName());
        }
        fieldRepository.delete(field);
    }

    // --- Connector Mapping ---

    @Transactional
    public void setConnectorMapping(UUID dataObjectId, ConnectorMappingRequest request) {
        DataObject obj = findObjectAndValidate(dataObjectId);
        Connector connector = connectorRepository.findById(request.connectorId())
                .orElseThrow(() -> new EntityNotFoundException("Connector not found: " + request.connectorId()));

        // Remove existing mappings
        mappingRepository.deleteByDataObjectId(dataObjectId);
        mappingRepository.flush();

        // Build field lookup
        Map<UUID, DataObjectField> fieldMap = obj.getFields().stream()
                .collect(Collectors.toMap(DataObjectField::getId, f -> f));

        List<ConnectorFieldMapping> mappings = request.mappings().stream().map(entry -> {
            DataObjectField field = fieldMap.get(entry.fieldId());
            if (field == null) {
                throw new EntityNotFoundException("Field not found: " + entry.fieldId());
            }
            return ConnectorFieldMapping.builder()
                    .dataObject(obj)
                    .connector(connector)
                    .field(field)
                    .sourceTable(entry.sourceTable())
                    .sourceField(entry.sourceField())
                    .build();
        }).toList();

        mappingRepository.saveAll(mappings);
    }

    @Transactional
    public void removeConnectorMapping(UUID dataObjectId) {
        findObjectAndValidate(dataObjectId);
        mappingRepository.deleteByDataObjectId(dataObjectId);
    }

    // --- Rule Fields ---

    @Transactional(readOnly = true)
    public List<RuleFieldResponse> getRuleFields(UUID dataObjectId, String dataObjectName) {
        UUID clientId = tenantValidator.getCurrentClientId();
        List<DataObjectField> fields;
        if (dataObjectId != null) {
            fields = fieldRepository.findRuleEligibleFieldsByClientIdAndDataObjectId(clientId, dataObjectId);
        } else if (dataObjectName != null && !dataObjectName.isBlank()) {
            fields = fieldRepository.findRuleEligibleFieldsByClientIdAndDataObjectName(clientId, dataObjectName);
        } else {
            fields = fieldRepository.findRuleEligibleFieldsByClientId(clientId);
        }
        return fields.stream().map(RuleFieldResponse::from).toList();
    }

    // --- Helpers ---

    /**
     * Injects virtual location hierarchy fields into Partner Data field list.
     * Each location level becomes a mandatory LIST field with the level's values as sample data.
     * These fields are not stored in the database — they are synthesized from the location hierarchy.
     */
    private List<DataObjectFieldResponse> injectLocationHierarchyFields(List<DataObjectFieldResponse> existingFields) {
        UUID clientId = tenantValidator.getCurrentClientId();
        List<LocationLevel> levels = locationLevelRepository.findByClientIdOrderByDepthAsc(clientId);
        if (levels.isEmpty()) return existingFields;

        List<DataObjectFieldResponse> result = new ArrayList<>(existingFields);

        // Insert location fields after the Partner ID field (or at position 2 as fallback)
        int insertIdx = 2;
        for (int i = 0; i < existingFields.size(); i++) {
            if ("Partner ID".equalsIgnoreCase(existingFields.get(i).name())) {
                insertIdx = i + 1;
                break;
            }
        }

        for (LocationLevel level : levels) {
            List<String> valueNames = locationValueRepository.findByClientIdAndLevelId(clientId, level.getId())
                .stream().map(LocationValue::getName).toList();

            DataObjectFieldResponse virtualField = DataObjectFieldResponse.locationHierarchyField(
                level.getName(), valueNames, insertIdx);

            result.add(insertIdx, virtualField);
            insertIdx++;
        }

        return result;
    }

    private DataObject findObjectAndValidate(UUID id) {
        UUID clientId = tenantValidator.getCurrentClientId();
        return dataObjectRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new EntityNotFoundException("Data object not found: " + id));
    }

    private String resolveConnectorName(DataObject obj) {
        List<ConnectorFieldMapping> mappings = obj.getConnectorFieldMappings();
        if (mappings == null || mappings.isEmpty()) return null;
        return mappings.get(0).getConnector().getName();
    }

    private ConnectorMappingDetailResponse buildMappingResponse(DataObject obj) {
        List<ConnectorFieldMapping> mappings = obj.getConnectorFieldMappings();
        if (mappings == null || mappings.isEmpty()) return null;
        Connector connector = mappings.get(0).getConnector();
        List<ConnectorMappingDetailResponse.FieldMappingEntry> entries = mappings.stream()
                .map(m -> new ConnectorMappingDetailResponse.FieldMappingEntry(
                        m.getField().getId(), m.getSourceTable(), m.getSourceField()))
                .toList();
        return new ConnectorMappingDetailResponse(
                connector.getId(), connector.getName(), connector.getConnectorType(), entries);
    }

    private String serializeSampleValues(List<String> values) {
        if (values == null || values.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            return null;
        }
    }
}
