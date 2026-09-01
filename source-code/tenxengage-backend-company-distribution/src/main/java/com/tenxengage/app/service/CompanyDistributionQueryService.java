package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.DistributionCatalogItemResponse;
import com.tenxengage.app.entity.ClientCatalogItemConfig;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.repository.ClientCatalogItemConfigRepository;
import com.tenxengage.app.dto.response.CompanyAwardResponse;
import com.tenxengage.app.dto.response.CompanyDistributionItemResponse;
import com.tenxengage.app.dto.response.CompanyDistributionResponse;
import com.tenxengage.app.entity.CompanyDistribution;
import com.tenxengage.app.entity.CompanyDistributionItem;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.DistributionItemStatus;
import com.tenxengage.app.entity.enums.DistributionRail;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.CompanyDistributionItemRepository;
import com.tenxengage.app.repository.CompanyDistributionRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.TenantValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reads for the two distribution surfaces.
 *
 * <p><b>Status is derived here, never stored twice.</b> A payout item reports its
 * {@code redemption_requests.status}; a wallet-transfer item reports its own. Because there is exactly one
 * owner of the truth per item, status can never disagree with where the money actually is.</p>
 *
 * <p>Both surfaces are scoped so one company cannot see another's, and one seller cannot see another's
 * awards — the scoping is in the query, not applied after loading.</p>
 */
@Service
public class CompanyDistributionQueryService {

    private final TenantValidator tenantValidator;
    private final CompanyDistributionRepository distributionRepository;
    private final CompanyDistributionItemRepository itemRepository;
    private final RedemptionRequestRepository redemptionRequestRepository;
    private final RedemptionCatalogItemRepository catalogItemRepository;
    private final ClientCatalogItemConfigRepository catalogConfigRepository;
    private final UserRepository userRepository;
    private final PartnerCompanyRepository partnerCompanyRepository;

    public CompanyDistributionQueryService(TenantValidator tenantValidator,
                                            CompanyDistributionRepository distributionRepository,
                                            CompanyDistributionItemRepository itemRepository,
                                            RedemptionRequestRepository redemptionRequestRepository,
                                            RedemptionCatalogItemRepository catalogItemRepository,
                                            ClientCatalogItemConfigRepository catalogConfigRepository,
                                            UserRepository userRepository,
                                            PartnerCompanyRepository partnerCompanyRepository) {
        this.tenantValidator = tenantValidator;
        this.distributionRepository = distributionRepository;
        this.itemRepository = itemRepository;
        this.redemptionRequestRepository = redemptionRequestRepository;
        this.catalogItemRepository = catalogItemRepository;
        this.catalogConfigRepository = catalogConfigRepository;
        this.userRepository = userRepository;
        this.partnerCompanyRepository = partnerCompanyRepository;
    }

    // ------------------------------------------------------------------ partner admin

    /**
     * Distribution History: every distribution drawn from the caller's company wallet, by any of its admins
     * (§6.2 A). The list omits per-recipient rows but still resolves their statuses, because the rollup and
     * the settled total both depend on them.
     */
    @Transactional(readOnly = true)
    public Page<CompanyDistributionResponse> listCompanyDistributions(DistributionRail rail,
                                                                      Instant dateFrom, Instant dateTo,
                                                                      Pageable pageable) {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID companyId = requireCompany();

        Page<CompanyDistribution> page = distributionRepository
                .findCompanyHistory(clientId, companyId, rail, dateFrom, dateTo, pageable);
        if (page.isEmpty()) {
            return page.map(d -> null);
        }

        // One query for all items on the page, then one for their payout legs — not N+1 per header.
        List<UUID> headerIds = page.getContent().stream().map(CompanyDistribution::getId).toList();
        Map<UUID, List<CompanyDistributionItem>> itemsByHeader = itemRepository.findByDistributionIdIn(headerIds)
                .stream().collect(Collectors.groupingBy(CompanyDistributionItem::getDistributionId));
        Map<UUID, RedemptionRequest> legs = legsFor(itemsByHeader.values().stream().flatMap(List::stream).toList());
        Map<UUID, String> names = namesFor(page.getContent().stream()
                .map(CompanyDistribution::getInitiatedByUserId).toList());
        Map<UUID, String> catalogNames = catalogNamesFor(page.getContent());

        return page.map(d -> {
            List<CompanyDistributionItem> items = itemsByHeader.getOrDefault(d.getId(), List.of());
            List<String> statuses = items.stream().map(i -> resolveStatus(i, legs)).toList();
            return CompanyDistributionResponse.from(
                    d,
                    catalogNames.get(d.getCatalogItemId()),
                    names.get(d.getInitiatedByUserId()),
                    rollup(statuses),
                    settledTotal(items, statuses),
                    List.of());   // per-recipient rows are the detail view's job
        });
    }

