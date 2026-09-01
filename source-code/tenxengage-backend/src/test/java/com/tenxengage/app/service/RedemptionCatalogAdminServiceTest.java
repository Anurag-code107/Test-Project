package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.CreateRedemptionCatalogItemRequest;
import com.tenxengage.app.dto.request.UpdateRedemptionCatalogItemRequest;
import com.tenxengage.app.dto.response.GiftCardSkuResponse;
import com.tenxengage.app.dto.response.IntegrationHealthResponse;
import com.tenxengage.app.dto.response.RedemptionCatalogItemDetailResponse;
import com.tenxengage.app.dto.response.RedemptionCatalogItemResponse;
import com.tenxengage.app.dto.response.SyncJobResponse;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionValueType;
import org.mockito.ArgumentCaptor;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.ClientCatalogRegionConfigRepository;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.testdata.RedemptionCatalogItemFixtures;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.lenient;
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
    @Mock private TenantValidator tenantValidator;
    @Mock private GiftCardCatalogService giftCardCatalogService;

    @InjectMocks private RedemptionCatalogAdminService service;

    private static final UUID CLIENT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        // Client-owned catalog: every create/read/write resolves + scopes by the caller's client.
        lenient().when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
    }

    // Alias used in upload tests for clarity
    private RedemptionCatalogAdminService adminService() { return service; }

    @Test
    void createCatalogItem_returns201_whenValid() {
        RedemptionCatalogItem saved = RedemptionCatalogItemFixtures.activeNonCashItem().build();
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
    void createCatalogItem_rejects_activeDuplicateProviderItemId() {
        // Only an ACTIVE non-deleted item with the same SKU + category blocks a new create.
        when(catalogItemRepository.existsByOwnerClientIdAndProviderItemIdAndCategoryAndIsActiveTrueAndDeletedFalse(
                CLIENT_ID, "DUP-001", RedemptionCategory.NON_CASH)).thenReturn(true);

        CreateRedemptionCatalogItemRequest request = new CreateRedemptionCatalogItemRequest(
                "Another Card", null, RedemptionCategory.NON_CASH,
                "USD", new BigDecimal("5.00"), null,
                List.of(), "DUP-001", false, 0, null);

        assertThatThrownBy(() -> service.createCatalogItem(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(CONFLICT));
        verify(catalogItemRepository, never()).save(any());
    }

    @Test
    void createCatalogItem_allowsReuse_whenNoActiveDuplicate() {
        // A retired (deactivated OR soft-deleted) item with the same SKU does NOT block re-creation.
        RedemptionCatalogItem saved = RedemptionCatalogItemFixtures.activeNonCashItem().build();
        when(catalogItemRepository.existsByOwnerClientIdAndProviderItemIdAndCategoryAndIsActiveTrueAndDeletedFalse(
                CLIENT_ID, "DUP-001", RedemptionCategory.NON_CASH)).thenReturn(false);
        when(catalogItemRepository.save(any())).thenReturn(saved);

        CreateRedemptionCatalogItemRequest request = new CreateRedemptionCatalogItemRequest(
                "Recreated Card", null, RedemptionCategory.NON_CASH,
                "USD", new BigDecimal("5.00"), RedemptionProcessingMode.INSTANT,
                List.of(), "DUP-001", false, 0, null);

        assertThat(service.createCatalogItem(request)).isNotNull();
        verify(catalogItemRepository).save(any());
    }

    @Test
    void createCatalogItem_stampsFixedValueTypeAndLocksBounds_fromResolvedSku() {
        when(catalogItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(giftCardCatalogService.findBySku("U-FIX-10")).thenReturn(Optional.of(new GiftCardSkuResponse(
                "U-FIX-10", "Acme $10", "Acme", null, "USD", "FIXED",
                new BigDecimal("10.00"), BigDecimal.ZERO, BigDecimal.ZERO)));

        CreateRedemptionCatalogItemRequest request = new CreateRedemptionCatalogItemRequest(
                "Acme $10", null, RedemptionCategory.CASH,
                "cash", new BigDecimal("3.00"), RedemptionProcessingMode.INSTANT,
                List.of(), "U-FIX-10", false, 0, null);

        service.createCatalogItem(request);

        ArgumentCaptor<RedemptionCatalogItem> captor = ArgumentCaptor.forClass(RedemptionCatalogItem.class);
        verify(catalogItemRepository).save(captor.capture());
        RedemptionCatalogItem entity = captor.getValue();
        assertThat(entity.getValueType()).isEqualTo(RedemptionValueType.FIXED);
        // FIXED locks min == max == face value, overriding the requested min (3.00).
        assertThat(entity.getDefaultMinRedemptionAmount()).isEqualByComparingTo("10.00");
        assertThat(entity.getDefaultMaxRedemptionAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    void createCatalogItem_stampsProviderImageUrl_fromResolvedSku() {
        // The SKU's brand image becomes the card's image when the admin uploads none of their own.
        when(catalogItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(giftCardCatalogService.findBySku("U371046")).thenReturn(Optional.of(new GiftCardSkuResponse(
                "U371046", "Sling TV eGift Card $25", "Sling TV",
                "https://cdn.example.com/brands/sling.png", "USD", "FIXED",
                new BigDecimal("25.00"), BigDecimal.ZERO, BigDecimal.ZERO)));

        CreateRedemptionCatalogItemRequest request = new CreateRedemptionCatalogItemRequest(
                "Sling TV eGift Card $25", null, RedemptionCategory.CASH,
                "cash", new BigDecimal("25.00"), RedemptionProcessingMode.INSTANT,
                List.of(), "U371046", false, 0, null);

        service.createCatalogItem(request);

        ArgumentCaptor<RedemptionCatalogItem> captor = ArgumentCaptor.forClass(RedemptionCatalogItem.class);
        verify(catalogItemRepository).save(captor.capture());
        assertThat(captor.getValue().getProviderImageUrl())
                .isEqualTo("https://cdn.example.com/brands/sling.png");
        // The admin's own upload is independent of the SKU image — nothing was uploaded here.
        assertThat(captor.getValue().getImageUrl()).isNull();
    }

    @Test
    void createCatalogItem_leavesProviderImageUrlNull_whenSkuHasNoBrandImage() {
        when(catalogItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(giftCardCatalogService.findBySku("U-NOIMG")).thenReturn(Optional.of(new GiftCardSkuResponse(
                "U-NOIMG", "Plain Card", "Plain", null, "USD", "FIXED",
                new BigDecimal("15.00"), BigDecimal.ZERO, BigDecimal.ZERO)));

        CreateRedemptionCatalogItemRequest request = new CreateRedemptionCatalogItemRequest(
                "Plain Card", null, RedemptionCategory.CASH,
                "cash", new BigDecimal("15.00"), RedemptionProcessingMode.INSTANT,
                List.of(), "U-NOIMG", false, 0, null);

        service.createCatalogItem(request);

        ArgumentCaptor<RedemptionCatalogItem> captor = ArgumentCaptor.forClass(RedemptionCatalogItem.class);
        verify(catalogItemRepository).save(captor.capture());
        // Null → the card falls back to the category illustration, as before.
        assertThat(captor.getValue().getProviderImageUrl()).isNull();
    }

    @Test
    void updateCatalogItem_reStampsProviderImageUrl_whenSkuChanges() {
        UUID id = UUID.randomUUID();
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeCashItem()
                .providerItemId("U-OLD")
                .providerImageUrl("https://cdn.example.com/brands/old.png")
                .build();
        item.setId(id);
        when(catalogItemRepository.findByIdAndOwnerClientIdAndDeletedFalseAndIsBankTransferFalse(id, CLIENT_ID))
                .thenReturn(Optional.of(item));
        when(catalogItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(giftCardCatalogService.findBySku("U-NEW")).thenReturn(Optional.of(new GiftCardSkuResponse(
                "U-NEW", "New Brand Card", "NewBrand",
                "https://cdn.example.com/brands/new.png", "USD", "FIXED",
                new BigDecimal("20.00"), BigDecimal.ZERO, BigDecimal.ZERO)));

        service.updateCatalogItem(id, new UpdateRedemptionCatalogItemRequest(
                null, null, null, null, null, null, null, "U-NEW", null, null, null));

        assertThat(item.getProviderImageUrl()).isEqualTo("https://cdn.example.com/brands/new.png");
    }

    @Test
    void updateCatalogItem_clearsProviderImageUrl_whenNewSkuIsUnknown() {
        // Never keep the previous brand's logo on a card that now points at a different SKU.
        UUID id = UUID.randomUUID();
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeCashItem()
                .providerItemId("U-OLD")
                .providerImageUrl("https://cdn.example.com/brands/old.png")
                .build();
        item.setId(id);
        when(catalogItemRepository.findByIdAndOwnerClientIdAndDeletedFalseAndIsBankTransferFalse(id, CLIENT_ID))
                .thenReturn(Optional.of(item));
        when(catalogItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(giftCardCatalogService.findBySku("U-UNKNOWN")).thenReturn(Optional.empty());

        service.updateCatalogItem(id, new UpdateRedemptionCatalogItemRequest(
                null, null, null, null, null, null, null, "U-UNKNOWN", null, null, null));

        assertThat(item.getProviderImageUrl()).isNull();
    }

    @Test
    void updateCatalogItem_rejects_movingALiveItemOntoASkuHeldByAnotherLiveItem() {
        UUID id = UUID.randomUUID();
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeCashItem()
                .providerItemId("U-OLD")
                .isActive(true)
                .build();
        item.setId(id);
        when(catalogItemRepository.findByIdAndOwnerClientIdAndDeletedFalseAndIsBankTransferFalse(id, CLIENT_ID))
                .thenReturn(Optional.of(item));
        when(catalogItemRepository.existsByOwnerClientIdAndProviderItemIdAndCategoryAndIsActiveTrueAndDeletedFalse(
                item.getOwnerClientId(), "U-TAKEN", RedemptionCategory.CASH)).thenReturn(true);

        assertThatThrownBy(() -> service.updateCatalogItem(id, new UpdateRedemptionCatalogItemRequest(
                null, null, null, null, null, null, null, "U-TAKEN", null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("An active catalog item with this providerItemId already exists");
        // Rejected before the entity was mutated — no partial write to save.
        assertThat(item.getProviderItemId()).isEqualTo("U-OLD");
        verify(catalogItemRepository, never()).save(any());
    }

    @Test
    void updateCatalogItem_allowsAnInactiveDraftToTakeALiveSku() {
        // Only a LIVE card reserves a SKU; drafts may share one and are caught at activation.
        UUID id = UUID.randomUUID();
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeCashItem()
                .providerItemId("U-OLD")
                .isActive(false)
                .build();
        item.setId(id);
        when(catalogItemRepository.findByIdAndOwnerClientIdAndDeletedFalseAndIsBankTransferFalse(id, CLIENT_ID))
                .thenReturn(Optional.of(item));
        when(catalogItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(giftCardCatalogService.findBySku("U-TAKEN")).thenReturn(Optional.empty());

        service.updateCatalogItem(id, new UpdateRedemptionCatalogItemRequest(
                null, null, null, null, null, null, null, "U-TAKEN", null, null, null));

        assertThat(item.getProviderItemId()).isEqualTo("U-TAKEN");
        verify(catalogItemRepository, never())
                .existsByOwnerClientIdAndProviderItemIdAndCategoryAndIsActiveTrueAndDeletedFalse(any(), any(), any());
    }

    @Test
    void updateCatalogItem_backfillsMissingProviderImageUrl_forItemsPredatingTheColumn() {
        // Items created before the brand image was stamped have none; saving the form fills it in
        // without the admin having to change the SKU.
        UUID id = UUID.randomUUID();
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeCashItem()
                .providerItemId("U-SAME")
                .build();
        item.setId(id);
        when(catalogItemRepository.findByIdAndOwnerClientIdAndDeletedFalseAndIsBankTransferFalse(id, CLIENT_ID))
                .thenReturn(Optional.of(item));
        when(catalogItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(giftCardCatalogService.findBySku("U-SAME")).thenReturn(Optional.of(new GiftCardSkuResponse(
                "U-SAME", "Legacy Card", "Legacy",
                "https://cdn.example.com/brands/legacy.png", "USD", "FIXED",
                new BigDecimal("10.00"), BigDecimal.ZERO, BigDecimal.ZERO)));

        service.updateCatalogItem(id, new UpdateRedemptionCatalogItemRequest(
                null, null, null, null, null, null, null, "U-SAME", null, null, null));

        assertThat(item.getProviderImageUrl()).isEqualTo("https://cdn.example.com/brands/legacy.png");
    }

    @Test
    void updateCatalogItem_keepsProviderImageUrl_whenSkuIsUnchanged() {
        // Re-saving the form resends the same SKU — that must not re-hit the catalog or drop the image.
        UUID id = UUID.randomUUID();
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeCashItem()
                .providerItemId("U-SAME")
                .providerImageUrl("https://cdn.example.com/brands/same.png")
                .build();
        item.setId(id);
        when(catalogItemRepository.findByIdAndOwnerClientIdAndDeletedFalseAndIsBankTransferFalse(id, CLIENT_ID))
                .thenReturn(Optional.of(item));
        when(catalogItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateCatalogItem(id, new UpdateRedemptionCatalogItemRequest(
                "Renamed", null, null, null, null, null, null, "U-SAME", null, null, null));

        verify(giftCardCatalogService, never()).findBySku(any());
        assertThat(item.getProviderImageUrl()).isEqualTo("https://cdn.example.com/brands/same.png");
    }

    @Test
    void createCatalogItem_stampsVariableBounds_fromResolvedSku() {
        when(catalogItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(giftCardCatalogService.findBySku("U-VAR")).thenReturn(Optional.of(new GiftCardSkuResponse(
                "U-VAR", "Globex Flex", "Globex", null, "USD", "VARIABLE",
                BigDecimal.ZERO, new BigDecimal("5.00"), new BigDecimal("500.00"))));

        CreateRedemptionCatalogItemRequest request = new CreateRedemptionCatalogItemRequest(
                "Globex Flex", null, RedemptionCategory.CASH,
                "cash", new BigDecimal("3.00"), RedemptionProcessingMode.INSTANT,
                List.of(), "U-VAR", false, 0, null);

        service.createCatalogItem(request);

        ArgumentCaptor<RedemptionCatalogItem> captor = ArgumentCaptor.forClass(RedemptionCatalogItem.class);
        verify(catalogItemRepository).save(captor.capture());
        RedemptionCatalogItem entity = captor.getValue();
        assertThat(entity.getValueType()).isEqualTo(RedemptionValueType.VARIABLE);
        assertThat(entity.getDefaultMinRedemptionAmount()).isEqualByComparingTo("5.00");
        assertThat(entity.getDefaultMaxRedemptionAmount()).isEqualByComparingTo("500.00");
    }

    @Test
    void createCatalogItem_variableSkuWithZeroVendorFloor_keepsAdminEnteredMin() {
        // Repro of the DB check-constraint failure: XTRM VARIABLE SKUs report minValue 0 ("any amount"),
        // but default_min_redemption_amount must be > 0. The admin-entered min is kept as the floor.
        when(catalogItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(giftCardCatalogService.findBySku("U-ANY")).thenReturn(Optional.of(new GiftCardSkuResponse(
                "U-ANY", "Amazon Any", "Amazon", null, "USD", "VARIABLE",
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("2000.00"))));

        CreateRedemptionCatalogItemRequest request = new CreateRedemptionCatalogItemRequest(
                "Amazon Any", null, RedemptionCategory.CASH,
                "cash", new BigDecimal("5.00"), RedemptionProcessingMode.INSTANT,
                List.of(), "U-ANY", false, 0, null);

        service.createCatalogItem(request);

        ArgumentCaptor<RedemptionCatalogItem> captor = ArgumentCaptor.forClass(RedemptionCatalogItem.class);
        verify(catalogItemRepository).save(captor.capture());
        RedemptionCatalogItem entity = captor.getValue();
        assertThat(entity.getValueType()).isEqualTo(RedemptionValueType.VARIABLE);
        assertThat(entity.getDefaultMinRedemptionAmount()).isEqualByComparingTo("5.00");
        assertThat(entity.getDefaultMaxRedemptionAmount()).isEqualByComparingTo("2000.00");
    }

    @Test
    void createCatalogItem_nonCash_skipsGiftCardSkuLookup_providerItemIdIsAXoxodayId() {
        // providerItemId means an XTRM gift-card SKU for CASH but a Xoxoday product id for NON_CASH.
        // The XTRM catalog must not be consulted for NON_CASH — a coincidental match would stamp the
        // wrong value type and bounds onto the item.
        when(catalogItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateRedemptionCatalogItemRequest request = new CreateRedemptionCatalogItemRequest(
                "Movie Voucher", null, RedemptionCategory.NON_CASH,
                "points", new BigDecimal("7.00"), RedemptionProcessingMode.INSTANT,
                List.of(), "XOXO-9001", false, 0, null);

        service.createCatalogItem(request);

        verify(giftCardCatalogService, never()).findBySku(any());
        ArgumentCaptor<RedemptionCatalogItem> captor = ArgumentCaptor.forClass(RedemptionCatalogItem.class);
        verify(catalogItemRepository).save(captor.capture());
        RedemptionCatalogItem entity = captor.getValue();
        // Open-value: no vendor value type, no ceiling, admin-entered floor kept as-is.
        assertThat(entity.getValueType()).isNull();
        assertThat(entity.getDefaultMaxRedemptionAmount()).isNull();
        assertThat(entity.getDefaultMinRedemptionAmount()).isEqualByComparingTo("7.00");
        assertThat(entity.getProviderItemId()).isEqualTo("XOXO-9001");
        // No SKU resolution → no vendor brand image either; the illustration stands in.
        assertThat(entity.getProviderImageUrl()).isNull();
    }

    @Test
    void activateCatalogItem_rejects_whenActiveDuplicateExists() {
        // Activation is where a live-duplicate SKU is caught (create allows inactive drafts to share one).
        UUID id = UUID.randomUUID();
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeNonCashItem()
                .ownerClientId(CLIENT_ID).providerItemId("DUP-001").isActive(false).build();
        when(catalogItemRepository.findByIdAndOwnerClientIdAndDeletedFalseAndIsBankTransferFalse(id, CLIENT_ID))
                .thenReturn(Optional.of(item));
        when(catalogItemRepository.existsByOwnerClientIdAndProviderItemIdAndCategoryAndIsActiveTrueAndDeletedFalse(
                CLIENT_ID, "DUP-001", RedemptionCategory.NON_CASH)).thenReturn(true);

        assertThatThrownBy(() -> service.activateCatalogItem(id))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(CONFLICT));
        verify(catalogItemRepository, never()).save(any());
    }

    @Test
    void activateCatalogItem_rejects_secondLiveCashCardOnTheSameSku() {
        // Only one live store card per gift-card SKU: the vendor SKU is the payout target, so two live
        // cards on one SKU are indistinguishable to a seller and to XTRM.
        UUID id = UUID.randomUUID();
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeCashItem()
                .ownerClientId(CLIENT_ID).providerItemId("U561621").isActive(false).build();
        when(catalogItemRepository.findByIdAndOwnerClientIdAndDeletedFalseAndIsBankTransferFalse(id, CLIENT_ID))
                .thenReturn(Optional.of(item));
        when(catalogItemRepository.existsByOwnerClientIdAndProviderItemIdAndCategoryAndIsActiveTrueAndDeletedFalse(
                CLIENT_ID, "U561621", RedemptionCategory.CASH)).thenReturn(true);

        assertThatThrownBy(() -> service.activateCatalogItem(id))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Another active catalog item with this providerItemId already exists");
        assertThat(item.isActive()).isFalse();
        verify(catalogItemRepository, never()).save(any());
    }

    @Test
    void activateCatalogItem_allows_whenTheSameSkuIsOnlyHeldByAnInactiveCard() {
        // The complement of the rule, and the case that used to fail at the DB index (V50): retiring a
        // card frees its SKU, so another card may take it live.
        UUID id = UUID.randomUUID();
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeCashItem()
                .ownerClientId(CLIENT_ID).providerItemId("U163059").isActive(false).build();
        item.setId(id);
        when(catalogItemRepository.findByIdAndOwnerClientIdAndDeletedFalseAndIsBankTransferFalse(id, CLIENT_ID))
                .thenReturn(Optional.of(item));
        when(catalogItemRepository.existsByOwnerClientIdAndProviderItemIdAndCategoryAndIsActiveTrueAndDeletedFalse(
                CLIENT_ID, "U163059", RedemptionCategory.CASH)).thenReturn(false);
        when(catalogItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RedemptionCatalogItemResponse result = service.activateCatalogItem(id);

        assertThat(result.isActive()).isTrue();
        assertThat(item.isActive()).isTrue();
    }

    @Test
    void updateCatalogItem_rejects_anotherClientsItem() {
        // Model 2 write isolation: an item not owned by the caller's client is invisible → 404.
        UUID id = UUID.randomUUID();
        when(catalogItemRepository.findByIdAndOwnerClientIdAndDeletedFalseAndIsBankTransferFalse(id, CLIENT_ID)).thenReturn(Optional.empty());

        UpdateRedemptionCatalogItemRequest request = new UpdateRedemptionCatalogItemRequest(
                "Hijack", null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.updateCatalogItem(id, request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(catalogItemRepository, never()).save(any());
    }

    @Test
    void deleteCatalogItem_softDeletes_setsDeletedTrue() {
        UUID id = UUID.randomUUID();
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeNonCashItem().build();
        item.setId(id);
        when(catalogItemRepository.findByIdAndOwnerClientIdAndDeletedFalseAndIsBankTransferFalse(id, CLIENT_ID)).thenReturn(Optional.of(item));
        when(catalogItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.deleteCatalogItem(id);

        assertThat(item.isDeleted()).isTrue();
        verify(catalogItemRepository).save(item);
    }

    @Test
    void deleteCatalogItem_rejects_anotherClientsItem() {
        UUID id = UUID.randomUUID();
        when(catalogItemRepository.findByIdAndOwnerClientIdAndDeletedFalseAndIsBankTransferFalse(id, CLIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteCatalogItem(id))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(catalogItemRepository, never()).save(any());
    }

    @Test
    void activateCatalogItem_rejects_nonCashWithoutProviderItemId() {
        UUID id = UUID.randomUUID();
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeNonCashItem()
                .providerItemId(null)
                .isActive(false)
                .build();
        item.setId(id);
        when(catalogItemRepository.findByIdAndOwnerClientIdAndDeletedFalseAndIsBankTransferFalse(id, CLIENT_ID)).thenReturn(Optional.of(item));

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
        when(catalogItemRepository.findByIdAndOwnerClientIdAndDeletedFalseAndIsBankTransferFalse(id, CLIENT_ID)).thenReturn(Optional.of(item));
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
        when(catalogItemRepository.findByIdAndOwnerClientIdAndDeletedFalseAndIsBankTransferFalse(id, CLIENT_ID)).thenReturn(Optional.of(item));
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
    void triggerXoxodaySync_isDisabled_underClientOwnedModel() {
        // Model 2: Xoxoday sync is disabled (global @Async job has no client owner). It must not
        // run the sync job and should surface a clear business error.
        assertThatThrownBy(() -> service.triggerXoxodaySync())
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("disabled");
        verify(syncJobService, never()).submitSyncJob();
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

        when(catalogItemRepository.findByIdAndOwnerClientIdAndDeletedFalseAndIsBankTransferFalse(id, CLIENT_ID)).thenReturn(Optional.of(item));
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

        when(catalogItemRepository.findByIdAndOwnerClientIdAndDeletedFalseAndIsBankTransferFalse(id, CLIENT_ID)).thenReturn(Optional.of(item));
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
