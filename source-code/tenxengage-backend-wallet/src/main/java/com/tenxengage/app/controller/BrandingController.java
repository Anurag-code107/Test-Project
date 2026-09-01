package com.tenxengage.app.controller;

import com.tenxengage.app.audit.Audited;
import com.tenxengage.app.dto.request.UpdateBrandingRequest;
import com.tenxengage.app.dto.response.BrandingResponse;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.service.BrandingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/branding")
@Tag(name = "Branding", description = "Tenant-scoped branding configuration")
public class BrandingController {

    private final BrandingService brandingService;

    public BrandingController(BrandingService brandingService) {
        this.brandingService = brandingService;
    }

    @GetMapping
    @Operation(summary = "Get branding configuration for the current tenant")
    public ResponseEntity<BrandingResponse> getBranding() {
        return ResponseEntity.ok(brandingService.getBranding());
    }

    @PutMapping
    @Operation(summary = "Upsert branding configuration for the current tenant")
    @RequiresPermission("action.branding.manage")
    @Audited(action = "Edited", resourceType = "BRANDING", description = "Updated branding configuration")
    public ResponseEntity<BrandingResponse> updateBranding(
            @Valid @RequestBody UpdateBrandingRequest request) {
        return ResponseEntity.ok(brandingService.saveBranding(request));
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a custom logo for the current tenant")
    @RequiresPermission("action.branding.manage")
    @Audited(action = "Uploaded", resourceType = "BRANDING", description = "Uploaded a custom logo")
    public ResponseEntity<BrandingResponse> uploadLogo(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(brandingService.uploadLogo(file));
    }

    @DeleteMapping("/logo")
    @Operation(summary = "Remove the custom logo for the current tenant")
    @RequiresPermission("action.branding.manage")
    @Audited(action = "Deleted", resourceType = "BRANDING", description = "Removed the custom logo")
    public ResponseEntity<BrandingResponse> removeLogo() {
        return ResponseEntity.ok(brandingService.removeLogo());
    }

    @GetMapping("/logo/file")
    @Operation(summary = "Stream the current tenant's custom logo binary")
    public ResponseEntity<InputStreamResource> streamLogo() {
        BrandingService.LogoStream logo = brandingService.streamLogo();
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(logo.contentType()))
            .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePrivate())
            .body(new InputStreamResource(logo.content()));
    }
}