    /** One distribution with its per-recipient rows. Company-scoped, so another company's id 404s. */
    @Transactional(readOnly = true)
    public CompanyDistributionResponse getDistribution(UUID distributionId) {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID companyId = requireCompany();

        CompanyDistribution d = distributionRepository
                .findByIdAndClientIdAndPartnerCompanyId(distributionId, clientId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("CompanyDistribution", "id", distributionId));

        List<CompanyDistributionItem> items = itemRepository.findByDistributionIdOrderByCreatedAtAsc(d.getId());
        Map<UUID, RedemptionRequest> legs = legsFor(items);
        Map<UUID, User> recipients = usersFor(items.stream()
                .map(CompanyDistributionItem::getRecipientUserId).toList());
        List<String> statuses = items.stream().map(i -> resolveStatus(i, legs)).toList();

        List<CompanyDistributionItemResponse> rows = new java.util.ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            CompanyDistributionItem item = items.get(i);
            RedemptionRequest leg = legFor(item, legs);
            User u = recipients.get(item.getRecipientUserId());
            rows.add(new CompanyDistributionItemResponse(
                    item.getId(),
                    item.getRecipientUserId(),
                    displayName(u),
                    u == null ? null : u.getEmail(),
                    item.getAmount(),
                    statuses.get(i),
                    destination(item, leg),
                    paymentTransactionId(leg),
                    failureReason(item, leg),
                    item.getSettledAt() != null ? item.getSettledAt()
                            : (leg == null ? null : leg.getCompletedAt())));
        }

