package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.CreateLocationLevelRequest;
import com.tenxengage.app.dto.request.CreateLocationValueRequest;
import com.tenxengage.app.dto.request.UpdateLocationLevelRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @Mock
    private LocationLevelRepository levelRepository;

    @Mock
    private LocationValueRepository valueRepository;

    @Mock
    private TenantValidator tenantValidator;

    @InjectMocks
    private LocationService locationService;

    private UUID clientId;
    private LocationLevel regionLevel;
    private LocationLevel countryLevel;
    private LocationValue americasValue;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();

        regionLevel = new LocationLevel();
        regionLevel.setId(UUID.randomUUID());
        regionLevel.setClientId(clientId);
        regionLevel.setName("Region");
        regionLevel.setDepth(0);
        regionLevel.setValues(new ArrayList<>());
        regionLevel.setCreatedAt(Instant.now());
        regionLevel.setUpdatedAt(Instant.now());

        countryLevel = new LocationLevel();
        countryLevel.setId(UUID.randomUUID());
        countryLevel.setClientId(clientId);
        countryLevel.setName("Country");
        countryLevel.setDepth(1);
        countryLevel.setValues(new ArrayList<>());
        countryLevel.setCreatedAt(Instant.now());
        countryLevel.setUpdatedAt(Instant.now());

        americasValue = new LocationValue();
        americasValue.setId(UUID.randomUUID());
        americasValue.setClientId(clientId);
        americasValue.setLevel(regionLevel);
        americasValue.setParent(null);
        americasValue.setName("AMERICAS");
        americasValue.setCode("AMERICAS");
        americasValue.setChildren(new ArrayList<>());
        americasValue.setCreatedAt(Instant.now());
        americasValue.setUpdatedAt(Instant.now());
    }

    @Test
    void getHierarchy_returnsLevelsAndTree() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(levelRepository.findByClientIdOrderByDepthAsc(clientId))
            .thenReturn(List.of(regionLevel, countryLevel));
        when(valueRepository.findByClientIdAndLevelIdAndParentIdIsNullOrderByName(clientId, regionLevel.getId()))
            .thenReturn(List.of(americasValue));

        LocationHierarchyResponse response = locationService.getHierarchy();

        assertThat(response.levels()).hasSize(2);
        assertThat(response.levels().get(0).name()).isEqualTo("Region");
        assertThat(response.levels().get(1).name()).isEqualTo("Country");
        assertThat(response.tree()).hasSize(1);
        assertThat(response.tree().get(0).name()).isEqualTo("AMERICAS");
    }

    @Test
    void getHierarchy_emptyLevels_returnsEmptyTree() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(levelRepository.findByClientIdOrderByDepthAsc(clientId)).thenReturn(List.of());

        LocationHierarchyResponse response = locationService.getHierarchy();

        assertThat(response.levels()).isEmpty();
        assertThat(response.tree()).isEmpty();
    }

    @Test
    void createLevel_assignsNextDepth() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(levelRepository.existsByClientIdAndName(clientId, "State")).thenReturn(false);
        when(levelRepository.findByClientIdOrderByDepthAsc(clientId))
            .thenReturn(List.of(regionLevel, countryLevel));

        LocationLevel saved = new LocationLevel();
        saved.setId(UUID.randomUUID());
        saved.setClientId(clientId);
        saved.setName("State");
        saved.setDepth(2);
        saved.setValues(new ArrayList<>());
        saved.setCreatedAt(Instant.now());
        saved.setUpdatedAt(Instant.now());

        when(levelRepository.save(any(LocationLevel.class))).thenReturn(saved);

        LocationLevelResponse response = locationService.createLevel(new CreateLocationLevelRequest("State"));

        assertThat(response.name()).isEqualTo("State");
        assertThat(response.depth()).isEqualTo(2);
    }

    @Test
    void createLevel_firstLevel_depthZero() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(levelRepository.existsByClientIdAndName(clientId, "Region")).thenReturn(false);
        when(levelRepository.findByClientIdOrderByDepthAsc(clientId)).thenReturn(List.of());

        LocationLevel saved = new LocationLevel();
        saved.setId(UUID.randomUUID());
        saved.setClientId(clientId);
        saved.setName("Region");
        saved.setDepth(0);
        saved.setValues(new ArrayList<>());
        saved.setCreatedAt(Instant.now());
        saved.setUpdatedAt(Instant.now());

        when(levelRepository.save(any(LocationLevel.class))).thenReturn(saved);

        LocationLevelResponse response = locationService.createLevel(new CreateLocationLevelRequest("Region"));

        assertThat(response.depth()).isEqualTo(0);
    }

    @Test
    void createLevel_duplicateName_throws() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(levelRepository.existsByClientIdAndName(clientId, "Region")).thenReturn(true);

        assertThatThrownBy(() -> locationService.createLevel(new CreateLocationLevelRequest("Region")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void updateLevel_renamesLevel() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(levelRepository.findById(regionLevel.getId())).thenReturn(Optional.of(regionLevel));
        when(levelRepository.existsByClientIdAndName(clientId, "Area")).thenReturn(false);

        LocationLevel saved = new LocationLevel();
        saved.setId(regionLevel.getId());
        saved.setClientId(clientId);
        saved.setName("Area");
        saved.setDepth(0);
        saved.setValues(new ArrayList<>());
        saved.setCreatedAt(Instant.now());
        saved.setUpdatedAt(Instant.now());

        when(levelRepository.save(any(LocationLevel.class))).thenReturn(saved);

        LocationLevelResponse response = locationService.updateLevel(
            regionLevel.getId(), new UpdateLocationLevelRequest("Area"));

        assertThat(response.name()).isEqualTo("Area");
    }

    @Test
    void updateLevel_notFound_throws() {
        UUID randomId = UUID.randomUUID();
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(levelRepository.findById(randomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> locationService.updateLevel(randomId, new UpdateLocationLevelRequest("X")))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteLevel_deletesBottomMostLevel() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(levelRepository.findById(countryLevel.getId())).thenReturn(Optional.of(countryLevel));
        when(levelRepository.findByClientIdOrderByDepthAsc(clientId))
            .thenReturn(List.of(regionLevel, countryLevel))
            .thenReturn(List.of(regionLevel));

        locationService.deleteLevel(countryLevel.getId());

        verify(levelRepository).delete(countryLevel);
        // Surviving regionLevel already sits at depth 0, so no reindex save() is needed.
        verify(levelRepository, never()).save(any(LocationLevel.class));
    }

    @Test
    void deleteLevel_rejectsNonLeafLevel() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(levelRepository.findById(regionLevel.getId())).thenReturn(Optional.of(regionLevel));
        when(levelRepository.findByClientIdOrderByDepthAsc(clientId))
            .thenReturn(List.of(regionLevel, countryLevel));

        assertThatThrownBy(() -> locationService.deleteLevel(regionLevel.getId()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("bottom up");

        verify(levelRepository, never()).delete(any(LocationLevel.class));
        verify(levelRepository, never()).save(any(LocationLevel.class));
    }

    @Test
    void createValue_createsSuccessfully() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(levelRepository.findById(regionLevel.getId())).thenReturn(Optional.of(regionLevel));
        when(valueRepository.existsByClientIdAndLevelIdAndParentIdAndName(
            clientId, regionLevel.getId(), null, "EMEAR")).thenReturn(false);

        LocationValue saved = new LocationValue();
        saved.setId(UUID.randomUUID());
        saved.setClientId(clientId);
        saved.setLevel(regionLevel);
        saved.setParent(null);
        saved.setName("EMEAR");
        saved.setCode("EMEAR");
        saved.setChildren(new ArrayList<>());
        saved.setCreatedAt(Instant.now());
        saved.setUpdatedAt(Instant.now());

        when(valueRepository.save(any(LocationValue.class))).thenReturn(saved);

        LocationValueResponse response = locationService.createValue(
            new CreateLocationValueRequest(regionLevel.getId(), null, "EMEAR", "EMEAR"));

        assertThat(response.name()).isEqualTo("EMEAR");
        assertThat(response.code()).isEqualTo("EMEAR");
    }

    @Test
    void createValue_withParent_createsSuccessfully() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(levelRepository.findById(countryLevel.getId())).thenReturn(Optional.of(countryLevel));
        when(valueRepository.findById(americasValue.getId())).thenReturn(Optional.of(americasValue));
        when(valueRepository.existsByClientIdAndLevelIdAndParentIdAndName(
            clientId, countryLevel.getId(), americasValue.getId(), "United States")).thenReturn(false);

        LocationValue saved = new LocationValue();
        saved.setId(UUID.randomUUID());
        saved.setClientId(clientId);
        saved.setLevel(countryLevel);
        saved.setParent(americasValue);
        saved.setName("United States");
        saved.setCode(null);
        saved.setChildren(new ArrayList<>());
        saved.setCreatedAt(Instant.now());
        saved.setUpdatedAt(Instant.now());

        when(valueRepository.save(any(LocationValue.class))).thenReturn(saved);

        LocationValueResponse response = locationService.createValue(
            new CreateLocationValueRequest(countryLevel.getId(), americasValue.getId(), "United States", null));

        assertThat(response.name()).isEqualTo("United States");
        assertThat(response.parentId()).isEqualTo(americasValue.getId());
    }

    @Test
    void createValue_duplicate_throws() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(levelRepository.findById(regionLevel.getId())).thenReturn(Optional.of(regionLevel));
        when(valueRepository.existsByClientIdAndLevelIdAndParentIdAndName(
            clientId, regionLevel.getId(), null, "AMERICAS")).thenReturn(true);

        assertThatThrownBy(() -> locationService.createValue(
            new CreateLocationValueRequest(regionLevel.getId(), null, "AMERICAS", "AMERICAS")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void updateValue_updatesNameAndCode() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(valueRepository.findById(americasValue.getId())).thenReturn(Optional.of(americasValue));

        LocationValue saved = new LocationValue();
        saved.setId(americasValue.getId());
        saved.setClientId(clientId);
        saved.setLevel(regionLevel);
        saved.setParent(null);
        saved.setName("Americas Region");
        saved.setCode("AMR");
        saved.setChildren(new ArrayList<>());
        saved.setCreatedAt(Instant.now());
        saved.setUpdatedAt(Instant.now());

        when(valueRepository.save(any(LocationValue.class))).thenReturn(saved);

        LocationValueResponse response = locationService.updateValue(
            americasValue.getId(), new UpdateLocationValueRequest("Americas Region", "AMR"));

        assertThat(response.name()).isEqualTo("Americas Region");
        assertThat(response.code()).isEqualTo("AMR");
    }

    @Test
    void updateValue_notFound_throws() {
        UUID randomId = UUID.randomUUID();
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(valueRepository.findById(randomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> locationService.updateValue(randomId, new UpdateLocationValueRequest("X", null)))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteValue_deletesSuccessfully() {
        when(valueRepository.findById(americasValue.getId())).thenReturn(Optional.of(americasValue));

        locationService.deleteValue(americasValue.getId());

        verify(valueRepository).delete(americasValue);
    }

    @Test
    void deleteValue_notFound_throws() {
        UUID randomId = UUID.randomUUID();
        when(valueRepository.findById(randomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> locationService.deleteValue(randomId))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── getBuilderOptions / getFilterOptions flag-split (BUG-032) ──────────

    @Test
    void getBuilderOptions_filtersByUseInBuilderNotUseInFilters() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(levelRepository.findByClientIdAndUseInBuilderTrueOrderByDepthAsc(clientId))
            .thenReturn(List.of(regionLevel, countryLevel));
        when(valueRepository.findByClientIdAndLevelId(clientId, regionLevel.getId()))
            .thenReturn(List.of());
        when(valueRepository.findByClientIdAndLevelId(clientId, countryLevel.getId()))
            .thenReturn(List.of());

        var response = locationService.getBuilderOptions();

        // Hits the useInBuilder repo method, never the useInFilters one.
        verify(levelRepository).findByClientIdAndUseInBuilderTrueOrderByDepthAsc(clientId);
        assertThat(response.levels())
            .extracting(LocationFilterOptionsResponse.LocationFilterLevel::levelName)
            .containsExactly("Region", "Country");
    }

    @Test
    void getFilterOptions_stillFiltersByUseInFilters() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(levelRepository.findByClientIdAndUseInFiltersTrueOrderByDepthAsc(clientId))
            .thenReturn(List.of(regionLevel));
        when(valueRepository.findByClientIdAndLevelId(clientId, regionLevel.getId()))
            .thenReturn(List.of());

        var response = locationService.getFilterOptions();

        // Regression guard: getFilterOptions is the LocationFilter consumer's
        // (used on /claims and /home) and MUST stay keyed on useInFilters —
        // BUG-032's fix only swapped the AI copilot's call, not this endpoint.
        verify(levelRepository).findByClientIdAndUseInFiltersTrueOrderByDepthAsc(clientId);
        assertThat(response.levels())
            .extracting(LocationFilterOptionsResponse.LocationFilterLevel::levelName)
            .containsExactly("Region");
    }
}
