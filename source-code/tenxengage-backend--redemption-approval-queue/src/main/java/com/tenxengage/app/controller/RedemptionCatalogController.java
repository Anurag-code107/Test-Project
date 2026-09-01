package com.tenxengage.app.controller;

import com.tenxengage.app.dto.response.CatalogBrowseItemResponse;
import com.tenxengage.app.dto.response.PaginatedResponse;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.service.RedemptionCatalogBrowseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/redemption/catalog")
@Tag(name = "Redemption Catalog", description = "Partner currency-aware catalog browse")
public class RedemptionCatalogController {

    private final RedemptionCatalogBrowseService browseService;

    public RedemptionCatalogController(RedemptionCatalogBrowseService browseService) {
        this.browseService = browseService;
    }

    @GetMapping
    @RequiresPermission("module.redemption_store")
    @Operation(summary = "Browse partner catalog", description = "Currency-aware, region-filtered catalog for the caller")
    public ResponseEntity<PaginatedResponse<CatalogBrowseItemResponse>> browseCatalog(
            @RequestParam(required = false) String currencyId,
            @RequestParam(required = false) String region,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        if (pageSize > 50) {
            throw new IllegalArgumentException("pageSize must not exceed 50");
        }
        Pageable pageable = PageRequest.of(page, pageSize);
        return ResponseEntity.ok(PaginatedResponse.from(
                browseService.browsePartnerCatalog(currencyId, region, pageable)));
    }

    @GetMapping("/{id}")
    @RequiresPermission("module.redemption_store")
    @Operation(summary = "Get catalog item detail", description = "404 if item not active, not enabled for tenant, or region-excluded")
    public ResponseEntity<CatalogBrowseItemResponse> getCatalogItem(
            @PathVariable UUID id,
            @RequestParam(required = false) String region) {
        return ResponseEntity.ok(browseService.getPartnerCatalogItem(id, region));
    }
}
