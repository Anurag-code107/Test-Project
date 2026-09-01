package com.tenxengage.app.service.redemption;

import com.tenxengage.app.dto.request.redemption.RedemptionAdminHistoryFilters;
import com.tenxengage.app.dto.request.redemption.RedemptionHistoryFilters;
import com.tenxengage.app.dto.response.RedemptionRequestDetailResponse;
import com.tenxengage.app.dto.response.RedemptionRequestResponse;
import com.tenxengage.app.dto.response.redemption.RedemptionAdminHistoryResponse;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.service.PermissionService;
import com.tenxengage.app.repository.redemption.RedemptionHistoryRepository;
import com.tenxengage.app.security.TenantValidator;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RedemptionHistoryService {

    private final RedemptionHistoryRepository historyRepository;
    private final RedemptionRequestRepository redemptionRequestRepository;
    private final RedemptionCatalogItemRepository catalogItemRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final TenantValidator tenantValidator;
    // @Lazy-injected via constructor to break the circular dependency with ReturnService
    private final ReturnService returnService;

    public RedemptionHistoryService(RedemptionHistoryRepository historyRepository,
                                    RedemptionRequestRepository redemptionRequestRepository,
                                    RedemptionCatalogItemRepository catalogItemRepository,
                                    UserRepository userRepository,
                                    PermissionService permissionService,
                                    TenantValidator tenantValidator,
                                    @Lazy ReturnService returnService) {
        this.historyRepository = historyRepository;
        this.redemptionRequestRepository = redemptionRequestRepository;
        this.catalogItemRepository = catalogItemRepository;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.tenantValidator = tenantValidator;
        this.returnService = returnService;
    }

    @Transactional(readOnly = true)
    public Page<RedemptionRequestResponse> getPersonalHistory(
            UUID userId,
            RedemptionHistoryFilters filters,
            Pageable pageable) {

        UUID clientId = tenantValidator.getCurrentClientId();

        Page<RedemptionRequest> page = historyRepository.findPersonalHistory(
                userId,
                clientId,
                filters.status(),
                filters.category(),
                filters.dateFromInstant(),
                filters.dateToInstant(),
                pageable);

        Map<UUID, RedemptionCatalogItem> catalogItems = loadCatalogItems(page.getContent());
        Map<UUID, String> catalogNames = catalogItems.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getName()));

        List<UUID> pageRedemptionIds = page.getContent().stream().map(RedemptionRequest::getId).toList();
        Set<UUID> redemptionIdsWithActiveReturns =
                returnService.getRedemptionIdsWithActiveReturns(pageRedemptionIds, clientId);

        return page.map(req -> {
            String catalogName = catalogNames.getOrDefault(req.getCatalogItemId(), "(unknown)");
            RedemptionCatalogItem catalogItem = catalogItems.get(req.getCatalogItemId());
            boolean eligible = catalogItem != null
                    && returnService.isReturnEligible(req, catalogItem, clientId, redemptionIdsWithActiveReturns);
            return RedemptionRequestResponse.from(req, catalogName, eligible);
        });
    }

    @Transactional(readOnly = true)
    public RedemptionRequestDetailResponse getRedemptionDetail(UUID id, UUID userId) {
        UUID clientId = tenantValidator.getCurrentClientId();
        boolean isAdmin = permissionService.resolveEffectivePermissions(userId)
                .contains("action.redemption.view_all_history");

        RedemptionRequest req = isAdmin
                ? redemptionRequestRepository.findByIdAndClientId(id, clientId)
                        .orElseThrow(() -> new ResourceNotFoundException("RedemptionRequest", "id", id))
                : redemptionRequestRepository.findByIdAndClientIdAndUserId(id, clientId, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("RedemptionRequest", "id", id));

        String catalogItemName = catalogItemRepository.findById(req.getCatalogItemId())
                .map(RedemptionCatalogItem::getName)
                .orElse("(unknown)");

        // imageUrl and linkedReturnId: null until catalog image lookup and F-06 deploy
        return RedemptionRequestDetailResponse.from(req, catalogItemName, null, null);
    }

    @Transactional(readOnly = true)
    public Page<RedemptionRequestResponse> getCompanyHistory(
            UUID userId,
            RedemptionHistoryFilters filters,
            Pageable pageable) {

        UUID clientId = tenantValidator.getCurrentClientId();
        UUID partnerCompanyId = tenantValidator.getCurrentPartnerCompanyId();

        if (partnerCompanyId == null) {
            return Page.empty(pageable);
        }

        // Query by partnerCompanyId across ALL company wallets (all currencies)
        // to avoid silently dropping redemptions from secondary currency wallets.
        Page<RedemptionRequest> page = historyRepository.findCompanyHistoryByPartnerCompany(
                clientId,
                partnerCompanyId,
                filters.status(),
                filters.category(),
                filters.dateFromInstant(),
                filters.dateToInstant(),
                pageable);

        Map<UUID, RedemptionCatalogItem> catalogItems = loadCatalogItems(page.getContent());
        Map<UUID, String> catalogNames = catalogItems.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getName()));

        List<UUID> pageRedemptionIds = page.getContent().stream().map(RedemptionRequest::getId).toList();
        Set<UUID> redemptionIdsWithActiveReturns =
                returnService.getRedemptionIdsWithActiveReturns(pageRedemptionIds, clientId);

        return page.map(req -> {
            String catalogName = catalogNames.getOrDefault(req.getCatalogItemId(), "(unknown)");
            RedemptionCatalogItem catalogItem = catalogItems.get(req.getCatalogItemId());
            boolean eligible = catalogItem != null
                    && returnService.isReturnEligible(req, catalogItem, clientId, redemptionIdsWithActiveReturns);
            return RedemptionRequestResponse.from(req, catalogName, eligible);
        });
    }

    @Transactional(readOnly = true)
    public Page<RedemptionAdminHistoryResponse> getTenantHistory(
            RedemptionAdminHistoryFilters filters,
            Pageable pageable) {

        UUID clientId = tenantValidator.getCurrentClientId();
        RedemptionHistoryFilters base = filters.toBaseFilters();

        Page<RedemptionRequest> page = historyRepository.findTenantHistory(
                clientId,
                filters.userId(),
                filters.companyId(),
                base.status(),
                base.category(),
                base.dateFromInstant(),
                base.dateToInstant(),
                pageable);

        Map<UUID, String> catalogNames = loadCatalogNames(page.getContent());
        Map<UUID, User> users = loadUsers(page.getContent());

        return page.map(req -> {
            String catalogItemName = catalogNames.getOrDefault(req.getCatalogItemId(), "(unknown)");
            User user = users.get(req.getUserId());
            String displayName = user != null
                    ? (nullToEmpty(user.getFirstName()) + " " + nullToEmpty(user.getLastName())).strip()
                    : "(unknown)";
            String companyName = (user != null && user.getPartnerCompany() != null)
                    ? user.getPartnerCompany().getName() : null;
            return RedemptionAdminHistoryResponse.from(req, catalogItemName, displayName, companyName);
        });
    }

    private Map<UUID, User> loadUsers(List<RedemptionRequest> requests) {
        List<UUID> ids = requests.stream()
                .map(RedemptionRequest::getUserId)
                .distinct()
                .collect(Collectors.toList());
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    private Map<UUID, String> loadCatalogNames(List<RedemptionRequest> requests) {
        List<UUID> ids = requests.stream()
                .map(RedemptionRequest::getCatalogItemId)
                .distinct()
                .collect(Collectors.toList());
        return catalogItemRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(RedemptionCatalogItem::getId, RedemptionCatalogItem::getName));
    }

    private Map<UUID, RedemptionCatalogItem> loadCatalogItems(List<RedemptionRequest> requests) {
        List<UUID> ids = requests.stream()
                .map(RedemptionRequest::getCatalogItemId)
                .distinct()
                .collect(Collectors.toList());
        return catalogItemRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(RedemptionCatalogItem::getId, item -> item));
    }
}
