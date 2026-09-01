package com.tenxengage.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.dto.request.CreateDataObjectRequest;
import com.tenxengage.app.dto.request.UpdateDataObjectRequest;
import com.tenxengage.app.dto.response.DataObjectDetailResponse;
import com.tenxengage.app.dto.response.DataObjectResponse;
import com.tenxengage.app.entity.DataObject;
import com.tenxengage.app.repository.ConnectorFieldMappingRepository;
import com.tenxengage.app.repository.ConnectorRepository;
import com.tenxengage.app.repository.DataObjectFieldRepository;
import com.tenxengage.app.repository.DataObjectRepository;
import com.tenxengage.app.repository.LocationLevelRepository;
import com.tenxengage.app.repository.LocationValueRepository;
import com.tenxengage.app.security.TenantValidator;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataObjectServiceTest {

    @Mock private DataObjectRepository dataObjectRepository;
    @Mock private DataObjectFieldRepository fieldRepository;
    @Mock private ConnectorFieldMappingRepository mappingRepository;
    @Mock private ConnectorRepository connectorRepository;
    @Mock private LocationLevelRepository locationLevelRepository;
    @Mock private LocationValueRepository locationValueRepository;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();
    @Mock private TenantValidator tenantValidator;

    @InjectMocks private DataObjectService service;

    private UUID clientId;
    private UUID objectId;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        objectId = UUID.randomUUID();
    }

    // -------------------------------------------------------------------------
    // getDataObjects
    // -------------------------------------------------------------------------

    @Test
    void getDataObjects_returnsMappedList() {
        DataObject obj = buildDataObject(objectId, "Sales Data", false);

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(dataObjectRepository.findByClientIdOrderBySortOrder(clientId))
                .thenReturn(List.of(obj));

        List<DataObjectResponse> result = service.getDataObjects();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Sales Data");
    }

    // -------------------------------------------------------------------------
    // getDataObject
    // -------------------------------------------------------------------------

    @Test
    void getDataObject_found_returnsDetail() {
        DataObject obj = buildDataObject(objectId, "Sales Data", false);

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(dataObjectRepository.findByIdAndClientId(objectId, clientId))
                .thenReturn(Optional.of(obj));

        DataObjectDetailResponse result = service.getDataObject(objectId);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Sales Data");
    }

    @Test
    void getDataObject_notFound_throwsEntityNotFoundException() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(dataObjectRepository.findByIdAndClientId(any(), eq(clientId)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDataObject(objectId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // createDataObject
    // -------------------------------------------------------------------------

    @Test
    void createDataObject_newName_createsSuccessfully() {
        DataObject saved = buildDataObject(objectId, "New Object", false);

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(dataObjectRepository.existsByClientIdAndName(clientId, "New Object"))
                .thenReturn(false);
        when(dataObjectRepository.save(any(DataObject.class))).thenReturn(saved);

        CreateDataObjectRequest request = new CreateDataObjectRequest("New Object", "A new object");

        DataObjectDetailResponse result = service.createDataObject(request);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("New Object");
        verify(dataObjectRepository).save(any(DataObject.class));
    }

    @Test
    void createDataObject_duplicateName_throwsIllegalArgumentException() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(dataObjectRepository.existsByClientIdAndName(clientId, "Duplicate"))
                .thenReturn(true);

        CreateDataObjectRequest request = new CreateDataObjectRequest("Duplicate", null);

        assertThatThrownBy(() -> service.createDataObject(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
    }

    // -------------------------------------------------------------------------
    // updateDataObject
    // -------------------------------------------------------------------------

    @Test
    void updateDataObject_found_updatesNameAndDescription() {
        DataObject obj = buildDataObject(objectId, "Old Name", false);

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(dataObjectRepository.findByIdAndClientId(objectId, clientId))
                .thenReturn(Optional.of(obj));
        when(dataObjectRepository.save(any(DataObject.class))).thenReturn(obj);

        UpdateDataObjectRequest request = new UpdateDataObjectRequest("Updated Name", "Updated desc");

        DataObjectDetailResponse result = service.updateDataObject(objectId, request);

        assertThat(result).isNotNull();
        assertThat(obj.getName()).isEqualTo("Updated Name");
    }

    // -------------------------------------------------------------------------
    // deleteDataObject
    // -------------------------------------------------------------------------

    @Test
    void deleteDataObject_defaultObject_throwsIllegalArgumentException() {
        DataObject obj = buildDataObject(objectId, "Partner Data", true);

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(dataObjectRepository.findByIdAndClientId(objectId, clientId))
                .thenReturn(Optional.of(obj));

        assertThatThrownBy(() -> service.deleteDataObject(objectId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("default");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private DataObject buildDataObject(UUID id, String name, boolean isDefault) {
        DataObject obj = DataObject.builder()
                .clientId(clientId)
                .name(name)
                .isDefault(isDefault)
                .sortOrder(0)
                .fields(new ArrayList<>())
                .connectorFieldMappings(new ArrayList<>())
                .build();
        obj.setId(id);
        obj.setCreatedAt(Instant.now());
        obj.setUpdatedAt(Instant.now());
        return obj;
    }
}
