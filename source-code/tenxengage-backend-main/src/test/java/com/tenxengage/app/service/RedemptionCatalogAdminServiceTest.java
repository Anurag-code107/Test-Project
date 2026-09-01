package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.CreateRedemptionCatalogItemRequest;
import com.tenxengage.app.dto.request.UpdateRedemptionCatalogItemRequest;
import com.tenxengage.app.dto.response.IntegrationHealthResponse;
import com.tenxengage.app.dto.response.RedemptionCatalogItemDetailResponse;
import com.tenxengage.app.dto.response.RedemptionCatalogItemResponse;
import com.tenxengage.app.dto.response.SyncJobResponse;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.ClientCatalogRegionConfigRepository;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.testdata.RedemptionCatalogItemFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;

@ExtendWith(MockitoExtension.class)
class RedemptionCatalogAdminServiceTest {

    @Mock private RedemptionCatalogItemRepository catalogItemRepository;
    @Mock private ClientCatalogRegionConfigRepository regionConfigRepository;
    @Mock private XoxodaySyncJobService syncJobService;
    @Mock private FileStorageService fileStorageService;

    @InjectMocks private RedemptionCatalogAdminService service;

    // Alias used in upload tests for clarity
    private RedemptionCatalogAdminService adminService() { return service; }

    @Test
    void createCatalogItem_returns201_whenValid() {
        RedemptionCatalogItem saved = RedemptionCatalogItemFixtures.activeNonCashItem().build();
        when(catalogItemRepository.findByProviderItemId(any())).thenReturn(Optional.empty());
        when(catalogItemRepository.save(any())).thenReturn(saved);

        CreateRedemptionCatalogItemRequest request = new CreateRedemptionCatalogItemRequest(
                "Gift Card", "A gift card", RedemptionCategory.NON_CASH,
                "USD", new BigDecimal("5.00"), RedemptionProcessingMode.INSTANT,
                List.of("US"), "AMZN-001", false, 30, null);

        RedemptionCatalogItemDetailResponse result = service.createCatalogItem(request);

        assertThat(result).isNotNull();
        verify(catalogItemRepository).save(any());
    }

