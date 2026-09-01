package com.tenxengage.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.dto.response.BuilderConfigResponse;
import com.tenxengage.app.entity.BuilderFieldConfig;
import com.tenxengage.app.entity.BuilderSectionConfig;
import com.tenxengage.app.repository.ActivityCategoryRepository;
import com.tenxengage.app.repository.BuilderFieldConfigRepository;
import com.tenxengage.app.repository.BuilderSectionConfigRepository;
import com.tenxengage.app.repository.ClientRoleRepository;
import com.tenxengage.app.repository.DataObjectFieldRepository;
import com.tenxengage.app.repository.LocationLevelRepository;
import com.tenxengage.app.repository.LocationValueRepository;
import com.tenxengage.app.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuilderConfigServiceTest {

    @Mock
    private BuilderSectionConfigRepository sectionConfigRepository;

    @Mock
    private BuilderFieldConfigRepository fieldConfigRepository;

    @Mock
    private DataObjectFieldRepository dataObjectFieldRepository;

    @Mock
    private LocationValueRepository locationValueRepository;

    @Mock
    private LocationLevelRepository locationLevelRepository;

    @Mock
    private ClientRoleRepository clientRoleRepository;

    @Mock
    private ActivityCategoryRepository activityCategoryRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private BuilderConfigService builderConfigService;

    private UUID clientId;

    @BeforeEach
    void setUp() {
        builderConfigService = new BuilderConfigService(
            sectionConfigRepository,
            fieldConfigRepository,
            dataObjectFieldRepository,
            locationValueRepository,
            locationLevelRepository,
            clientRoleRepository,
            activityCategoryRepository,
            objectMapper
        );

        clientId = UUID.randomUUID();
        TenantContext.setClientId(clientId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getBuilderConfig_returnsSectionsWithFields() {
        UUID sectionId = UUID.randomUUID();
        UUID fieldId = UUID.randomUUID();

        BuilderFieldConfig field = BuilderFieldConfig.builder()
            .fieldKey("incentive_name")
            .displayName("Incentive Name")
            .fieldType("TEXT")
            .isMandatory(true)
            .isSystem(true)
            .sortOrder(0)
            .build();
        field.setId(fieldId);

        BuilderSectionConfig section = BuilderSectionConfig.builder()
            .clientId(clientId)
            .incentiveType("SALES")
            .sectionKey("basics")
            .displayName("Basic Information")
            .sortOrder(0)
            .isLocked(false)
            .isVisible(true)
            .fields(new ArrayList<>(List.of(field)))
            .build();
        section.setId(sectionId);
        field.setSectionConfig(section);

        when(sectionConfigRepository
            .findByClientIdAndIncentiveTypeOrderBySortOrder(clientId, "SALES"))
            .thenReturn(List.of(section));

        BuilderConfigResponse response = builderConfigService.getBuilderConfig("SALES");

        assertThat(response.incentiveType()).isEqualTo("SALES");
        assertThat(response.sections()).hasSize(1);
        assertThat(response.sections().get(0).sectionKey()).isEqualTo("basics");
        assertThat(response.sections().get(0).fields()).hasSize(1);
        assertThat(response.sections().get(0).fields().get(0).fieldKey()).isEqualTo("incentive_name");
    }

    @Test
    void addField_lockedSection_throwsException() {
        UUID sectionId = UUID.randomUUID();

        BuilderSectionConfig section = BuilderSectionConfig.builder()
            .clientId(clientId)
            .incentiveType("SALES")
            .sectionKey("locked_section")
            .displayName("Locked Section")
            .isLocked(true)
            .fields(new ArrayList<>())
            .build();
        section.setId(sectionId);

        when(sectionConfigRepository.findById(sectionId)).thenReturn(Optional.of(section));

        com.tenxengage.app.dto.request.CreateBuilderFieldRequest request =
            new com.tenxengage.app.dto.request.CreateBuilderFieldRequest(
                "custom_field", "Custom Field", "TEXT",
                "Help text", false, false, null, null, null, false
            );

        assertThatThrownBy(() -> builderConfigService.addField(sectionId, request))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("locked");

        verify(fieldConfigRepository, never()).save(any());
    }

    @Test
    void addField_unlockedSection_createsField() {
        UUID sectionId = UUID.randomUUID();

        BuilderSectionConfig section = BuilderSectionConfig.builder()
            .clientId(clientId)
            .incentiveType("SALES")
            .sectionKey("eligibility")
            .displayName("Eligibility")
            .isLocked(false)
            .fields(new ArrayList<>())
            .build();
        section.setId(sectionId);

        when(sectionConfigRepository.findById(sectionId)).thenReturn(Optional.of(section));

        BuilderFieldConfig savedField = BuilderFieldConfig.builder()
            .fieldKey("custom_region")
            .displayName("Custom Region")
            .fieldType("MULTI_SELECT")
            .isSystem(false)
            .isMandatory(true)
            .isEligibility(true)
            .sortOrder(0)
            .sectionConfig(section)
            .build();
        savedField.setId(UUID.randomUUID());
        when(fieldConfigRepository.save(any())).thenReturn(savedField);

        com.tenxengage.app.dto.request.CreateBuilderFieldRequest request =
            new com.tenxengage.app.dto.request.CreateBuilderFieldRequest(
                "custom_region", "Custom Region", "MULTI_SELECT",
                null, true, true, null, null, null, false
            );

        builderConfigService.addField(sectionId, request);

        ArgumentCaptor<BuilderFieldConfig> captor = ArgumentCaptor.forClass(BuilderFieldConfig.class);
        verify(fieldConfigRepository).save(captor.capture());

        BuilderFieldConfig captured = captor.getValue();
        assertThat(captured.getFieldKey()).isEqualTo("custom_region");
        assertThat(captured.getDisplayName()).isEqualTo("Custom Region");
        assertThat(captured.getFieldType()).isEqualTo("MULTI_SELECT");
        assertThat(captured.isSystem()).isFalse();
        assertThat(captured.isMandatory()).isTrue();
        assertThat(captured.isEligibility()).isTrue();
    }

    @Test
    void removeField_systemField_throwsException() {
        UUID fieldId = UUID.randomUUID();

        BuilderSectionConfig section = BuilderSectionConfig.builder()
            .clientId(clientId)
            .incentiveType("SALES")
            .sectionKey("basics")
            .displayName("Basics")
            .build();
        section.setId(UUID.randomUUID());

        BuilderFieldConfig field = BuilderFieldConfig.builder()
            .fieldKey("incentive_name")
            .displayName("Incentive Name")
            .fieldType("TEXT")
            .isSystem(true)
            .sectionConfig(section)
            .build();
        field.setId(fieldId);

        when(fieldConfigRepository.findById(fieldId)).thenReturn(Optional.of(field));

        assertThatThrownBy(() -> builderConfigService.removeField(fieldId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("system field");

        verify(fieldConfigRepository, never()).delete(any());
    }

    @Test
    void removeField_customField_deletes() {
        UUID fieldId = UUID.randomUUID();

        BuilderSectionConfig section = BuilderSectionConfig.builder()
            .clientId(clientId)
            .incentiveType("SALES")
            .sectionKey("eligibility")
            .displayName("Eligibility")
            .build();
        section.setId(UUID.randomUUID());

        BuilderFieldConfig field = BuilderFieldConfig.builder()
            .fieldKey("custom_region")
            .displayName("Custom Region")
            .fieldType("MULTI_SELECT")
            .isSystem(false)
            .sectionConfig(section)
            .build();
        field.setId(fieldId);

        when(fieldConfigRepository.findById(fieldId)).thenReturn(Optional.of(field));

        builderConfigService.removeField(fieldId);

        verify(fieldConfigRepository).delete(field);
    }
}
