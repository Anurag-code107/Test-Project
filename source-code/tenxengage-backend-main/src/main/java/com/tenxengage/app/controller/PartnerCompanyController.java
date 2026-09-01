package com.tenxengage.app.controller;

import com.tenxengage.app.audit.Audited;
import com.tenxengage.app.dto.request.CreatePartnerCompanyRequest;
import com.tenxengage.app.dto.request.UpdatePartnerCompanyRequest;
import com.tenxengage.app.dto.response.PartnerCompanyResponse;
import com.tenxengage.app.entity.enums.PartnerCompanyStatus;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.service.PartnerCompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/partner-companies")
@Tag(name = "Partner Companies", description = "Partner company management — scoped by tenant")
public class PartnerCompanyController {

    private final PartnerCompanyService partnerCompanyService;

    public PartnerCompanyController(PartnerCompanyService partnerCompanyService) {
        this.partnerCompanyService = partnerCompanyService;
    }

    @GetMapping
    @Operation(summary = "List partner companies", description = "Get paginated list within current tenant")
    @RequiresPermission("action.partner_company.view")
    public ResponseEntity<Page<PartnerCompanyResponse>> getPartnerCompanies(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(description = "Search by name")
            @RequestParam(required = false) String search,
            @Parameter(description = "Filter by status (ACTIVE, INACTIVE)")
            @RequestParam(required = false) PartnerCompanyStatus status) {
        Page<PartnerCompanyResponse> companies = partnerCompanyService.getPartnerCompanies(pageable, search, status);
        return ResponseEntity.ok(companies);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get partner company by ID")
    @RequiresPermission("action.partner_company.view")
    public ResponseEntity<PartnerCompanyResponse> getPartnerCompanyById(@PathVariable UUID id) {
        PartnerCompanyResponse company = partnerCompanyService.getPartnerCompanyById(id);
        return ResponseEntity.ok(company);
    }

    @PostMapping
    @Operation(summary = "Create partner company")
    @RequiresPermission("action.partner_company.create")
    @Audited(action = "Created", resourceType = "PARTNER_COMPANY", resourceName = "#result.body.name", resourceId = "#result.body.id.toString()")
    public ResponseEntity<PartnerCompanyResponse> createPartnerCompany(
            @Valid @RequestBody CreatePartnerCompanyRequest request) {
        PartnerCompanyResponse company = partnerCompanyService.createPartnerCompany(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(company);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update partner company")
    @RequiresPermission("action.partner_company.edit")
    @Audited(action = "Edited", resourceType = "PARTNER_COMPANY", resourceName = "#result.body.name", resourceId = "#result.body.id.toString()")
    public ResponseEntity<PartnerCompanyResponse> updatePartnerCompany(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePartnerCompanyRequest request) {
        PartnerCompanyResponse company = partnerCompanyService.updatePartnerCompany(id, request);
        return ResponseEntity.ok(company);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete partner company")
    @RequiresPermission("action.partner_company.delete")
    @Audited(action = "Deleted", resourceType = "PARTNER_COMPANY", resourceId = "#id.toString()")
    public ResponseEntity<Void> deletePartnerCompany(@PathVariable UUID id) {
        partnerCompanyService.deletePartnerCompany(id);
        return ResponseEntity.noContent().build();
    }
}
