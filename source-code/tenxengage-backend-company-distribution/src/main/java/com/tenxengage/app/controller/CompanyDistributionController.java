package com.tenxengage.app.controller;

import com.tenxengage.app.audit.Audited;
import com.tenxengage.app.dto.request.CreateCompanyDistributionRequest;
import com.tenxengage.app.dto.response.CompanyAwardResponse;
import com.tenxengage.app.dto.response.CompanyDistributionResponse;
import com.tenxengage.app.dto.response.DistributionCatalogItemResponse;
import com.tenxengage.app.dto.response.DistributionRecipientResponse;
import com.tenxengage.app.dto.response.PaginatedResponse;
import com.tenxengage.app.entity.CompanyDistribution;
import com.tenxengage.app.entity.enums.DistributionRail;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.dto.response.GiftCardSkuResponse;
import com.tenxengage.app.service.GiftCardCatalogService;
import com.tenxengage.app.service.CompanyDistributionQueryService;
import com.tenxengage.app.service.CompanyDistributionService;
import com.tenxengage.app.service.CompanyWalletXtrmSyncService;
import com.tenxengage.app.service.DistributionRecipientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * The Distribution Store, Distribution History and Company Award History surfaces.
 *
 * <p>Visibility is PARTNER_ADMIN and PARTNER_SELLER only. A CLIENT_ADMIN holds none of these permissions —
 * they can fund a company wallet but have no application view of how it was spent, which is a deliberate
 * governance choice (design OQ-13).</p>
 */
@RestController
@RequestMapping("/api/v1/redemption/distribution")
@Tag(name = "Company Distribution", description = "Partner admin distributing the company wallet to its sellers")
public class CompanyDistributionController {

    private final CompanyDistributionService distributionService;
    private final CompanyDistributionQueryService queryService;
    private final GiftCardCatalogService giftCardCatalogService;
    private final DistributionRecipientService recipientService;
    private final TenantValidator tenantValidator;
    private final CompanyWalletXtrmSyncService companyWalletSync;

    public CompanyDistributionController(CompanyDistributionService distributionService,
                                          CompanyDistributionQueryService queryService,
                                          DistributionRecipientService recipientService,
                                          GiftCardCatalogService giftCardCatalogService,
                                          TenantValidator tenantValidator,
                                          CompanyWalletXtrmSyncService companyWalletSync) {
        this.distributionService = distributionService;
        this.queryService = queryService;
        this.recipientService = recipientService;
        this.giftCardCatalogService = giftCardCatalogService;
        this.tenantValidator = tenantValidator;
        this.companyWalletSync = companyWalletSync;
    }

    /**
     * Candidate recipients for a rail, ineligible ones included with a reason so the UI can explain rather
     * than silently omit them.
     */
    @GetMapping("/recipients")
    @RequiresPermission("action.redemption.distribute")
    @Operation(summary = "List the company's sellers with per-rail payout readiness")
    public ResponseEntity<List<DistributionRecipientResponse>> listRecipients(
            @RequestParam DistributionRail rail) {
        UUID companyId = requireCompany();
        return ResponseEntity.ok(recipientService.listRecipients(
                tenantValidator.getCurrentClientId(), companyId, rail));
    }

    /**
     * The gift cards this admin can distribute.
     *
     * <p>Not the personal store's browse endpoint: that computes {@code canAfford} against the caller's own
     * wallet, which is the wrong wallet here. This returns only items submit will actually accept — active,
     * client-owned, not the reserved bank-transfer card, and carrying a vendor SKU — with the effective amount
     * bounds so the picker constrains exactly what the server enforces.</p>
     */
    /**
     * The provider's whole digital gift-card catalogue.
     *
     * <p>Separate from the client-admin picker at {@code /admin/redemption-catalog/gift-card-catalog}, which is
     * gated on {@code catalog.manage} — a permission a partner admin does not hold. Same underlying (cached)
     * XTRM call, exposed to the one permission that already means "may spend the company wallet".</p>
     *
     * <p>This deliberately is not limited to the client's curated catalogue: a partner admin distributing to
     * their own sellers picks any SKU the provider offers, and the server provisions a hidden catalog row for
     * whichever they choose.</p>
     */
    @GetMapping("/gift-cards")
    @RequiresPermission("action.redemption.distribute")
    @Operation(summary = "List every provider gift-card SKU a partner admin can distribute")
    public ResponseEntity<List<GiftCardSkuResponse>> distributableGiftCards() {
        requireCompany();
        return ResponseEntity.ok(giftCardCatalogService.listGiftCardSkus());
    }