        return CompanyDistributionResponse.from(
                d,
                catalogNamesFor(List.of(d)).get(d.getCatalogItemId()),
                namesFor(List.of(d.getInitiatedByUserId())).get(d.getInitiatedByUserId()),
                rollup(statuses),
                settledTotal(items, statuses),
                rows);
    }

    // ------------------------------------------------------------------ distributable catalog

    /**
     * The gift cards this client can distribute.
     *
     * <p>Filtered to what {@code resolveRail} will actually accept, so the picker cannot offer something submit
     * would reject: active, not deleted, owned by this client, not the reserved bank-transfer card, and
     * carrying a vendor SKU. A SKU-less item is undistributable, so listing it would only produce a confusing
     * failure at submit.</p>
     *
     * <p>Bounds are the effective ones (client override applied), matching what submit enforces.</p>
     */
    @Transactional(readOnly = true)
    public List<DistributionCatalogItemResponse> listDistributableCatalog() {
        UUID clientId = tenantValidator.getCurrentClientId();
        requireCompany();   // same gate as the rest of the store — a caller with no company has no business here

        List<RedemptionCatalogItem> items = catalogItemRepository
                .findByOwnerClientIdAndIsActiveAndDeletedFalseAndIsBankTransferFalse(clientId, true)
                .stream()
                .filter(i -> i.getProviderItemId() != null && !i.getProviderItemId().isBlank())
                .toList();
        if (items.isEmpty()) {
            return List.of();
        }

        Map<UUID, ClientCatalogItemConfig> configs = configsFor(clientId, items);

        return items.stream().map(i -> {
            ClientCatalogItemConfig c = configs.get(i.getId());
            BigDecimal min = c != null && c.getMinTransactionAmountOverride() != null
                    ? c.getMinTransactionAmountOverride() : i.getDefaultMinRedemptionAmount();
            BigDecimal max = c != null && c.getMaxTransactionAmountOverride() != null
                    ? c.getMaxTransactionAmountOverride() : i.getDefaultMaxRedemptionAmount();
            return new DistributionCatalogItemResponse(
                    i.getId(),
                    i.getName(),
                    i.getDescription(),
                    // Uploaded images go through the API proxy, never the raw storage key.
                    i.getImageUrl() != null
                            ? "/api/v1/admin/redemption-catalog/" + i.getId() + "/image" : null,
                    i.getProviderImageUrl(),
                    i.getCurrencyId(),
                    i.getValueType() == null ? null : i.getValueType().name(),
                    min,
                    max);
        }).toList();
    }

    private Map<UUID, ClientCatalogItemConfig> configsFor(UUID clientId, List<RedemptionCatalogItem> items) {
        Map<UUID, ClientCatalogItemConfig> out = new HashMap<>();
        for (RedemptionCatalogItem i : items) {
            catalogConfigRepository.findByClientIdAndRedemptionCatalogItemId(clientId, i.getId())
                    .ifPresent(c -> out.put(i.getId(), c));
        }
        return out;
    }

    // ------------------------------------------------------------------ partner seller

    /** One award, scoped to the caller — a seller can never read another seller's award by id. */
    @Transactional(readOnly = true)
    public CompanyAwardResponse getMyAward(UUID awardId) {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID userId = tenantValidator.getCurrentUserId();

        CompanyDistributionItem item = itemRepository
                .findByIdAndClientIdAndRecipientUserId(awardId, clientId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("CompanyAward", "id", awardId));

        return toAward(item, legsFor(List.of(item)));
    }

    /** Company Award History: what this seller received. Scoped to them — never another seller's awards. */
    @Transactional(readOnly = true)
    public Page<CompanyAwardResponse> listMyAwards(Pageable pageable) {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID userId = tenantValidator.getCurrentUserId();

        Page<CompanyDistributionItem> page = itemRepository.findAwardsForRecipient(clientId, userId, pageable);
        if (page.isEmpty()) {
            return page.map(i -> null);
        }

        Map<UUID, RedemptionRequest> legs = legsFor(page.getContent());
        return page.map(item -> toAward(item, legs));
    }

    /**
     * The single mapping from item to seller-facing award, shared by the list and the detail endpoint so the
     * two cannot drift — a field appearing on one screen but not the other is exactly the bug that splitting
     * this would cause.
     */
    private CompanyAwardResponse toAward(CompanyDistributionItem item, Map<UUID, RedemptionRequest> legs) {
        CompanyDistribution d = distributionRepository.findById(item.getDistributionId()).orElse(null);
        RedemptionRequest leg = legFor(item, legs);

        String rewardName = null;
        String adminName = null;
        String companyName = null;
        if (d != null) {
            rewardName = d.getRail() == DistributionRail.GIFT_CARD
                    ? catalogNamesFor(List.of(d)).get(d.getCatalogItemId())
                    : d.getRail().getDisplayName();
            adminName = namesFor(List.of(d.getInitiatedByUserId())).get(d.getInitiatedByUserId());
            companyName = companyNamesFor(List.of(d)).get(d.getPartnerCompanyId());
        }

        return new CompanyAwardResponse(
                item.getId(),
                item.getCreatedAt(),
                d == null ? null : d.getRail(),
                d == null ? null : d.getRail().getDisplayName(),
                rewardName,
                item.getAmount(),
                d == null ? null : d.getCurrencyId(),
                resolveStatus(item, legs),
                destination(item, leg),
                adminName,
                companyName,
                d == null ? null : d.getNote(),
                failureReason(item, leg),
                paymentTransactionId(leg));
    }

    // ------------------------------------------------------------------ derivation

    /** Delegates to {@link DistributionStatusRollup} so the read model and the admin summary agree. */
    private String resolveStatus(CompanyDistributionItem item, Map<UUID, RedemptionRequest> legs) {
        return DistributionStatusRollup.itemStatus(item, legs);
    }

    private String rollup(List<String> statuses) {
        return DistributionStatusRollup.rollup(statuses);
    }


    /** What actually left the wallet, as opposed to what was requested. Differs after a partial failure. */
    private BigDecimal settledTotal(List<CompanyDistributionItem> items, List<String> statuses) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < items.size(); i++) {
            if (DistributionStatusRollup.COMPLETED.equals(statuses.get(i))) {
                sum = sum.add(items.get(i).getAmount());
            }
        }
        return sum;
    }

    /** Payout items report the snapshotted masked label; the internal rail always lands in the cash wallet. */
    private String destination(CompanyDistributionItem item, RedemptionRequest leg) {
        if (item.getRedemptionRequestId() == null) {
            return "Cash wallet";
        }
        return leg == null ? null : leg.getPayoutDestinationLabel();
    }

    /** Mirrors the existing redemption-detail rule: the vendor ref is only meaningful once completed. */
    private String paymentTransactionId(RedemptionRequest leg) {
        if (leg == null || leg.getStatus() != RedemptionStatus.COMPLETED) {
            return null;
        }
        return leg.getVendorReferenceId();
    }

    private String failureReason(CompanyDistributionItem item, RedemptionRequest leg) {
        if (item.getFailureReason() != null) {
            return item.getFailureReason();
        }
        return leg == null ? null : leg.getFailureReason();
    }

    // ------------------------------------------------------------------ bulk lookups

    /**
     * The payout leg for an item, or null when it has none.
     *
     * <p>Must not be inlined as {@code legs.get(item.getRedemptionRequestId())}: a WALLET_CREDIT item has a null
     * id, and {@code Map.of()} — which {@code legsFor} returns when no item on the page has a leg — throws NPE
     * on a null key rather than returning null. That combination is precisely a page of wallet-transfer awards,
     * so the naive form crashed the whole Company Award History for that rail.</p>
     */
    private RedemptionRequest legFor(CompanyDistributionItem item, Map<UUID, RedemptionRequest> legs) {
        UUID legId = item.getRedemptionRequestId();
        return legId == null ? null : legs.get(legId);
    }

    private Map<UUID, RedemptionRequest> legsFor(List<CompanyDistributionItem> items) {
        List<UUID> ids = items.stream()
                .map(CompanyDistributionItem::getRedemptionRequestId)
                .filter(java.util.Objects::nonNull)
                .distinct().toList();
        if (ids.isEmpty()) {
            // HashMap, not Map.of(): callers legitimately look up null ids (a non-gift-card rail has no
            // catalogItemId, a wallet transfer has no redemptionRequestId) and Map.of() throws on a null key.
            return new HashMap<>();
        }
        return redemptionRequestRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(RedemptionRequest::getId, Function.identity(), (a, b) -> a));
    }

    private Map<UUID, User> usersFor(List<UUID> ids) {
        List<UUID> distinct = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            // HashMap, not Map.of(): callers legitimately look up null ids (a non-gift-card rail has no
            // catalogItemId, a wallet transfer has no redemptionRequestId) and Map.of() throws on a null key.
            return new HashMap<>();
        }
        return userRepository.findAllById(distinct).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
    }

    private Map<UUID, String> namesFor(List<UUID> userIds) {
        Map<UUID, String> out = new HashMap<>();
        usersFor(userIds).forEach((id, u) -> out.put(id, displayName(u)));
        return out;
    }

    private Map<UUID, String> catalogNamesFor(java.util.Collection<CompanyDistribution> headers) {
        List<UUID> ids = headers.stream().map(CompanyDistribution::getCatalogItemId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            // HashMap, not Map.of(): callers legitimately look up null ids (a non-gift-card rail has no
            // catalogItemId, a wallet transfer has no redemptionRequestId) and Map.of() throws on a null key.
            return new HashMap<>();
        }
        return catalogItemRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(c -> c.getId(), c -> c.getName(), (a, b) -> a));
    }

    private Map<UUID, String> companyNamesFor(java.util.Collection<CompanyDistribution> headers) {
        List<UUID> ids = headers.stream().map(CompanyDistribution::getPartnerCompanyId).distinct().toList();
        if (ids.isEmpty()) {
            // HashMap, not Map.of(): callers legitimately look up null ids (a non-gift-card rail has no
            // catalogItemId, a wallet transfer has no redemptionRequestId) and Map.of() throws on a null key.
            return new HashMap<>();
        }
        return partnerCompanyRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(PartnerCompany::getId, PartnerCompany::getName, (a, b) -> a));
    }

    private UUID requireCompany() {
        UUID companyId = tenantValidator.getCurrentPartnerCompanyId();
        if (companyId == null) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Distribution history requires an associated partner company");
        }
        return companyId;
    }

    private static String displayName(User u) {
        if (u == null) {
            return null;
        }
        String first = u.getFirstName() == null ? "" : u.getFirstName();
        String last = u.getLastName() == null ? "" : u.getLastName();
        String name = (first + " " + last).trim();
        return name.isEmpty() ? u.getEmail() : name;
    }
}