    @Test
    void createCatalogItem_rejects_cashItemWithIsReturnable() {
        CreateRedemptionCatalogItemRequest request = new CreateRedemptionCatalogItemRequest(
                "Bank Transfer", null, RedemptionCategory.CASH,
                "USD", new BigDecimal("10.00"), null,
                List.of(), null, true, 0, null);

        assertThatThrownBy(() -> service.createCatalogItem(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(BAD_REQUEST));
    }

    @Test
    void createCatalogItem_rejects_duplicateProviderItemId() {
        RedemptionCatalogItem existing = RedemptionCatalogItemFixtures.activeNonCashItem().build();
        when(catalogItemRepository.findByProviderItemId("DUP-001")).thenReturn(Optional.of(existing));

        CreateRedemptionCatalogItemRequest request = new CreateRedemptionCatalogItemRequest(
                "Another Card", null, RedemptionCategory.NON_CASH,
                "USD", new BigDecimal("5.00"), null,
                List.of(), "DUP-001", false, 0, null);

        assertThatThrownBy(() -> service.createCatalogItem(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(CONFLICT));
    }

    @Test
    void activateCatalogItem_rejects_nonCashWithoutProviderItemId() {
        UUID id = UUID.randomUUID();
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeNonCashItem()
                .providerItemId(null)
                .isActive(false)
                .build();
        item.setId(id);
        when(catalogItemRepository.findById(id)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.activateCatalogItem(id))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot activate a non-cash catalog item without a provider item ID");
    }

    @Test
    void updateCatalogItem_rejects_geographicScopeNarrowing() {
        UUID id = UUID.randomUUID();
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeNonCashItem()
                .geographicScope(new String[]{"US", "IN"})
                .build();
        item.setId(id);
        when(catalogItemRepository.findById(id)).thenReturn(Optional.of(item));
        when(regionConfigRepository.existsByRedemptionCatalogItemIdAndRegionCode(id, "IN")).thenReturn(true);

        UpdateRedemptionCatalogItemRequest request = new UpdateRedemptionCatalogItemRequest(
                null, null, null, null, null, null, List.of("US"), null, null, null, null); // imageUrl absent → null Optional

        assertThatThrownBy(() -> service.updateCatalogItem(id, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot narrow geographic scope");
    }

    @Test
    void deactivateCatalogItem_setsIsActiveFalse_doesNotCascade() {
        UUID id = UUID.randomUUID();
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeCashItem().isActive(true).build();
        item.setId(id);
        when(catalogItemRepository.findById(id)).thenReturn(Optional.of(item));
        when(catalogItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RedemptionCatalogItemResponse result = service.deactivateCatalogItem(id);

        assertThat(result).isNotNull();
        verify(catalogItemRepository).save(any());
        // ClientCatalogItemConfig is never touched
        verify(regionConfigRepository, never()).deleteByClientIdAndRedemptionCatalogItemIdAndRegionCode(
                any(), any(), any());
    }

    @Test
    void listCatalogItems_enforcesPageSizeCap() {
        assertThatThrownBy(() -> service.listCatalogItems(null, null, null, PageRequest.of(0, 51)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pageSize must not exceed 50");
    }

    @Test
    void triggerXoxodaySync_returns202WithJobId() {
        doNothing().when(syncJobService).submitSyncJob();

        SyncJobResponse response = service.triggerXoxodaySync();

        assertThat(response.jobId()).isNotNull();
        assertThat(response.status()).isEqualTo("QUEUED");
        verify(syncJobService).submitSyncJob();
    }

    @Test
    void getIntegrationHealth_returnsLastSyncStatus() {
        when(syncJobService.getSyncStatus()).thenReturn("SUCCESS");
        when(syncJobService.getLastSyncAt()).thenReturn(Instant.now());
        when(syncJobService.getFailedSyncCount()).thenReturn(0);

        IntegrationHealthResponse response = service.getIntegrationHealth();

        assertThat(response.syncStatus()).isEqualTo("SUCCESS");
        assertThat(response.lastSyncAt()).isNotNull();
        assertThat(response.failedSyncCount()).isZero();
        assertThat(response.recentWebhooks()).isEmpty();
    }

    @Test
    void uploadCatalogItemImage_validFile_savesKeyAndReturnsResponse() throws Exception {
        UUID id = UUID.randomUUID();
        RedemptionCatalogItem item = RedemptionCatalogItem.builder()
                .name("Test Item").category(RedemptionCategory.NON_CASH)
                .currencyId("points").defaultMinRedemptionAmount(BigDecimal.TEN)
                .defaultProcessingMode(RedemptionProcessingMode.INSTANT)
                .geographicScope(new String[0]).isActive(true).build();
        item.setId(id);

        MockMultipartFile file = new MockMultipartFile(
                "file", "image.png", "image/png", new byte[1024]);

        when(catalogItemRepository.findById(id)).thenReturn(Optional.of(item));
        when(catalogItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RedemptionCatalogItemResponse response = service.uploadCatalogItemImage(id, file);

        assertThat(response.imageUrl()).startsWith("catalog/" + id + "/image-");
        assertThat(response.imageUrl()).endsWith(".png");
        verify(fileStorageService).upload(startsWith("catalog/" + id + "/image-"),
                any(), eq(1024L), eq("image/png"));
    }

    @Test
    void uploadCatalogItemImage_replaceExisting_deletesOldKey() throws Exception {
        UUID id = UUID.randomUUID();
        RedemptionCatalogItem item = RedemptionCatalogItem.builder()
                .name("Test").category(RedemptionCategory.NON_CASH)
                .currencyId("points").defaultMinRedemptionAmount(BigDecimal.TEN)
                .defaultProcessingMode(RedemptionProcessingMode.INSTANT)
                .geographicScope(new String[0]).isActive(true).build();
        item.setId(id);
        item.setImageUrl("catalog/" + id + "/image-old.jpg");

        MockMultipartFile file = new MockMultipartFile(
                "file", "new.png", "image/png", new byte[512]);

        when(catalogItemRepository.findById(id)).thenReturn(Optional.of(item));
        when(catalogItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.uploadCatalogItemImage(id, file);

        verify(fileStorageService).delete("catalog/" + id + "/image-old.jpg");
    }

    @Test
    void uploadCatalogItemImage_oversizedFile_throws400() {
        UUID id = UUID.randomUUID();
        byte[] bigFile = new byte[6 * 1024 * 1024]; // 6 MB
        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", bigFile);

        assertThatThrownBy(() -> service.uploadCatalogItemImage(id, file))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void uploadCatalogItemImage_unsupportedMimeType_throws400() {
        UUID id = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[100]);

        assertThatThrownBy(() -> service.uploadCatalogItemImage(id, file))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