    /**
     * The client's curated gift cards. Superseded by {@code /gift-cards} above, which offers the provider's
     * full catalogue; kept because older clients still call it.
     */
    @GetMapping("/catalog")
    @RequiresPermission("action.redemption.distribute")
    @Operation(summary = "List the client's curated gift cards (legacy — prefer /gift-cards)")
    public ResponseEntity<List<DistributionCatalogItemResponse>> listCatalog() {
        return ResponseEntity.ok(queryService.listDistributableCatalog());
    }

    /**
     * Create a distribution. Returns <b>202</b>, not 201: the funds are reserved and the rows are committed,
     * but each recipient is paid after the transaction commits, so the work is accepted rather than finished.
     */
    @PostMapping
    @RequiresPermission("action.redemption.distribute")
    @Audited(action = "DISTRIBUTED", resourceType = "COMPANY_DISTRIBUTION",
             resourceId = "#result.body.id.toString()",
             description = "Partner admin distributed the company wallet to sellers")
    @Operation(summary = "Distribute the company wallet to selected sellers")
    public ResponseEntity<CompanyDistributionResponse> create(
            @Valid @RequestBody CreateCompanyDistributionRequest request) {
        // Restate the balance from XTRM before the reserve, so the affordability check is made against the
        // money that is actually there rather than whatever we last saw. Outside submit's transaction on
        // purpose: the reserve holds a row lock, and a vendor call must not run while it does.
        companyWalletSync.syncIfConnected(tenantValidator.getCurrentPartnerCompanyId());
        CompanyDistribution header = distributionService.submit(request);
        // Re-read through the query service so the response carries derived status and resolved names
        // instead of a half-populated snapshot of what submit happened to have in memory.
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(queryService.getDistribution(header.getId()));
    }

    /** Distribution History — every distribution from this company's wallet, by any of its admins. */
    @GetMapping
    @RequiresPermission("action.redemption.view_distribution_history")
    @Operation(summary = "List this company's distributions")
    public ResponseEntity<PaginatedResponse<CompanyDistributionResponse>> list(
            @RequestParam(required = false) DistributionRail rail,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginatedResponse.from(queryService.listCompanyDistributions(
                rail, startOfDay(dateFrom), endOfDay(dateTo),
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt")))));
    }

    /** One distribution with its per-recipient outcomes. */
    @GetMapping("/{id}")
    @RequiresPermission("action.redemption.view_distribution_history")
    @Operation(summary = "Get one distribution with per-recipient detail")
    public ResponseEntity<CompanyDistributionResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(queryService.getDistribution(id));
    }

    /**
     * Company Award History — the seller's own awards. Distributions are excluded from their personal
     * Transaction History, so this is the only place they appear.
     */
    @GetMapping("/awards")
    @RequiresPermission("action.redemption.view_company_awards")
    @Operation(summary = "List the rewards this seller received from company admins")
    public ResponseEntity<PaginatedResponse<CompanyAwardResponse>> myAwards(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginatedResponse.from(queryService.listMyAwards(
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt")))));
    }

    /** One award's detail — backs the "View details" action on each Company Award History row. */
    @GetMapping("/awards/{awardId}")
    @RequiresPermission("action.redemption.view_company_awards")
    @Operation(summary = "Get one award this seller received")
    public ResponseEntity<CompanyAwardResponse> getMyAward(@PathVariable UUID awardId) {
        return ResponseEntity.ok(queryService.getMyAward(awardId));
    }

    private UUID requireCompany() {
        UUID companyId = tenantValidator.getCurrentPartnerCompanyId();
        if (companyId == null) {
            throw new AccessDeniedException("Distributing requires an associated partner company");
        }
        return companyId;
    }

    private static Instant startOfDay(LocalDate d) {
        return d == null ? null : d.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static Instant endOfDay(LocalDate d) {
        return d == null ? null : d.atTime(23, 59, 59).toInstant(ZoneOffset.UTC);
    }
}
